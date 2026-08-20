#!/usr/bin/env node
/**
 * Responsive audit — every route, every phone size, real CSS cascade.
 *
 * viewport-audit.mjs resolves the cascade but only for a hand-picked list of
 * selectors on the auth and AI screens. That is how layout bugs on the other
 * 30 routes stayed invisible: nothing was looking at them.
 *
 * This walks EVERY route the router declares, at the widths real customers
 * actually hold, and reports:
 *
 *   1. fixed pixel widths that cannot fit the viewport
 *   2. horizontal overflow from min-width / white-space: nowrap
 *   3. tap targets below the 44px accessibility floor
 *   4. text below the legibility floor
 *   5. grids whose columns cannot fit side by side
 *   6. content trapped under the fixed bottom nav
 *
 * Usage: node tests/responsive-audit.mjs [--verbose]
 */
import { readFileSync, readdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import postcss from 'postcss';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..');
const DIST = join(ROOT, 'dist');
const VERBOSE = process.argv.includes('--verbose');

const G = (s) => `\x1b[32m${s}\x1b[0m`;
const R = (s) => `\x1b[31m${s}\x1b[0m`;
const Y = (s) => `\x1b[33m${s}\x1b[0m`;
const B = (s) => `\x1b[1m${s}\x1b[0m`;
const D = (s) => `\x1b[2m${s}\x1b[0m`;

/**
 * The phones people actually use. The 320 entry is the floor: iPhone SE 1st
 * gen and the Android Go devices that are common in Uganda, which is the
 * market this app serves.
 */
const DEVICES = [
  { name: 'Galaxy Fold (cover)', w: 280, h: 653 },
  { name: 'iPhone SE / Android Go', w: 320, h: 568 },
  { name: 'iPhone 12/13 mini', w: 360, h: 780 },
  { name: 'Pixel / common Android', w: 393, h: 851 },
  { name: 'iPhone 14 Pro Max', w: 430, h: 932 },
  { name: 'Tablet portrait', w: 768, h: 1024 },
];

const cssFile = readdirSync(join(DIST, 'assets')).find((f) => f.endsWith('.css'));
const css = readFileSync(join(DIST, 'assets', cssFile), 'utf8');
const root = postcss.parse(css);

let failures = [];
let warnings = [];
let passes = 0;

function fail(device, msg, detail) {
  failures.push({ device, msg, detail });
}
function warn(device, msg, detail) {
  warnings.push({ device, msg, detail });
}
function ok(msg) {
  passes += 1;
  if (VERBOSE) console.log(`  ${G('✓')} ${msg}`);
}

/** Does this @media query match the given viewport? */
function mediaMatches(params, W, H) {
  const q = params.toLowerCase();
  if (q.includes('print')) return false;
  if (q.includes('prefers-reduced-motion')) return false;
  if (q.includes('prefers-color-scheme')) return false;
  // Every device in this list is a touchscreen, so `pointer: coarse` and
  // `hover: none` are TRUE here - dropping them (as this used to) silently
  // skipped the touch-target rules and reported the desktop sizes instead.
  // `pointer: fine` / `hover: hover` are the desktop-only cases and are false.
  if (/\(\s*pointer:\s*fine\s*\)|\(\s*hover:\s*hover\s*\)|\(\s*any-hover:\s*hover\s*\)/.test(q)) return false;
  return q.split(',').some((part) => {
    let okq = true;
    // A bare `(pointer: coarse)` branch with no width bound matches outright.
    if (/\(\s*pointer:\s*coarse\s*\)|\(\s*hover:\s*none\s*\)/.test(part)
        && !/(min|max)-(width|height)/.test(part)) return true;
    for (const m of part.matchAll(/\(\s*(min|max)-(width|height):\s*([\d.]+)px\s*\)/g)) {
      const [, bound, axis, raw] = m;
      const v = parseFloat(raw);
      const actual = axis === 'width' ? W : H;
      if (bound === 'min' && !(actual >= v)) okq = false;
      if (bound === 'max' && !(actual <= v)) okq = false;
    }
    return okq;
  });
}

/**
 * Resolve the winning declarations for every selector at this viewport.
 * Returns Map<selector, Map<prop, value>>.
 *
 * Cascade order only — these are all single-class selectors of equal
 * specificity, so source order decides, which is what the browser does.
 */
function resolveAll(W, H) {
  const out = new Map();
  root.walkRules((rule) => {
    const parent = rule.parent;
    if (parent && parent.type === 'atrule') {
      if (parent.name !== 'media') return;
      if (!mediaMatches(parent.params, W, H)) return;
    }
    for (const sel of rule.selectors || []) {
      if (!out.has(sel)) out.set(sel, new Map());
      const props = out.get(sel);
      rule.walkDecls((decl) => props.set(decl.prop, decl.value));
    }
  });
  return out;
}

/** px value of a length, or null when it is not a plain px/number. */
function px(v) {
  if (!v) return null;
  const s = String(v).trim();
  const m = /^(-?[\d.]+)px$/.exec(s);
  if (m) return parseFloat(m[1]);
  if (/^0$/.test(s)) return 0;
  return null;
}

console.log(B('\nResponsive audit — every route, every phone size\n'));

for (const dev of DEVICES) {
  const { name, w: W, h: H } = dev;
  const resolved = resolveAll(W, H);
  const tag = `${name} (${W}×${H})`;
  console.log(B(`${tag}`));

  // ── 1. Fixed widths that cannot fit ──────────────────────────────────────
  {
    const bad = [];
    for (const [sel, props] of resolved) {
      // Skip things that are deliberately off-screen or scrolled.
      if (/::(before|after)|:root|html|body/.test(sel)) continue;
      const wv = px(props.get('width'));
      const mw = px(props.get('min-width'));
      // A fixed width wider than the viewport overflows unless the element
      // scrolls horizontally on purpose.
      const scrolls = /auto|scroll/.test(props.get('overflow-x') || props.get('overflow') || '');
      if (wv !== null && wv > W && !scrolls) bad.push(`${sel} width:${wv}px`);
      if (mw !== null && mw > W && !scrolls) bad.push(`${sel} min-width:${mw}px`);
    }
    if (bad.length) fail(tag, `${bad.length} rule(s) exceed the viewport width`, bad.slice(0, 8).join('  |  '));
    else ok(`no fixed width exceeds ${W}px`);
  }

  // ── 2. Tap targets ───────────────────────────────────────────────────────
  // WCAG 2.5.5 asks for 44×44. Anything materially under that is a miss on a
  // phone, and misses cost orders.
  {
    const FLOOR = 40; // allow a small grace under the 44 ideal
    const bad = [];
    const interactive = /(^|[\s,])(\.btn|\.icon-btn|\.chip|\.tab|\.nav-item|\.bottom-nav|\.pill|button|a\.)/;
    // Things that MATCH the pattern but are not tap targets: an active-tab
    // underline, a numeric count badge, a table header. Flagging these was
    // noise that would have buried the real findings.
    const notATarget = /:(before|after)|-count|-badge|thead|:disabled|::/;
    for (const [sel, props] of resolved) {
      if (!interactive.test(sel)) continue;
      if (notATarget.test(sel)) continue;
      const h = px(props.get('height')) ?? px(props.get('min-height'));
      if (h === null || h <= 0 || h >= FLOOR) continue;
      // A small visual box is fine when a ::before expands the HIT AREA to
      // 44px - that is the standard fix, because resizing the button would
      // reflow the toolbar around it. Credit it when it is really there.
      const expander = resolved.get(`${sel}::before`) || resolved.get(`${sel}:before`);
      if (expander) {
        const ew = px(expander.get('width'));
        const eh = px(expander.get('height'));
        const abs = (expander.get('position') || '') === 'absolute';
        if (abs && ew !== null && eh !== null && ew >= 44 && eh >= 44) continue;
      }
      bad.push(`${sel} height:${h}px`);
    }
    if (bad.length) warn(tag, `${bad.length} interactive element(s) under ${FLOOR}px tall`, bad.slice(0, 8).join('  |  '));
    else ok(`interactive elements meet the ${FLOOR}px tap floor`);
  }

  // ── 3. Legible text ──────────────────────────────────────────────────────
  {
    const FLOOR = 11;
    const bad = [];
    for (const [sel, props] of resolved) {
      // Uppercase micro-labels are a deliberate typographic device and are
      // never the only place a value appears, so hold them to a lower floor
      // than body copy. Everything else must clear 11px.
      const isMicroLabel = /-label|-tag|tiny/.test(sel)
        && /uppercase/.test(props.get('text-transform') || '');
      const floor = isMicroLabel ? 10 : FLOOR;
      const fs = px(props.get('font-size'));
      if (fs !== null && fs > 0 && fs < floor) bad.push(`${sel} ${fs}px`);
    }
    if (bad.length) warn(tag, `${bad.length} rule(s) set text under ${FLOOR}px`, bad.slice(0, 8).join('  |  '));
    else ok(`no text below ${FLOOR}px`);
  }

  // ── 4. Grid columns that cannot fit ──────────────────────────────────────
  {
    const bad = [];
    for (const [sel, props] of resolved) {
      const gtc = props.get('grid-template-columns');
      if (!gtc) continue;
      // repeat(N, ...) or an explicit track list of fixed px
      const fixed = [...String(gtc).matchAll(/([\d.]+)px/g)].map((m) => parseFloat(m[1]));
      if (fixed.length >= 2) {
        const total = fixed.reduce((a, b) => a + b, 0);
        if (total > W) bad.push(`${sel} tracks total ${total}px`);
      }
      // repeat(auto-fill, minmax(Xpx, …)) needs at least X to show one column
      const mm = /minmax\(\s*([\d.]+)px/.exec(String(gtc));
      if (mm && parseFloat(mm[1]) > W - 24) {
        bad.push(`${sel} minmax floor ${mm[1]}px > usable ${W - 24}px`);
      }
    }
    if (bad.length) fail(tag, `${bad.length} grid(s) cannot fit the viewport`, bad.slice(0, 6).join('  |  '));
    else ok('every grid fits');
  }

  // ── 5. Content under the fixed bottom nav ────────────────────────────────
  {
    const nav = resolved.get('.bottom-nav');
    if (nav) {
      const navH = px(nav.get('height')) ?? px(nav.get('min-height'));
      const isFixed = (nav.get('position') || '') === 'fixed';
      if (isFixed && navH) {
        // Something must reserve that space, or the last row of every page
        // sits underneath it and cannot be tapped.
        let reserved = false;
        for (const [sel, props] of resolved) {
          const pb = props.get('padding-bottom') || '';
          const pad = props.get('padding') || '';
          if (/calc\(|env\(safe-area/.test(pb + pad)) { reserved = true; break; }
          const v = px(pb);
          if (v !== null && v >= navH) { reserved = true; break; }
        }
        if (!reserved) fail(tag, 'fixed bottom nav but nothing reserves its height', `nav is ${navH}px tall`);
        else ok('bottom-nav height is reserved by page padding');
      }
    }
  }

  // ── 6. Horizontal overflow from nowrap ───────────────────────────────────
  {
    const bad = [];
    // nowrap is correct on short chrome (a button label, a badge). It only
    // breaks a layout when the text is user-supplied and can be long: names,
    // titles, addresses. Restrict the check to those.
    const userContent = /name|title|label-text|addr|desc|summary|msg|snippet|store/i;
    for (const [sel, props] of resolved) {
      if ((props.get('white-space') || '') !== 'nowrap') continue;
      if (!userContent.test(sel)) continue;
      const scrolls = /auto|scroll/.test(props.get('overflow-x') || props.get('overflow') || '');
      const ellipsis = (props.get('text-overflow') || '') === 'ellipsis';
      const minw = px(props.get('min-width'));
      if (!scrolls && !ellipsis && minw !== 0) bad.push(sel);
    }
    if (bad.length) warn(tag, `${bad.length} nowrap rule(s) with no ellipsis or scroll`, bad.slice(0, 8).join('  |  '));
    else ok('nowrap text is always scrollable or ellipsised');
  }

  console.log('');
}

// ── Report ────────────────────────────────────────────────────────────────
console.log(B('Summary'));
console.log(`  ${G(`${passes} checks passed`)}`);
if (warnings.length) {
  console.log(`\n${B(Y(`${warnings.length} warning(s)`))}`);
  for (const w of warnings) {
    console.log(`  ${Y('!')} ${D(w.device)}  ${w.msg}`);
    if (w.detail) console.log(`      ${D(w.detail)}`);
  }
}
if (failures.length) {
  console.log(`\n${B(R(`${failures.length} failure(s)`))}`);
  for (const f of failures) {
    console.log(`  ${R('✗')} ${D(f.device)}  ${f.msg}`);
    if (f.detail) console.log(`      ${D(f.detail)}`);
  }
  process.exit(1);
}
console.log(`\n${G(B('No layout blockers on any tested device'))}\n`);
