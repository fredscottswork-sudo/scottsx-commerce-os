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
  return q.split(',').some((part) => {
    let ok = true;
    for (const m of part.matchAll(/\(\s*(min|max)-width:\s*([\d.]+)px\s*\)/g)) {
      const v = parseFloat(m[2]);
      if (m[1] === 'min' && !(WIDTH >= v)) ok = false;
      if (m[1] === 'max' && !(WIDTH <= v)) ok = false;
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

// 5. Arithmetic: does the whole AI card fit the space left under the chrome?
if (WIDTH <= 620) {
  const CHROME = 414;                 // measured: topbar+mainnav+padding+header+rail+gap+bottomnav
  const HEIGHT = 780;                 // reference phone
  const availCard = HEIGHT - CHROME;
  const head = 48, composer = 63;
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

  const cardMax = aiChat['max-height']?.value || '';
  const cardSub = resolveCalc(cardMax);
  if (cardSub === null) {
    bad(`.ai-chat has no viewport-relative max-height (got "${cardMax || 'none'}") `
      + '— nothing bounds the card to the screen');
  } else if (cardSub < CHROME) {
    bad(`.ai-chat subtracts only ${cardSub}px but the chrome above it is ${CHROME}px `
      + `-> card overflows by ~${CHROME - cardSub}px`);
  } else {
    ok(`.ai-chat max-height subtracts ${cardSub}px >= ${CHROME}px of chrome `
      + `-> card fits in ${HEIGHT - cardSub}px`);
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
