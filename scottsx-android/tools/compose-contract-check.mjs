#!/usr/bin/env node
/**
 * ScottsTechX Android — Compose API contract check.
 *
 * The Kotlin syntax checker runs without the Android SDK, so it cannot resolve
 * androidx symbols and therefore cannot catch a MISUSE of a real API. That is
 * the blind spot that let a genuine compile error ship: `WindowInsets.safeDrawing`
 * is a `@Composable` getter, and reading it from a plain (non-@Composable)
 * function is a hard error — "@Composable invocations can only happen from the
 * context of a @Composable function".
 *
 * This encodes the Compose rules that a compiler WOULD enforce, so the same
 * class of bug fails here instead of in CI ten minutes later.
 *
 * Usage:  node tools/compose-contract-check.mjs      (from scottsx-android/)
 */
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, dirname, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const SRC = join(ROOT, 'app/src/main/java/com/scottsx/app');

let pass = 0, fail = 0;
const failures = [];
const ok = (n) => { pass++; console.log(`  \x1b[32m✓\x1b[0m ${n}`); };
const bad = (n, d = '') => { fail++; failures.push(`${n}${d ? ` — ${d}` : ''}`); console.log(`  \x1b[31m✗\x1b[0m ${n}${d ? ` — ${d}` : ''}`); };
const ok_if = (n, c, d = '') => (c ? ok(n) : bad(n, d));

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
const rel = (f) => relative(ROOT, f);

console.log(`\n\x1b[1mCompose API contract check\x1b[0m  (${files.length} Kotlin files)\n`);

/**
 * Split a file into top-level function declarations with the annotations that
 * immediately precede them, so we can ask "is this function @Composable?".
 */
function functions(src) {
  const lines = src.split('\n');
  const out = [];
  for (let i = 0; i < lines.length; i++) {
    // Top-level only: a `fun` indented inside a composable body is a local
    // function and still runs in a composable context, so it is NOT an error.
    const m = /^(?:internal |private |public )?fun\s+([\w.<>]+)/.exec(lines[i]);
    if (!m) continue;
    // Walk backwards over annotation lines and comments to find @Composable.
    let composable = /@Composable/.test(lines[i]);
    for (let j = i - 1; j >= 0; j--) {
      const l = lines[j].trim();
      if (l.startsWith('@')) { if (l.startsWith('@Composable')) composable = true; continue; }
      if (l === '' || l.startsWith('//') || l.startsWith('*') || l.startsWith('/*')) continue;
      break;
    }
    // Body: to the next top-level declaration.
    let end = lines.length;
    for (let k = i + 1; k < lines.length; k++) {
      if (/^(?:@\w+|(?:internal |private |public )?(?:fun|val|var|class|object|enum|data class)\s)/.test(lines[k])) { end = k; break; }
    }
    out.push({ name: m[1], composable, body: lines.slice(i, end).join('\n'), line: i + 1 });
  }
  return out;
}

/** Symbols that are @Composable getters/functions: reading them needs context. */
const COMPOSABLE_READS = [
  'WindowInsets.safeDrawing', 'WindowInsets.safeContent', 'WindowInsets.safeGestures',
  'WindowInsets.systemBars', 'WindowInsets.statusBars', 'WindowInsets.navigationBars',
  'WindowInsets.ime', 'WindowInsets.displayCutout',
  'MaterialTheme.colorScheme', 'MaterialTheme.typography', 'MaterialTheme.shapes',
  'LocalContext.current', 'LocalDensity.current', 'LocalConfiguration.current',
  'rememberCoroutineScope(', 'rememberScrollState(', 'rememberNavController(',
  'collectAsState(', 'isSystemInDarkTheme(',
];

console.log('\x1b[1m1. @Composable-only APIs are read from a @Composable context\x1b[0m');
{
  let offenders = [];
  for (const f of files) {
    const src = readFileSync(f, 'utf8');
    for (const fn of functions(src)) {
      if (fn.composable) continue;
      // `remember {}` etc. inside a lambda passed to a composable is still a
      // composable context, but a top-level non-composable fun is never one.
      for (const sym of COMPOSABLE_READS) {
        if (fn.body.includes(sym)) {
          offenders.push(`${rel(f)}:${fn.line} fun ${fn.name}() reads ${sym}`);
          break;
        }
      }
    }
  }
  ok_if('no plain function reads a @Composable-only API', offenders.length === 0,
    offenders.slice(0, 5).join(' | '));
}

