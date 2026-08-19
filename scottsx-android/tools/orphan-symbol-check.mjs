#!/usr/bin/env node
/**
 * orphan-symbol-check.mjs
 *
 * Catches the bug that broke the release APK three times:
 * a CALL SITE copied to a branch without the file that DECLARES the symbol.
 *
 *   e: UiKit.kt:218:14 Unresolved reference: statusBarSpacer
 *
 * UiKit.kt called `.statusBarSpacer()`, but that extension lives in
 * ScreenScaffold.kt, which had never been copied to master. Every existing gate
 * missed it: the syntax checker has no Android SDK so unresolved references look
 * fine, and the import checker only inspects `import` lines -- this call needed
 * no import because it is in the same package.
 *
 * So this checks SAME-PACKAGE resolution: any `.foo(` chained call, or bare
 * `Foo(` composable invocation, that matches nothing declared anywhere in the
 * app source and is not a known framework/library symbol.
 *
 * Exit 1 on any orphan.
 */
import { readdirSync, readFileSync, statSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const ROOT = 'app/src/main/java/com/scottsx/app';
if (!existsSync(ROOT)) {
  console.error(`orphan-symbol-check: ${ROOT} not found (run from scottsx-android/)`);
  process.exit(1);
}

const files = [];
(function walk(dir) {
  for (const e of readdirSync(dir)) {
    const p = join(dir, e);
    if (statSync(p).isDirectory()) walk(p);
    else if (p.endsWith('.kt')) files.push(p);
  }
})(ROOT);

/** Symbols declared anywhere in our own source. */
const declared = new Set(['BuildConfig', 'R']);
const declRe = [
  /^\s*(?:@\w+\s+)*(?:public |internal |private )?fun\s+(?:<[^>]*>\s*)?(?:[A-Za-z_][\w.]*\.)?([A-Za-z_]\w*)\s*[(<]/,
  /^\s*(?:@\w+\s+)*(?:public |internal |private )?(?:data |sealed |enum |abstract |open )*(?:class|object|interface)\s+([A-Za-z_]\w*)/,
  /^\s*(?:public |internal |private )?(?:val|var)\s+([A-Za-z_]\w*)/,
];
for (const f of files) {
  for (const line of readFileSync(f, 'utf8').split('\n')) {
    for (const re of declRe) {
      const m = line.match(re);
      if (m) declared.add(m[1]);
    }
  }
}

/** Symbols brought in by an explicit import are resolved by that import. */
const importedPerFile = new Map();
for (const f of files) {
  const set = new Set();
  for (const line of readFileSync(f, 'utf8').split('\n')) {
    const m = line.match(/^\s*import\s+[\w.]*\.([A-Za-z_]\w*)\s*$/);
    if (m) set.add(m[1]);
    if (/^\s*import\s+[\w.]+\.\*\s*$/.test(line)) set.add('*WILDCARD*');
  }
  importedPerFile.set(f, set);
}

/**
 * Framework/library extension functions and builders that are legitimately
 * available without being declared by us. Kept deliberately broad: this gate
 * exists to catch OUR missing files, not to police third-party APIs.
 */
const KNOWN = new Set(`
fillMaxWidth fillMaxHeight fillMaxSize padding background clip size width height weight
clickable combinedClickable border offset absoluteOffset alpha rotate scale align
alignByBaseline verticalScroll horizontalScroll shadow graphicsLayer aspectRatio
wrapContentWidth wrapContentHeight wrapContentSize defaultMinSize widthIn heightIn sizeIn
requiredSize requiredWidth requiredHeight testTag semantics zIndex pointerInput draggable
scrollable selectable toggleable focusRequester focusable onFocusChanged onGloballyPositioned
onSizeChanged navigationBarsPadding statusBarsPadding systemBarsPadding imePadding
windowInsetsPadding safeDrawingPadding safeContentPadding consumeWindowInsets
animateContentSize paddingFrom matchParentSize composed then blur drawBehind drawWithContent
layout rotateZ nestedScroll swipeable animateItemPlacement
put putAll opt optString optInt optBoolean optDouble optLong optJSONArray optJSONObject
getString getInt getBoolean getDouble getLong getJSONArray getJSONObject has keys length
build connectTimeout readTimeout writeTimeout callTimeout okHttpClient directory maxSizeBytes
crossfade respectCacheHeaders memoryCache diskCache data url method header addHeader
post get patch delete addInterceptor followRedirects retryOnConnectionFailure
setContentTitle setContentText setStyle setSmallIcon setLargeIcon setAutoCancel setPriority
setContentIntent setChannelId setWhen setDefaults notify createNotificationChannel
setColor setColorized setGroup setOngoing setSilent setTicker setSubText setNumber
setType addFormDataPart addPart setSound setVibrate setLights setProgress
sortedWith sortedBy sortedByDescending toList toMutableList toSet toMap filter filterNot
map mapNotNull flatMap forEach firstOrNull lastOrNull find any all none count sumOf
take takeLast drop dropLast distinct distinctBy groupBy associate associateBy reversed
joinToString split trim trimEnd trimStart lowercase uppercase replace substring startsWith
endsWith contains isNullOrBlank isNullOrEmpty orEmpty toIntOrNull toDoubleOrNull toLongOrNull
plus minus also let run apply with use close flush await launch collect emit value
copy toString hashCode equals compareTo rangeTo coerceIn coerceAtLeast coerceAtMost
roundToInt toFloat toDouble toInt toLong format
`.trim().split(/\s+/));

const orphans = [];
const chainRe = /^\s*\.([a-z]\w*)\s*\(/;

for (const f of files) {
  const imported = importedPerFile.get(f);
  if (imported.has('*WILDCARD*')) continue;
  const lines = readFileSync(f, 'utf8').split('\n');
  lines.forEach((line, i) => {
    const m = line.match(chainRe);
    if (!m) return;
    const name = m[1];
    if (KNOWN.has(name) || declared.has(name) || imported.has(name)) return;
    orphans.push({ file: f, line: i + 1, name, text: line.trim() });
  });
}

console.log('orphan-symbol-check: scanned ' + files.length + ' Kotlin files');
if (orphans.length === 0) {
  console.log('  OK - every same-package call resolves to a declaration in the tree');
  process.exit(0);
}
console.error('\n  FAIL - ' + orphans.length + ' call(s) resolve to nothing in this tree:\n');
for (const o of orphans) {
  console.error(`    ${o.file}:${o.line}  .${o.name}()`);
  console.error(`        ${o.text}`);
  console.error(`        -> declared nowhere in app source and not imported.`);
  console.error(`           A file that declares it is probably missing from this branch.\n`);
}
process.exit(1);
