/**
 * ScottsTechX — image search plumbing shared by the topbar, the search page
 * and the STX AI page.
 *
 * Photos are compressed on-device (canvas → JPEG) before they leave the
 * browser, so a 12 MP camera shot becomes a ~200 KB upload instead of a
 * multi-megabyte one, and the public `/ai/image-upload-search` endpoint stays
 * comfortably inside Fastify's 8 MB multipart cap. Guests never need an
 * account: the route has no auth.
 */
import { multipart } from '../api/client';
import { aiService } from '../api/services';
import type { AiSearchResult } from '../api/types';

const MAX_DIM = 1280;
const JPEG_QUALITY = 0.82;

export interface CompressedImage {
  blob: Blob;
  dataUrl: string;
  filename: string;
}

/** Downscale + re-encode a picked photo, returning it as a JPEG blob. */
export async function compressImage(file: File): Promise<CompressedImage> {
  const objectUrl = URL.createObjectURL(file);
  try {
    const img = await new Promise<HTMLImageElement>((resolve, reject) => {
      const el = new Image();
      el.onload = () => resolve(el);
      el.onerror = () => reject(new Error('Could not read that image — try another file.'));
      el.src = objectUrl;
    });

    const scale = Math.min(1, MAX_DIM / Math.max(img.width || MAX_DIM, img.height || MAX_DIM));
    const width = Math.max(1, Math.round((img.width || MAX_DIM) * scale));
    const height = Math.max(1, Math.round((img.height || MAX_DIM) * scale));

    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('This browser cannot process photos here.');
    ctx.drawImage(img, 0, 0, width, height);

    const dataUrl = canvas.toDataURL('image/jpeg', JPEG_QUALITY);
    const blob = await new Promise<Blob>((resolve, reject) => {
      canvas.toBlob(
        (b) => (b ? resolve(b) : reject(new Error('Could not encode the photo.'))),
        'image/jpeg',
        JPEG_QUALITY
      );
    });

    const base = (file.name || 'photo').replace(/\.[a-z0-9]+$/i, '').replace(/[^\w-]+/g, ' ') || 'photo';
    return { blob, dataUrl, filename: `${base.trim() || 'photo'}.jpg` };
  } finally {
    URL.revokeObjectURL(objectUrl);
  }
}

/**
 * Upload a photo and search the catalogue with it in one call.
 * Public (no auth); the server mines the filename + your hint for terms.
 */
export async function searchByUploadedImage(file: File, hint?: string): Promise<AiSearchResult> {
  const { blob, filename } = await compressImage(file);
  const form = new FormData();
  form.append('image', blob, filename);
  if (hint?.trim()) form.append('hint', hint.trim());
  return multipart('/ai/image-upload-search', form) as Promise<AiSearchResult>;
}

/** Search by a pasted image URL (kept for callers that have a link already). */
export async function searchByImageUrl(url: string, hint?: string): Promise<AiSearchResult> {
  return aiService.imageSearch({ imageUrl: url.trim(), hint: hint?.trim() || undefined });
}

/* ── Cross-page handoff ─────────────────────────────────────────────────────
 * The topbar camera opens a modal, and on success the shopper lands on
 * /search with the results already loaded. sessionStorage carries the result
 * across the navigation without polluting the URL or the query string.
 */
const STASH_KEY = 'stx:image-search-result';

export function stashImageSearchResult(r: AiSearchResult): void {
  try {
    sessionStorage.setItem(STASH_KEY, JSON.stringify(r));
  } catch {
    /* private mode — the search page just runs a normal search */
  }
}

export function consumeStashedImageSearch(): AiSearchResult | null {
  try {
    const raw = sessionStorage.getItem(STASH_KEY);
    if (!raw) return null;
    sessionStorage.removeItem(STASH_KEY);
    return JSON.parse(raw) as AiSearchResult;
  } catch {
    return null;
  }
}
