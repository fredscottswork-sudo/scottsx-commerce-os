/**
 * ImageSearchButton — "pick a photo, get matches", the same way the STX AI
 * composer attaches a picture: one button, the native file picker, nothing
 * else to fill in. No drop zone, no image URL, no hint field.
 *
 * The chosen photo is compressed on-device and searched straight away; while
 * that runs the button shows a spinner. Hosts that want drag-and-drop or
 * paste support call `searchFile` from the hook directly.
 */
import { useCallback, useRef, useState, type ReactNode } from 'react';
import { ImagePlus, Loader2 } from 'lucide-react';
import type { AiSearchResult } from '../api/types';
import { searchByUploadedImage } from '../lib/imageSearch';
import { useToast } from '../store/ToastContext';

export function useImageSearch(onResults: (r: AiSearchResult) => void) {
  const { toast } = useToast();
  const [busy, setBusy] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const searchFile = useCallback(async (file: File | null | undefined) => {
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      toast('Please choose a photo (JPEG, PNG or WEBP)', 'warning');
      return;
    }
    setBusy(true);
    try {
      const r = await searchByUploadedImage(file);
      onResults(r);
      if (r.products.length === 0) toast('No visual matches for that photo yet', 'info');
    } catch (e: any) {
      toast(e?.message || 'Image search failed', 'error');
    } finally {
      setBusy(false);
    }
  }, [onResults, toast]);

  const open = useCallback(() => inputRef.current?.click(), []);

  /** Hidden picker — render once inside the host. */
  const input = (
    <input
      ref={inputRef}
      type="file"
      accept="image/*"
      style={{ display: 'none' }}
      aria-hidden
      tabIndex={-1}
      onChange={(e) => { const f = e.target.files?.[0]; e.target.value = ''; void searchFile(f); }}
    />
  );

  return { busy, open, searchFile, input };
}

export function ImageSearchButton({
  onResults,
  className = 'btn btn-icon',
  size = 17,
  icon,
}: {
  onResults: (r: AiSearchResult) => void;
  className?: string;
  size?: number;
  icon?: ReactNode;
}) {
  const { busy, open, input } = useImageSearch(onResults);
  return (
    <>
      {input}
      <button
        type="button"
        className={className}
        onClick={open}
        disabled={busy}
        title="Search with a photo"
        aria-label="Search by image"
        aria-busy={busy || undefined}
      >
        {busy ? <Loader2 size={size} className="anim-spin" /> : (icon ?? <ImagePlus size={size} />)}
      </button>
    </>
  );
}
