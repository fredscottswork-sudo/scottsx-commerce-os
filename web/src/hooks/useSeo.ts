/**
 * Per-page document metadata: title, description, canonical URL and the
 * Open Graph / Twitter tags used when a link is shared.
 *
 * Why this exists
 * ---------------
 * This is a single-page app, so every route was served with the one <title>
 * and <meta description> baked into index.html. Two consequences:
 *
 *   1. Every entry in sitemap.xml pointed at a page whose title was
 *      "ScottsTechX — Uganda's Marketplace". Search results are built from the
 *      title and description, so 40 distinct URLs competed as near-duplicates.
 *   2. Pasting a product link into WhatsApp, Facebook or X produced no preview
 *      — no image, no price, just a bare URL. For a marketplace where sellers
 *      share listings, that is a commercial problem, not a cosmetic one.
 *
 * Scope, honestly stated
 * ----------------------
 * These tags are applied by JavaScript after the bundle runs. Googlebot
 * renders JavaScript and will see them. Most social scrapers (WhatsApp,
 * Facebook, Twitter, Slack, iMessage) do NOT execute JavaScript — they read
 * the HTML as served. Making previews work for those requires server-side
 * rendering or a prerender layer for crawler user-agents, which is a much
 * larger change and is noted as a follow-up in STATUS.md.
 *
 * So: this fixes search-engine titles and descriptions today, and puts the
 * correct tags in the DOM ready for a prerender layer to serve later.
 */
import { useEffect } from 'react';

/** Site-wide defaults, matching index.html so the two never disagree. */
const DEFAULT_TITLE = "ScottsTechX — Uganda's Marketplace";
const DEFAULT_DESCRIPTION =
  "ScottsTechX — Uganda's AI-powered marketplace. Buy and sell electronics, " +
  'fashion, home goods and more with verified local sellers.';
const SITE_NAME = 'ScottsTechX';

export interface SeoOptions {
  /** Page title. The site name is appended unless the title already ends with it. */
  title?: string;
  /** Meta description. Trimmed to ~160 characters, the length Google displays. */
  description?: string;
  /** Absolute URL of a representative image, for link previews. */
  image?: string;
  /** 'website' for pages, 'product' for a listing, 'profile' for a storefront. */
  type?: 'website' | 'product' | 'profile' | 'article';
  /**
   * Set true on pages that must never appear in search results — anything
   * behind auth, or a URL with user-specific query parameters.
   */
  noIndex?: boolean;
}

/** Create the tag if absent, then set its content. Never duplicates. */
function setMeta(selector: string, attr: 'name' | 'property', key: string, content: string) {
  let el = document.head.querySelector<HTMLMetaElement>(selector);
  if (!el) {
    el = document.createElement('meta');
    el.setAttribute(attr, key);
    document.head.appendChild(el);
  }
  el.setAttribute('content', content);
}

function removeMeta(selector: string) {
  document.head.querySelector(selector)?.remove();
}

/**
 * A description longer than about 160 characters is truncated by Google
 * mid-word. Cut at a word boundary and add an ellipsis instead.
 */
function clampDescription(text: string, max = 160): string {
  const clean = text.replace(/\s+/g, ' ').trim();
  if (clean.length <= max) return clean;
  const cut = clean.slice(0, max - 1);
  const lastSpace = cut.lastIndexOf(' ');
  return `${(lastSpace > max * 0.6 ? cut.slice(0, lastSpace) : cut).trimEnd()}…`;
}

export function useSeo({ title, description, image, type = 'website', noIndex = false }: SeoOptions) {
  useEffect(() => {
    // ── Title ────────────────────────────────────────────────────────────
    const fullTitle = !title
      ? DEFAULT_TITLE
      : title.includes(SITE_NAME)
        ? title
        : `${title} | ${SITE_NAME}`;
    document.title = fullTitle;

    // ── Description ──────────────────────────────────────────────────────
    const desc = clampDescription(description || DEFAULT_DESCRIPTION);
    setMeta('meta[name="description"]', 'name', 'description', desc);

    // ── Canonical ────────────────────────────────────────────────────────
    // Query strings create endless near-duplicate URLs (/search?q=…&page=2).
    // The canonical points at the clean path so Google consolidates them.
    const canonicalHref = `${window.location.origin}${window.location.pathname}`;
    let link = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (!link) {
      link = document.createElement('link');
      link.rel = 'canonical';
      document.head.appendChild(link);
    }
    link.href = canonicalHref;

    // ── Open Graph (WhatsApp, Facebook, LinkedIn, Slack, iMessage) ────────
    setMeta('meta[property="og:title"]', 'property', 'og:title', fullTitle);
    setMeta('meta[property="og:description"]', 'property', 'og:description', desc);
    setMeta('meta[property="og:type"]', 'property', 'og:type', type);
    setMeta('meta[property="og:url"]', 'property', 'og:url', canonicalHref);
    setMeta('meta[property="og:site_name"]', 'property', 'og:site_name', SITE_NAME);

    // ── Twitter/X ────────────────────────────────────────────────────────
    // summary_large_image only renders if an image is actually present.
    setMeta('meta[name="twitter:card"]', 'name', 'twitter:card',
      image ? 'summary_large_image' : 'summary');
    setMeta('meta[name="twitter:title"]', 'name', 'twitter:title', fullTitle);
    setMeta('meta[name="twitter:description"]', 'name', 'twitter:description', desc);

    if (image) {
      // Scrapers reject relative paths, so only ever emit an absolute URL.
      const absolute = image.startsWith('http')
        ? image
        : `${window.location.origin}${image.startsWith('/') ? '' : '/'}${image}`;
      setMeta('meta[property="og:image"]', 'property', 'og:image', absolute);
      setMeta('meta[name="twitter:image"]', 'name', 'twitter:image', absolute);
    } else {
      // A stale image from the previously viewed page would be worse than none.
      removeMeta('meta[property="og:image"]');
      removeMeta('meta[name="twitter:image"]');
    }

    // ── Indexing ─────────────────────────────────────────────────────────
    if (noIndex) {
      setMeta('meta[name="robots"]', 'name', 'robots', 'noindex, nofollow');
    } else {
      removeMeta('meta[name="robots"]');
    }
  }, [title, description, image, type, noIndex]);
}
