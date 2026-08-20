#!/usr/bin/env node
/**
 * ScottsTechX Android — static layout / overflow check.
 *
 * There is no emulator here, so nothing can actually lay a Composable out.
 * What this DOES do is catch the two classes of bug that produced the visible
 * damage on a real phone, by reading the source:
 *
 *   1. EDGE-TO-EDGE. The app targets SDK 35. On Android 15+ that forces the
 *      window edge to edge and the opt-out is deprecated for target 36, so any
 *      screen that pins content to the top or bottom edge without consulting
 *      WindowInsets draws underneath the status bar or the gesture pill. That
 *      is the "doesn't fit on the screen" report.
 *
 *   2. FIXED-WIDTH ROWS. A Row of N fixed-width children plus spacing that
 *      exceeds the narrowest supported screen will clip its last child. This
 *      measures declared widths arithmetically against real device widths.
 *
 * It also measures worst-case text (a 13-character UGX revenue figure) against
 * the space a stat tile actually gets, using a width table for Roboto Medium,
 * because that specific overflow is what made the seller dashboard look broken.
 *
 * Usage:  node tools/layout-check.mjs      (from scottsx-android/)
 */
import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs';
import { join, dirname, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const SRC = join(ROOT, 'app/src/main/java/com/scottsx/app/ui');

let pass = 0, fail = 0;
const failures = [];
const ok = (n) => { pass++; console.log(`  \x1b[32m✓\x1b[0m ${n}`); };
const bad = (n, d = '') => { fail++; failures.push(n); console.log(`  \x1b[31m✗\x1b[0m ${n}${d ? ` — ${d}` : ''}`); };

/** Narrowest phone we support, in dp. Galaxy A03/A04-class hardware is 360dp. */
const NARROW_DP = 360;

function walk(dir) {
  const out = [];
  for (const e of readdirSync(dir)) {
    const p = join(dir, e);
    if (statSync(p).isDirectory()) out.push(...walk(p));
    else if (e.endsWith('.kt')) out.push(p);
  }
  return out;
}
const files = walk(SRC);
const read = (f) => readFileSync(f, 'utf8');
const rel = (f) => relative(ROOT, f);

console.log(`\n\x1b[1mScottsTechX Android layout check\x1b[0m  (${files.length} UI files, narrow screen ${NARROW_DP}dp)\n`);

// ── 1. Edge-to-edge is declared ─────────────────────────────────────────────
console.log('\x1b[1m1. Edge-to-edge / window insets\x1b[0m');
{
  const main = read(join(ROOT, 'app/src/main/java/com/scottsx/app/MainActivity.kt'));
  ok_if('MainActivity calls enableEdgeToEdge()', /enableEdgeToEdge\(\)/.test(main));
  ok_if('enableEdgeToEdge() runs before super.onCreate',
    main.indexOf('enableEdgeToEdge()') < main.indexOf('super.onCreate'));

  const gradle = read(join(ROOT, 'app/build.gradle.kts'));
  const target = /targetSdk\s*=\s*(\d+)/.exec(gradle);
  ok_if('targetSdk is known', !!target);
  if (target && Number(target[1]) >= 35) {
    ok_if('a shared inset helper exists for the enforced edge-to-edge target',
      files.some((f) => /fun\s+topInset|fun\s+bottomInset/.test(read(f))));
  }
}

// ── 2. Screens that own the top or bottom edge must consult insets ──────────
console.log('\n\x1b[1m2. Screens that pin content to a screen edge\x1b[0m');
{
  // A screen is "full bleed" if it fills the whole window itself rather than
  // sitting inside something that already padded it.
  const fullBleed = files.filter((f) => {
    const s = read(f);
    return /Modifier\s*\n?\s*\.fillMaxSize\(\)/.test(s) && /@Composable/.test(s);
  });
  ok_if('found full-screen composables to audit', fullBleed.length > 0, `${fullBleed.length}`);

  // Strip imports first: `import ...statusBarSpacer` must not count as using it.
  // Without this a screen that imports a helper and never calls it passes.
  const body = (s) => s.split('\n').filter((l) => !/^\s*import\s/.test(l)).join('\n');
  const insetAware = (src) =>
    /topInset\(\)|bottomInset\(\)|statusBarSpacer\(\)|navBarSpacer\(\)|windowInsetsPadding\(|systemBarsPadding\(|statusBarsPadding\(|navigationBarsPadding\(|ScreenScaffold\(/.test(body(src));

  // The three screens the user reported, plus every screen that draws its own
  // bottom navigation bar (those are the ones that can hide a tappable row).
  const mustBeAware = [
    'screens/BuyerHomeScreen.kt',
    'screens/SellerHomeScreen.kt',
    'screens/WelcomeScreen.kt',
  ];
  for (const name of mustBeAware) {
    const f = files.find((x) => rel(x).endsWith(name));
    if (!f) { bad(`${name} exists`); continue; }
    ok_if(`${name.split('/').pop()} handles window insets`, insetAware(read(f)));
  }

  // Any screen positioning a bar at BottomCenter must lift it off the nav bar.
  for (const f of files) {
    const s = read(f);
    if (!/align\(Alignment\.BottomCenter\)/.test(s)) continue;
    ok_if(`${rel(f).split('/').pop()} lifts its bottom-anchored bar off the navigation bar`,
      insetAware(s));
  }

  // EVERY screen must end up inset-aware, either directly or through one of the
  // shared headers. Screens are added often; this makes forgetting one a test
  // failure rather than something a user has to notice on a phone.
  const screens = files.filter((f) => rel(f).includes('/screens/'));
  const viaShared = (src) => /ScreenHeader\(|GradientHeader\(|ConversationListScreen\(/.test(body(src));
  const bare = screens.filter((f) => {
    const s = read(f);
    return !insetAware(s) && !viaShared(s);
  });
  ok_if(`all ${screens.length} screens handle insets directly or via a shared header`,
    bare.length === 0, bare.map((f) => rel(f).split('/').pop()).join(', '));

  // The shared headers themselves must be the thing that does it.
  const uiKit = read(files.find((f) => rel(f).endsWith('components/UiKit.kt')));
  ok_if('GradientHeader pads for the status bar', /statusBarSpacer\(\)/.test(uiKit));
  const addr = read(files.find((f) => rel(f).endsWith('screens/AddressesScreen.kt')));
  // Scope to the function body: from its declaration to the next top-level
  // declaration, so a match elsewhere in the file cannot satisfy this.
  const hdrStart = addr.indexOf('internal fun ScreenHeader');
  const after = addr.slice(hdrStart + 1);
  const hdrEnd = after.search(/\n(internal |private |@Composable|fun )/);
  const hdrBody = hdrEnd === -1 ? after : after.slice(0, hdrEnd);
  ok_if('the shared ScreenHeader pads for the status bar',
    hdrStart !== -1 && /statusBarSpacer\(\)/.test(hdrBody));

  // A chat composer must clear BOTH the keyboard and the navigation bar.
  const thread = read(files.find((f) => rel(f).endsWith('screens/MessageThreadScreen.kt')));
  ok_if('the message composer clears the keyboard and the navigation bar',
    /imePadding\(\)/.test(thread) && /navBarSpacer\(\)/.test(thread));
}

// ── 3. Fixed-width rows must fit the narrowest phone ────────────────────────
console.log('\n\x1b[1m3. Fixed widths vs a 360dp screen\x1b[0m');
{
  // Any single declared .width(N.dp) larger than the screen is an instant clip.
  let widest = 0, widestWhere = '';
  for (const f of files) {
    for (const m of read(f).matchAll(/\.width\((\d+)\.dp\)/g)) {
      const w = Number(m[1]);
      if (w > widest) { widest = w; widestWhere = rel(f); }
    }
  }
  ok_if(`no single fixed width exceeds ${NARROW_DP}dp`, widest <= NARROW_DP,
    `widest is ${widest}dp in ${widestWhere}`);

  // A horizontally scrolling LazyRow may legitimately hold wide children, but a
  // non-scrolling Row of fixed widths may not.
  const sellerBar = files.find((f) => rel(f).endsWith('components/SellerBottomBar.kt'));
  if (sellerBar) {
    const s = read(sellerBar);
    const spacer = Number((/Spacer\(Modifier\.width\((\d+)\.dp\)\)/.exec(s) || [])[1] || 0);
    const fab = Number((/\.size\((\d+)\.dp\)\s*\n\s*\.scale/.exec(s) || [])[1] || 0);
    ok_if('seller bottom bar reserves at least the FAB width for the centre gap',
      spacer >= fab - 8, `spacer ${spacer}dp vs FAB ${fab}dp`);
  }
}

// ── 4. Worst-case dashboard figures fit their tile ──────────────────────────
console.log('\n\x1b[1m4. Seller dashboard stat tiles\x1b[0m');
{
  const f = files.find((x) => rel(x).endsWith('screens/SellerHomeScreen.kt'));
  const s = read(f);

  // Roboto Medium advance widths in dp per character at 1sp, close enough for
  // digits and capitals which are tabular-ish. Measured from the metrics table.
  const CHAR_DP = { digit: 0.5566, upper: 0.66, space: 0.26, comma: 0.28, dot: 0.28 };
  const textWidth = (str, sp) => {
    let w = 0;
    for (const c of str) {
      if (/[0-9]/.test(c)) w += CHAR_DP.digit;
      else if (/[A-Z]/.test(c)) w += CHAR_DP.upper;
      else if (c === ' ') w += CHAR_DP.space;
      else w += CHAR_DP.comma;
    }
    return w * sp;
  };

  const usesCompact = /formatUgxCompact\(/.test(s);
  ok_if('revenue is abbreviated rather than printed in full', usesCompact);

  const perRow = /Row\(/.test(s) && /weight\(1f\)/.test(s);
  ok_if('stat tiles are weighted, not free-flowing', perRow);

  // 2 tiles per row, 20dp screen padding each side, 10dp gap, 12dp inner padding.
  const tileOuter = (NARROW_DP - 20 * 2 - 10) / 2;
  const tileInner = tileOuter - 12 * 2;
  // Worst realistic revenue: "UGX 999.9B" at 17sp.
  const worst = 'UGX 999.9B';
  const w = textWidth(worst, 17);
  ok_if(`worst-case revenue "${worst}" fits a tile (${w.toFixed(1)}dp <= ${tileInner.toFixed(1)}dp)`,
    w <= tileInner, `needs ${w.toFixed(1)}dp, has ${tileInner.toFixed(1)}dp`);

  // And prove the OLD layout would have failed, so this check has teeth.
  const oldWorst = 'UGX 45000000';
  const oldTile = (NARROW_DP - 20 * 2) / 4;
  const ow = textWidth(oldWorst, 18);
  ok_if('the previous 4-across raw-number layout would indeed have overflowed',
    ow > oldTile, `it fit in ${ow.toFixed(1)}dp <= ${oldTile.toFixed(1)}dp, so this check proves nothing`);

  ok_if('stat values are single-line with ellipsis', /softWrap = false/.test(s) && /TextOverflow\.Ellipsis/.test(s));

  // Organisation: the same fact must not be repeated as a tile, a banner AND a
  // list. Low stock is the one that was tripled.
  const lowStockMentions = (s.match(/lowStock|low on stock|Needs restocking|Inventory alerts/g) || [])
    .filter((m) => m !== 'lowStockItems').length;
  ok_if('low stock is not restated three times over', !/low on stock — restock soon/.test(s));
  ok_if('the duplicate "Inventory alerts" strip is gone', !/SectionHeader\("Inventory alerts"\)/.test(s));
  ok_if('the restock list is guarded so its heading never sits above an empty strip',
    /if \(lowStockItems\.isNotEmpty\(\)\)[\s\S]{0,200}?SectionHeader\("Needs restocking"\)/.test(s));
  ok_if('the time-sensitive restock list comes before the full inventory grid',
    s.indexOf('SectionHeader("Needs restocking")') < s.indexOf('SectionHeader("Your inventory"'));
  // Scope to the restock block itself rather than guessing a character window.
  const rsStart = s.indexOf('SectionHeader("Needs restocking")');
  const rsEnd = s.indexOf('SectionHeader("Your inventory"', rsStart);
  const restockBlock = rsStart === -1 ? '' : s.slice(rsStart, rsEnd);
  ok_if('restock chips clamp long product titles',
    /maxLines = 1/.test(restockBlock) && /TextOverflow\.Ellipsis/.test(restockBlock));
}

// ── 5. Brand lockup is not distorted ────────────────────────────────────────
{
  // The seller bottom bar is on every seller screen; if it ignores the nav bar
  // its labels sit under the gesture pill.
  const sb = read('app/src/main/java/com/scottsx/app/ui/components/SellerBottomBar.kt');
  ok_if('the seller bottom bar lifts its tabs above the navigation bar',
    /\.navBarSpacer\(\)/.test(sb));
  ok_if('the seller bottom bar surface still paints to the bottom edge',
    sb.indexOf('.navBarSpacer()') > sb.indexOf('Surface('));

  // Modifier order: .padding(n).size(m) sizes the CONTENT and inflates the
  // drawn box. On a circular icon button that means an oversized disc.
  const uk = read('app/src/main/java/com/scottsx/app/ui/components/UiKit.kt');
  ok_if('no icon button pads before sizing (inflates the circle)',
    !/\.padding\(\d+\.dp\)\s*\n\s*\.size\(\d+\.dp\)/.test(uk));
  // App-wide: .padding(p).size(n) sizes the CONTENT and inflates the drawn box
  // to n+2p, so a clipped circle comes out oversized. The exception is when the
  // padding sits BEFORE the clip/background — there it is an edge inset that
  // positions the disc, which is legitimate.
  {
    const offenders = [];
    for (const f of files) {
      const src = read(f);
      for (const m of src.matchAll(/\.padding\((\d+)\.dp\)\s*\n\s*\.size\((\d+)\.dp\)/g)) {
        const after = src.slice(m.index + m[0].length, m.index + m[0].length + 160);
        const insetBeforeShape = /\.clip\(|\.background\(/.test(after);
        if (!insetBeforeShape) {
          const line = src.slice(0, m.index).split('\n').length;
          offenders.push(`${rel(f).split('/').pop()}:${line}`);
        }
      }
    }
    ok_if('no icon button inflates its circle by padding before sizing',
      offenders.length === 0, offenders.join(', '));
  }

  ok_if('the gradient header back button has a fixed touch target',
    /\.size\(40\.dp\)\s*\n\s*\.clip\(CircleShape\)/.test(uk));
}

{
  // chunked(2) + weight(1f) is a trap: an odd item count leaves the last row
  // with one child, which then takes the ENTIRE row width and renders at
  // double size. Every such grid needs a weighted filler.
  // Scan EVERY file rather than a hand-list, so a new grid cannot slip in
  // without the filler. Each chunked(2) row must have a weighted Spacer keyed
  // to the same loop variable.
  const unfilled = [];
  for (const f of files) {
    const src = read(f);
    if (!src.includes('.chunked(2)')) continue;
    for (const m of src.matchAll(/(\w+)\.chunked\(2\)/g)) {
      const v = m[1];
      // the loop variable is the lambda parameter, e.g. `.forEach { rowItems ->`
      const after = src.slice(m.index, m.index + 200);
      const lv = (/forEach\s*\{\s*(\w+)\s*->/.exec(after) || [])[1];
      if (!lv) continue;
      const re = new RegExp(`if \\(${lv}\\.size == 1\\)\\s*Spacer\\(Modifier\\.weight\\(1f\\)\\)`);
      if (!re.test(src)) unfilled.push(`${rel(f).split('/').pop()} (${v})`);
    }
  }
  ok_if('every chunked(2) grid pads a lone last item so it cannot double in width',
    unfilled.length === 0, unfilled.join(', '));
}

console.log('\n\x1b[1m4b. Shipping hygiene\x1b[0m');
{
  // The app is being published. Credentials printed on the sign-in screen are
  // a handed-out admin login, so this guards every screen, not just the one
  // that had them.
  const leaks = [];
  for (const f of files) {
    const src = read(f);
    for (const pat of [/demo:\s*\S+@/i, /Demo admin/i, /Admin123!/, /Seller123!/, /secret123/]) {
      if (pat.test(src)) leaks.push(`${rel(f).split('/').pop()} (${pat.source})`);
    }
  }
  ok_if('no demo or seed credentials are printed in any screen',
    leaks.length === 0, leaks.join(', '));
}

console.log('\n\x1b[1m5. Brand artwork\x1b[0m');
{
  // The welcome screen deliberately uses the ORIGINAL brand block - a
  // translucent circle holding the shopping emoji with the ScottsTechX
  // wordmark under it. Four checks here previously asserted the opposite
  // (that the welcome screen must render brand_lockup instead); that was my
  // redesign and the user reversed it, so asserting it would lock in a
  // decision that has been overturned. The lockup checks now follow the
  // lockup to the splash screen, which is where it actually lives.
  const w = files.find((x) => rel(x).endsWith('screens/WelcomeScreen.kt'));
  const s = read(w);
  ok_if('welcome screen keeps the original emoji brand circle',
    /CircleShape/.test(s) && /fontSize = 44\.sp/.test(s));
  ok_if('welcome screen keeps the original 34sp wordmark',
    /"ScottsTechX",[\s\S]{0,120}?fontSize = 34\.sp/.test(s));

  const sp = files.find((x) => rel(x).endsWith('screens/SplashScreen.kt'));
  if (sp) {
    const ss = read(sp);
    ok_if('splash uses the transparent lockup, not the raw square logo',
      /R\.drawable\.brand_lockup/.test(ss) && !/R\.drawable\.logo\b/.test(ss));
    ok_if('splash does not force the lockup into a fixed square',
      !/painterResource\(R\.drawable\.brand_lockup\)[\s\S]{0,300}?\.size\(\d+\.dp\)/.test(ss));
    ok_if('splash does not print the wordmark twice',
      !/"ScottsTechX",\s*\n\s*color = Color\.White,\s*\n\s*fontSize = 3\d\.sp/.test(ss));
    ok_if('splash hands off to the next destination',
      /onFinished\(\)/.test(ss));
  }

  const lockupPath = join(ROOT, 'app/src/main/res/drawable-nodpi/brand_lockup.png');
  if (!existsSync(lockupPath)) {
    console.log('  \x1b[33m-\x1b[0m brand_lockup.png not present — skipping artwork checks');
  } else {
    const png = readFileSync(lockupPath);
    ok_if('brand_lockup.png exists and is a PNG', png.slice(1, 4).toString() === 'PNG');
    // colour type 6 = RGBA. An opaque logo shows a black box on any backdrop.
    ok_if('the lockup has an alpha channel so it sits on the backdrop cleanly',
      png[25] === 6, `colour type ${png[25]}`);
  }
}

console.log('\n\x1b[1m5b. Seller dashboard hero\x1b[0m');
{
  const f = files.find((x) => rel(x).endsWith('screens/SellerHomeScreen.kt'));
  const s = read(f);
  // The hero stats keep the ORIGINAL styling: plain figures straight on the
  // gradient at 18sp, label at 0.8 alpha. They were briefly boxed in
  // translucent panels at 17sp, which changed the look of the whole card.
  ok_if('stat figures use the original 18sp', /fontSize = 18\.sp/.test(s));
  ok_if('stat labels keep the original 0.8 alpha',
    /Color\.White\.copy\(alpha = 0\.8f\)/.test(s));
  ok_if('stat tiles are not boxed in a translucent panel',
    !/Color\.White\.copy\(alpha = 0\.14f\)/.test(s));
  // A tile is ~72.5dp wide on a 360dp phone; "UGX 2.4M" needs ~81dp at 18sp.
  ok_if('the currency is on the label so the figure cannot ellipsise',
    !/value = "UGX /.test(s) && /label = "Revenue UGX"/.test(s));
}

// ── 6. Product tiles ────────────────────────────────────────────────────────
console.log('\n\x1b[1m6. Product card\x1b[0m');
{
  const f = files.find((x) => rel(x).endsWith('components/ProductCard.kt'));
  const s = read(f);
  ok_if('the wishlist heart can be turned off per call site', /showWishlist/.test(s));
  ok_if('the heart is no longer an opaque white disc',
    !/Color\.White\.copy\(alpha = 0\.92f\)/.test(s));
  ok_if('wishlist state is owned by the caller, not a throwaway local',
    !/var wished by remember/.test(s));

  const seller = read(files.find((x) => rel(x).endsWith('screens/SellerHomeScreen.kt')));
  ok_if('the seller grid hides the wishlist heart on its own products',
    /showWishlist = false/.test(seller));
  ok_if('the seller grid shows moderation status on the tile instead',
    /statusLabel = product\.status/.test(seller));

  const buyer = read(files.find((x) => rel(x).endsWith('screens/BuyerHomeScreen.kt')));
  ok_if('the buyer grid persists wishlist taps to the backend',
    /toggleBookmark/.test(buyer));
}

function ok_if(name, cond, detail = '') { cond ? ok(name) : bad(name, detail); }

console.log(`\n\x1b[1mResult: ${pass} passed, ${fail} failed\x1b[0m`);
if (fail) { console.log('\nFailures:'); failures.forEach((f) => console.log(`  - ${f}`)); }
process.exit(fail ? 1 : 0);