console.log('\n\x1b[1m2. Modifier extensions that touch insets are @Composable\x1b[0m');
{
  let offenders = [];
  for (const f of files) {
    const src = readFileSync(f, 'utf8');
    // Match "fun Modifier.foo(...)" declarations and check the annotation.
    const re = /(?:@Composable\s*\n\s*)?(?:internal |private |public )?fun\s+Modifier\.(\w+)\(/g;
    let m;
    while ((m = re.exec(src))) {
      const decl = src.slice(Math.max(0, m.index - 220), m.index + 200);
      const isComposable = /@Composable/.test(decl.slice(0, decl.indexOf('fun Modifier.') + 1));
      const bodyStart = m.index;
      const body = src.slice(bodyStart, bodyStart + 400);
      const usesInsets = /WindowInsets\./.test(body);
      if (usesInsets && !isComposable) offenders.push(`${rel(f)} Modifier.${m[1]}()`);
    }
  }
  ok_if('every inset-reading Modifier extension is annotated @Composable',
    offenders.length === 0, offenders.join(', '));
}

console.log('\n\x1b[1m3. Imports exist for every symbol used\x1b[0m');
{
  // Catch the classic "used it, forgot the import" that only the compiler sees.
  const needs = [
    ['statusBarSpacer(', 'com.scottsx.app.ui.components.statusBarSpacer'],
    ['navBarSpacer(', 'com.scottsx.app.ui.components.navBarSpacer'],
    ['bottomInset(', 'com.scottsx.app.ui.components.bottomInset'],
    ['topInset(', 'com.scottsx.app.ui.components.topInset'],
    ['formatUgxCompact(', 'com.scottsx.app.ui.components.formatUgxCompact'],
    ['TextOverflow.', 'androidx.compose.ui.text.style.TextOverflow'],
    ['ContentScale.', 'androidx.compose.ui.layout.ContentScale'],
    ['aspectRatio(', 'androidx.compose.foundation.layout.aspectRatio'],
    ['windowInsetsPadding(', 'androidx.compose.foundation.layout.windowInsetsPadding'],
    ['imePadding(', 'androidx.compose.foundation.layout.imePadding'],
    ['rememberCoroutineScope(', 'androidx.compose.runtime.rememberCoroutineScope'],
    ['enableEdgeToEdge(', 'androidx.activity.enableEdgeToEdge'],
  ];
  const missing = [];
  for (const f of files) {
    const src = readFileSync(f, 'utf8');
    const bodyOnly = src.split('\n').filter((l) => !/^\s*import\s/.test(l)).join('\n');
    // Definitions live in the same package -> no import needed there.
    const pkg = (/^package\s+(\S+)/m.exec(src) || [])[1] || '';
    for (const [use, imp] of needs) {
      if (!bodyOnly.includes(use)) continue;
      if (src.includes(`import ${imp}`)) continue;
      // A fully-qualified use needs no import: strip every occurrence that is
      // preceded by its own package path, then re-test.
      const qualified = new RegExp(`${imp.replace(/\./g, '\\.')}\\b`, 'g');
      if (!qualified.test(bodyOnly) ? false : !bodyOnly.replace(qualified, '').includes(use)) continue;
      const symbolPkg = imp.slice(0, imp.lastIndexOf('.'));
      if (symbolPkg === pkg) continue;                 // same package
      if (bodyOnly.includes(`fun ${use.replace('(', '')}`)) continue; // defines it
      if (bodyOnly.includes(`fun Modifier.${use.replace('(', '')}`)) continue;
      missing.push(`${rel(f)} uses ${use} without importing ${imp}`);
    }
  }
  ok_if('every helper used is imported (or defined in the same package)',
    missing.length === 0, missing.slice(0, 6).join(' | '));
}

console.log('\n\x1b[1m4. No dangling references to removed APIs\x1b[0m');
{
  const pc = readFileSync(join(SRC, 'ui/components/ProductCard.kt'), 'utf8');
  // ProductCard no longer owns wishlist state; make sure nothing still assigns it.
  ok_if('ProductCard does not assign to its `wished` parameter',
    !/wished\s*=\s*!wished/.test(pc));
  ok_if('ProductCard declares wished as a parameter, not a local var',
    /wished:\s*Boolean/.test(pc) && !/var wished/.test(pc));

  // Every ProductCard call site must pass args the signature actually accepts.
  const sig = /fun ProductCard\(([\s\S]*?)\n\)/.exec(pc);
  const params = sig ? [...sig[1].matchAll(/^\s*(\w+):/gm)].map((m) => m[1]) : [];
  ok_if('ProductCard signature parsed', params.length > 0, params.join(','));
  const badArgs = [];
  for (const f of files) {
    const src = readFileSync(f, 'utf8');
    for (const call of src.matchAll(/ProductCard\(([\s\S]{0,600}?)\n\s*\)/g)) {
      for (const a of call[1].matchAll(/^\s*(\w+)\s*=/gm)) {
        if (!params.includes(a[1])) badArgs.push(`${rel(f)}: ProductCard(${a[1]} = …)`);
      }
    }
  }
  ok_if('every ProductCard call site uses only real parameters',
    badArgs.length === 0, badArgs.slice(0, 5).join(' | '));
}

console.log(`\n\x1b[1mResult: ${pass} passed, ${fail} failed\x1b[0m`);
if (fail) { console.log('\nFailures:'); failures.forEach((f) => console.log(`  - ${f}`)); }
process.exit(fail ? 1 : 0);
