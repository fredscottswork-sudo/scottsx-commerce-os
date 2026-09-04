import { useEffect, useState } from 'react';

/**
 * Cycles the `placeholder` of a search input through a list of brand
 * watermark phrases, so the topbar/search hero never sits still.
 *
 * Pure `placeholder` swap — no remount, no focus loss, no layout change;
 * the input keeps its value and caret across the rotation.
 */
export function useRotatingPlaceholder(phrases: readonly string[], intervalMs = 3200): string {
  const [index, setIndex] = useState(0);

  useEffect(() => {
    if (phrases.length <= 1) return;
    const timer = setInterval(
      () => setIndex((i) => (i + 1) % phrases.length),
      intervalMs
    );
    return () => clearInterval(timer);
  }, [phrases.length, intervalMs]);

  return phrases[index % phrases.length] ?? '';
}

/** Watermark rotation used by every search surface on the app. */
export const SEARCH_WATERMARKS = [
  'Search phones, fashion, deals and more…',
  'Try “cheapest phone under 1.5M”…',
  'Find it near you on ScottsTechX…',
  'Smart TVs, sneakers, home & living…',
  'Snap a photo and search by image…',
  'Tech, fashion, groceries — all in one…',
] as const;
