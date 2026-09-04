#!/usr/bin/env node
/**
 * Viewport audit — resolves the REAL CSS cascade for a given viewport width.
 *
 * Why this exists: three rounds of regex checks on the stylesheet said the
 * mobile fixes were present, and they were — but a regex cannot tell you which
 * rule WINS. A later `@media` block, a more specific selector, or a desktop
 * `min-width` query that also matches will silently override the mobile rule.
 * That is how "the AI chat still doesn't fit" survived checks that passed.
 *
 * This parses the built bundle with postcss, keeps only the @media blocks that
 * actually match the target width, walks them in source order, and reports the
 * winning declaration for the properties that decide whether a screen fits.
 *
 * Usage:  node tests/viewport-audit.mjs [width]        (default 360)
 */
import { readFileSync, readdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import postcss from 'postcss';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..');
const DIST = join(ROOT, 'dist');
const WIDTH = Number(process.argv[2] || 360);
/** Reference phone height. Height-gated media queries are evaluated against
    this, so a (max-height: …) fallback does not masquerade as the winner. */
const HEIGHT_PX = Number(process.argv[3] || 780);

const cssFile = readdirSync(join(DIST, 'assets')).find((f) => f.endsWith('.css'));
const css = readFileSync(join(DIST, 'assets', cssFile), 'utf8');
const root = postcss.parse(css);

/** Does a media query text match a viewport of WIDTH px (screen, no print)? */
function mediaMatches(params) {
  const q = params.toLowerCase();
  if (q.includes('print')) return false;
  if (q.includes('prefers-reduced-motion')) return false;
  if (q.includes('hover') || q.includes('pointer')) return false;
  // Evaluate every comma-separated query; any match wins.
  //
  // HEIGHT matters too. Ignoring (max-height: …) made every height-gated block
  // look like it matched, so a short-screen fallback silently overrode the
  // rule that actually applies on a normal phone — and the audit then reported
  // the fallback's value as the winner. That masked a genuine regression.
  return q.split(',').some((part) => {
    let ok = true;
    for (const m of part.matchAll(/\(\s*(min|max)-(width|height):\s*([\d.]+)px\s*\)/g)) {
      const [, bound, axis, raw] = m;
      const v = parseFloat(raw);
      const actual = axis === 'width' ? WIDTH : HEIGHT_PX;
      if (bound === 'min' && !(actual >= v)) ok = false;
      if (bound === 'max' && !(actual <= v)) ok = false;
    }
    return ok;
  });
}

/**
 * Collect declarations for a selector, in cascade order, keeping only rules
 * whose enclosing @media matches. Returns the winning value per property.
 */
function resolve(selector, props) {
  const winners = {};       // prop -> {value, from}
  root.walkRules((rule) => {
    const parent = rule.parent;
    if (parent && parent.type === 'atrule') {
      if (parent.name !== 'media') return;
      if (!mediaMatches(parent.params)) return;
    }
    // exact selector match within a comma list
    const sels = rule.selector.split(',').map((s) => s.trim());
    if (!sels.includes(selector)) return;
    const where = parent && parent.type === 'atrule' ? `@media ${parent.params}` : '(base)';
    rule.walkDecls((d) => {
      if (!props.includes(d.prop)) return;
      // later source order wins (same specificity for identical selector)
      winners[d.prop] = { value: d.value, from: where, important: d.important };
    });
  });
  return winners;
}

function show(title, selector, props) {
  console.log(`\n\x1b[1m${title}\x1b[0m  \x1b[2m${selector}\x1b[0m`);
  const w = resolve(selector, props);
  if (!Object.keys(w).length) { console.log('  (no matching declarations)'); return w; }
  for (const p of props) {
    if (!w[p]) continue;
    console.log(`  ${p.padEnd(12)} = ${w[p].value}${w[p].important ? ' !important' : ''}   \x1b[2m${w[p].from}\x1b[0m`);
  }
  return w;
}

console.log(`\x1b[1mResolved cascade at ${WIDTH}px viewport\x1b[0m  (bundle: ${cssFile})`);

const aiChat = show('AI chat card', '.ai-chat', ['height', 'min-height', 'max-height']);
const aiBody = show('AI transcript', '.ai-chat-body', ['height', 'min-height', 'max-height', 'flex', 'overflow-y']);
const aiConsole = show('AI console grid', '.ai-console', ['grid-template-columns', 'display', 'gap']);
show('AI composer', '.ai-chat-input', ['padding', 'flex-shrink']);
const pageSub = resolve('.page-sub', ['font-size', 'line-height']);
const aiWelcomeP = resolve('.ai-welcome p', ['font-size', 'line-height']);
const authWrap = show('Auth grid', '.auth-wrap', ['grid-template-columns', 'min-height', 'display']);
const authBrand = show('Auth brand panel', '.auth-brand', ['padding', 'min-height', 'display']);
const authCard = show('Auth card', '.auth-card', ['max-width', 'padding', 'width']);
const authForm = show('Auth form column', '.auth-form', ['padding', 'display']);
show('Auth lockup', '.auth-lockup', ['width']);
show('Topbar', '.topbar', ['height', 'flex-wrap', 'padding', 'gap']);
show('Public topbar', '.public-topbar', ['flex-direction', 'height', 'padding', 'gap']);

// ── Verdicts ───────────────────────────────────────────────────────────────
let fail = 0;
const bad = (m) => { fail++; console.log(`  \x1b[31m✗ ${m}\x1b[0m`); };
const ok = (m) => console.log(`  \x1b[32m✓ ${m}\x1b[0m`);

console.log(`\n\x1b[1mVerdicts at ${WIDTH}px\x1b[0m`);

const PHONE = WIDTH <= 620;
if (!PHONE) {
  console.log('  \x1b[2m(desktop width — the phone-only rules below are intentionally skipped)\x1b[0m');
}

// 1. The AI card must not force a height taller than the viewport.
const h = aiChat.height?.value || '';
const mh = aiChat['min-height']?.value || '';
const px = (v) => { const m = /^([\d.]+)px$/.exec(v || ''); return m ? parseFloat(m[1]) : null; };
if (PHONE) {
  if (px(mh) && px(mh) > 420) bad(`.ai-chat min-height ${mh} still forces a tall card`);
  else ok(`.ai-chat min-height resolves to "${mh || 'none'}"`);
  if (/100vh/.test(h)) bad(`.ai-chat height uses 100vh (${h}) — ignores mobile browser chrome`);
  else ok(`.ai-chat height resolves to "${h || 'auto'}"`);
}

// 2. The transcript must be bounded, or the card grows past the screen.
const bodyMax = aiBody['max-height']?.value || '';
if (PHONE) {
  if (!bodyMax) bad('.ai-chat-body has no max-height — the transcript can push the composer off screen');
  else if (/100vh/.test(bodyMax)) bad(`.ai-chat-body max-height uses 100vh (${bodyMax})`);
  else ok(`.ai-chat-body max-height = ${bodyMax}`);
}

// 3. Auth must be a single column on a phone.
const cols = authWrap['grid-template-columns']?.value || '';
if (WIDTH <= 900) {
  if (/fr\s+.*fr/.test(cols)) bad(`.auth-wrap is still ${cols} — two columns on a phone`);
  else ok(`.auth-wrap grid-template-columns = ${cols || 'none'}`);
}

// 4. The auth card must fit inside the column.
const cardMaxRaw = authCard['max-width']?.value || '';
const cardMax = px(cardMaxRaw);
const formPad = (authForm.padding?.value || '0').split(/\s+/).map(px).filter((n) => n !== null);
const sidePad = formPad.length >= 2 ? formPad[1] * 2 : 0;
const avail = WIDTH - sidePad;
if (!cardMaxRaw) bad('.auth-card has no resolved max-width — cannot prove it fits');
else if (cardMax === null) {
  // A relative value (100%, min(), clamp()) is inherently safe: it cannot
  // exceed its container. Only a fixed px value can overflow.
  ok(`.auth-card max-width "${cardMaxRaw}" is container-relative, so it cannot overflow`);
} else if (cardMax > avail) bad(`.auth-card max-width ${cardMax}px > available ${avail}px`);
else ok(`.auth-card max-width ${cardMax}px fits available ${avail}px`);

// 4a. Legibility: nothing may be shrunk below a comfortable reading size.
// Every previous fix made things FIT by making them smaller, and the result
// read as cramped. Fitting and legibility are two constraints, not one: the
// page scrolls vertically (only overflow-x is hidden), so shrinking body copy
// to win vertical space is never necessary. Guard the floor.
if (WIDTH <= 620) {
  // Resolve the type tokens THROUGH the cascade for this width, so a mobile
  // override of :root is picked up. Hardcoding the desktop scale here would
  // have hidden the very bug this section exists to catch.
  const rootVars = resolve(':root', ['--fs-xs', '--fs-sm', '--fs-base', '--fs-md', '--fs-lg', '--fs-xl', '--fs-2xl', '--fs-3xl']);
  const FS = {};
  for (const [k, v] of Object.entries(rootVars)) {
    const n = /^([\d.]+)px$/.exec((v.value || '').trim());
    if (n) FS[k] = parseFloat(n[1]);
  }
  const toPx = (v) => {
    if (!v) return null;
    const m = /var\((--fs-[a-z0-9]+)\)/.exec(v);
    if (m) return FS[m[1]] ?? null;
    const n = /^([\d.]+)px$/.exec(v.trim());
    return n ? parseFloat(n[1]) : null;
  };
  // Body copy the user actually reads. 13px is the floor; below that a phone
  // screen feels squeezed even though it technically fits.
  // Platform norm. iOS and Android both default to 16px body text; rendering
  // materially below that is what "everything is tiny" means on a handset.
  // This is the check that would have caught the real problem six rounds ago.
  const base = FS['--fs-base'];
  if (base === undefined) {
    bad('could not resolve --fs-base for this width');
  } else if (base < 15) {
    bad(`--fs-base is ${base}px on a phone — below the 15px platform norm, so `
      + 'every screen renders smaller than the OS default and reads as cramped');
  } else {
    ok(`--fs-base is ${base}px on a phone — at the platform norm`);
  }
  const xs = FS['--fs-xs'];
  if (xs !== undefined && xs < 12) {
    bad(`--fs-xs is ${xs}px — captions and badges are below the legibility floor`);
  } else if (xs !== undefined) {
    ok(`--fs-xs is ${xs}px — small text stays legible`);
  }

  const MIN_BODY = 13;
  const bodyCopy = [
    ['.page-sub', pageSub],
    ['.ai-welcome p', aiWelcomeP],
  ];
  for (const [name, rule] of bodyCopy) {
    const fs = toPx(rule?.['font-size']?.value);
    if (fs === null) { ok(`${name} keeps the inherited body size`); continue; }
    if (fs < MIN_BODY) {
      bad(`${name} font-size ${fs}px is below the ${MIN_BODY}px legibility floor — `
        + 'text will look squeezed');
    } else ok(`${name} font-size ${fs}px is comfortably readable`);
  }
}

// 4c. The AI card must END on screen, not merely be "tall".
// A previous fix set the card to 86dvh, which sounded generous but was
// actively harmful: the card starts ~218px down the page, so 86dvh ended
// 165px BELOW the fold and took the message composer with it. Growing the
// number made that worse. What matters is where the card ENDS.
if (WIDTH <= 620) {
  const H = HEIGHT_PX;
  const BOTTOM_NAV = 76;
  const CARD_TOP = 215;   // topbar 100 + category bar 44 + padding 16 + agent chip row 43 + gap 12
  const usableBottom = H - BOTTOM_NAV;

  const fullRule = resolve('.ai-console-full .ai-chat', ['height', 'min-height', 'max-height']);
  const hv = (fullRule['height']?.value || '').trim();

  let cardPx = null;
  const calcM = /^calc\(100dvh\s*-\s*([\d.]+)px\)$/.exec(hv);
  const dvhM = /^([\d.]+)dvh$/.exec(hv);
  const maxM = /^max\(/.test(hv);
  if (calcM) cardPx = H - parseFloat(calcM[1]);
  else if (dvhM) cardPx = (parseFloat(dvhM[1]) / 100) * H;

  if (maxM) {
    bad(`.ai-console-full .ai-chat height "${hv}" uses max(), which can exceed the `
      + 'space available and push the composer off screen — size it to the visible area');
  } else if (cardPx === null) {
    bad(`.ai-console-full .ai-chat height "${hv || 'unset'}" is not viewport-relative`);
  } else {
    const endsAt = CARD_TOP + cardPx;
    if (endsAt > usableBottom + 1) {
      bad(`.ai-console-full .ai-chat ends at ${Math.round(endsAt)}px but the screen is `
        + `usable only to ${usableBottom}px -> the composer is ${Math.round(endsAt - usableBottom)}px off screen`);
    } else {
      ok(`.ai-console-full .ai-chat ends at ${Math.round(endsAt)}px, inside the ${usableBottom}px `
        + 'usable area — the whole card including the composer is on screen');
    }
  }
}

// 4b. Arithmetic: do the AUTH screens fit vertically?
// The audit originally only asked whether the auth CARD was too WIDE. It was
// not — the real fault was vertical. Login/Register render inside the public
// shell, so ~160px of chrome sits above them and a 76px fixed bottom nav sits
// below, leaving ~544px on a 360x780 phone. `.auth-wrap { min-height: 100dvh }`
// demanded the full 780px inside that 544px, and the decorative brand panel
// stacked above the form pushed the email field below the fold.
if (WIDTH <= 620) {
  const HEIGHT = 780;
  // Login/register now render in their own minimal shell: a 45px header with
  // just the brand and the theme toggle. No search bar, no category nav, no
  // footer, no bottom nav — that furniture cost 474px and pushed the form
  // 302px down the page.
  const AUTH_CHROME = 45;
  const BOTTOM_NAV = 0;        // the auth shell has no bottom nav
  const availAuth = HEIGHT - AUTH_CHROME - BOTTOM_NAV;

  const wrapMin = authWrap['min-height']?.value || '';
  if (/100dvh|100vh/.test(wrapMin)) {
    bad(`.auth-wrap min-height "${wrapMin}" demands the full viewport, but it sits `
      + `inside the public shell with only ${availAuth}px available `
      + `-> the page is ${HEIGHT - availAuth}px too tall before any field is drawn`);
  } else {
    ok(`.auth-wrap min-height "${wrapMin || '0'}" lets the page size to its content`);
  }

  // The brand panel is decoration; if it is tall it pushes the form off screen.
  const brandPad = authBrand['padding']?.value || '';
  const padTop = parseFloat((/^([\d.]+)px/.exec(brandPad) || [])[1] || '0');
  if (padTop > 30) {
    bad(`.auth-brand padding "${brandPad}" keeps the decorative panel tall on a phone, `
      + 'pushing the form below the fold');
  } else {
    ok(`.auth-brand padding "${brandPad || 'default'}" is compact enough for a phone`);
  }
}

// 5. Arithmetic: does the whole AI card fit the space left under the chrome?
if (WIDTH <= 620) {
  const CHROME = 414;                 // measured: topbar+mainnav+padding+header+rail+gap+bottomnav
  const HEIGHT = 780;                 // reference phone
  const availCard = HEIGHT - CHROME;
  const head = 48, composer = 63;
  const AI_HEAD_PX = head, AI_COMPOSER_PX = composer;
  const bodyMin = px(aiBody['min-height']?.value) ?? 0;
  const cardMin = head + bodyMin + composer;
  if (cardMin > availCard) bad(`.ai-chat minimum height ${cardMin}px > ${availCard}px available`);
  else ok(`.ai-chat minimum height ${cardMin}px fits ${availCard}px available`);

  // THE CEILING MUST BE ON THE CARD, NOT THE TRANSCRIPT.
  // .ai-chat-head and .ai-chat-input are SIBLINGS of .ai-chat-body inside
  // .ai-chat. A max-height on the body therefore bounds only the transcript;
  // the head and composer are added on top of it. Bounding the body at the
  // full card budget (100dvh - 414px) yields a card of head + budget +
  // composer, which overflows by exactly head+composer. This verdict
  // previously asserted the subtrahend on the BODY and so certified that
  // bug as correct. Check the card, and require the body to be shrinkable.
  const TOPBAR_H = 62;
  const resolveCalc = (v) => {
    const r = (v || '').replace(/var\(--topbar-h\)/g, `${TOPBAR_H}px`);
    if (!/100dvh|100vh/.test(r)) return null;
    const subs = [...r.matchAll(/-\s*([\d.]+)px/g)].map((x) => parseFloat(x[1]));
    return subs.length ? subs.reduce((a, b) => a + b, 0) : null;
  };

  // The card must be bounded by the viewport in SOME viewport-relative way, so
  // the composer can never be pushed out of reach. Two valid shapes:
  //   calc(100dvh - Npx)  — strict "fit above the fold" budget
  //   N dvh/vh            — a share of the viewport (the page scrolls, so this
  //                         is fine and avoids cramming the card into 366px)
  const cardMax = aiChat['max-height']?.value || '';
  const cardSub = resolveCalc(cardMax);
  const vhShare = /^([\d.]+)(dvh|vh)$/.exec(cardMax.trim());
  if (cardSub !== null) {
    // The card must be BOUNDED (so the composer is always reachable) and must
    // leave a usable transcript. It does NOT have to fit above the fold: the
    // document scrolls vertically, and demanding that is exactly what forced
    // the cramped 366px card in the first place.
    const cardPx = HEIGHT - cardSub;
    const minUsable = AI_HEAD_PX + 120 + AI_COMPOSER_PX;   // head + a few lines + composer
    if (cardPx < minUsable) {
      bad(`.ai-chat resolves to ${cardPx}px on a ${HEIGHT}px screen — below the `
        + `${minUsable}px needed for the header, a readable transcript and the composer`);
    } else {
      ok(`.ai-chat max-height = ${cardPx}px on a ${HEIGHT}px screen — bounded and usable`);
    }
  } else if (vhShare) {
    const share = parseFloat(vhShare[1]);
    const cardPx = Math.round((share / 100) * HEIGHT);
    // A share this large would hide the composer even after scrolling.
    if (share > 85) {
      bad(`.ai-chat max-height ${cardMax} (${cardPx}px) leaves no room for the page chrome`);
    } else {
      ok(`.ai-chat max-height ${cardMax} = ${cardPx}px on a ${HEIGHT}px phone — `
        + 'bounded, and the page scrolls to reach it');
    }
  } else {
    bad(`.ai-chat has no viewport-relative max-height (got "${cardMax || 'none'}") `
      + '— nothing bounds the card to the screen');
  }

  // The transcript must be able to shrink, or it re-inflates the card.
  const bodyMinH = aiBody['min-height']?.value || '0';
  const bodyMaxH = aiBody['max-height']?.value || '';
  const bodyMinPx = px(bodyMinH) || 0;
  const bodySub = resolveCalc(bodyMaxH);
  if (bodySub !== null && bodySub >= CHROME) {
    bad(`.ai-chat-body max-height "${bodyMaxH}" applies the whole-card budget to the `
      + 'transcript alone — head + composer are siblings, so the card overflows by their height');
  } else if (bodyMinPx > 0) {
    const cardFloor = head + bodyMinPx + composer;
    if (cardFloor > availCard) {
      bad(`.ai-chat-body min-height ${bodyMinPx}px forces a ${cardFloor}px card `
        + `but only ${availCard}px is available -> overflow ${cardFloor - availCard}px`);
    } else {
      ok(`.ai-chat-body min-height ${bodyMinPx}px keeps the card at ${cardFloor}px <= ${availCard}px`);
    }
  } else {
    ok('.ai-chat-body is free to shrink (min-height 0), so the card ceiling governs');
  }
  // flex:1 lets the transcript absorb leftover space instead of forcing height
  const bodyFlex = aiBody.flex?.value || '';
  if (!/^1(\s|$)/.test(bodyFlex)) {
    bad(`.ai-chat-body is not flexible (flex: "${bodyFlex || 'none'}") — it cannot shrink to fit`);
  } else ok(`.ai-chat-body flexes to the space available (flex: ${bodyFlex})`);
}

console.log(fail ? `\n\x1b[31m\x1b[1m${fail} problem(s) at ${WIDTH}px\x1b[0m\n`
                 : `\n\x1b[32m\x1b[1mNo layout blockers at ${WIDTH}px\x1b[0m\n`);
process.exit(fail ? 1 : 0);
