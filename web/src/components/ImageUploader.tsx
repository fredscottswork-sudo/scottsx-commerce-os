/**
 * ImageUploader — pick photos from the device (camera roll or camera), or paste
 * a link.
 *
 * Sellers list from a phone, where the photo is in the gallery and there is no
 * URL to paste, so "paste a public link" was effectively unusable on mobile.
 *
 * Photos are downscaled and re-encoded in the browser BEFORE upload: a modern
 * phone camera produces 4-12 MB files, which are slow and expensive over a
 * Ugandan mobile connection and would be rejected by the 3 MB server limit.
 * Resizing to a 1600px long edge keeps listing photos sharp at a fraction of
 * the bytes.
 */
import { useCallback, useRef, useState } from 'react';
import { ImagePlus, Link2, Loader2, Trash2, Star, AlertCircle } from 'lucide-react';
import { multipart, resolveMediaUrl } from '../api/client';
import { Btn, Input } from './ui';

/** Longest edge we keep. Comfortably above what any product card renders. */
const MAX_EDGE = 1600;
const TARGET_TYPE = 'image/jpeg';
const TARGET_QUALITY = 0.85;
/** Server hard limit is 3 MB; stay clear of it. */
const MAX_UPLOAD_BYTES = 2.6 * 1024 * 1024;

export type UploadedImage = { url: string; width?: number | null; height?: number | null };

/** Resolve a stored URL for display (API paths need the API origin prefix). */
export const resolveImageUrl = resolveMediaUrl;

/**
 * Downscale in a canvas. Falls back to the original file when the browser
 * cannot decode it (the server validates the bytes regardless).
 */
async function shrink(file: File): Promise<Blob> {
  if (typeof document === 'undefined' || !file.type.startsWith('image/')) return file;
  // Nothing to gain for an already-small file.
  if (file.size < 320 * 1024) return file;

  const url = URL.createObjectURL(file);
  try {
    const img = await new Promise<HTMLImageElement>((resolve, reject) => {
      const el = new Image();
      el.onload = () => resolve(el);
      el.onerror = () => reject(new Error('decode failed'));
      el.src = url;
    });
    const longest = Math.max(img.naturalWidth, img.naturalHeight);
    const scale = longest > MAX_EDGE ? MAX_EDGE / longest : 1;
    if (scale === 1 && file.size <= MAX_UPLOAD_BYTES) return file;

    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(img.naturalWidth * scale));
    canvas.height = Math.max(1, Math.round(img.naturalHeight * scale));
    const ctx = canvas.getContext('2d');
    if (!ctx) return file;
    ctx.drawImage(img, 0, 0, canvas.width, canvas.height);

    const blob = await new Promise<Blob | null>((resolve) =>
      canvas.toBlob(resolve, TARGET_TYPE, TARGET_QUALITY)
    );
    if (!blob) return file;
    // Only take the re-encode if it actually helped.
    return blob.size < file.size ? blob : file;
  } catch {
    return file;
  } finally {
    URL.revokeObjectURL(url);
  }
}

export function ImageUploader({
  images,
  onChange,
  max = 8,
  disabled,
}: {
  images: string[];
  onChange: (next: string[]) => void;
  max?: number;
  disabled?: boolean;
}) {
  const [busy, setBusy] = useState(false);
  const [progress, setProgress] = useState<{ done: number; total: number } | null>(null);
  const [error, setError] = useState('');
  const [linkMode, setLinkMode] = useState(false);
  const [link, setLink] = useState('');
  const inputRef = useRef<HTMLInputElement | null>(null);

  const addFiles = useCallback(
    async (files: FileList | null) => {
      if (!files || files.length === 0) return;
      setError('');
      const room = max - images.length;
      if (room <= 0) {
        setError(`You can attach up to ${max} photos`);
        return;
      }
      const chosen = Array.from(files).slice(0, room);
      setBusy(true);
      setProgress({ done: 0, total: chosen.length });

      const added: string[] = [];
      for (let i = 0; i < chosen.length; i++) {
        try {
          const blob = await shrink(chosen[i]);
          if (blob.size > MAX_UPLOAD_BYTES + 400 * 1024) {
            setError(`"${chosen[i].name}" is too large even after resizing — try a smaller photo`);
            continue;
          }
          const form = new FormData();
          form.append('image', blob, chosen[i].name.replace(/\.[^.]+$/, '') + '.jpg');
          const res = (await multipart('/uploads/images', form)) as UploadedImage;
          if (res?.url) added.push(res.url);
        } catch (err) {
          setError(err instanceof Error ? err.message : 'Upload failed');
        }
        setProgress({ done: i + 1, total: chosen.length });
      }

      if (added.length) onChange([...images, ...added]);
      setBusy(false);
      setProgress(null);
      if (inputRef.current) inputRef.current.value = '';
    },
    [images, max, onChange]
  );

  const addLink = () => {
    const v = link.trim();
    if (!v) return;
    if (!/^https?:\/\//i.test(v)) {
      setError('A link must start with http:// or https://');
      return;
    }
    if (images.length >= max) {
      setError(`You can attach up to ${max} photos`);
      return;
    }
    onChange([...images, v]);
    setLink('');
    setError('');
  };

  const remove = (i: number) => onChange(images.filter((_, n) => n !== i));
  const makeCover = (i: number) => {
    if (i === 0) return;
    const next = [...images];
    const [pick] = next.splice(i, 1);
    onChange([pick, ...next]);
  };

  return (
    <div className="uploader">
      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp,image/gif"
        multiple
        hidden
        aria-label="Choose product images"
        data-testid="image-file-input"
        onChange={(e) => void addFiles(e.target.files)}
      />

      <div className="uploader-actions">
        <Btn
          type="button"
          variant="primary"
          icon={busy ? <Loader2 size={15} className="spin" /> : <ImagePlus size={15} />}
          disabled={disabled || busy || images.length >= max}
          onClick={() => inputRef.current?.click()}
          data-testid="choose-photos"
        >
          {busy
            ? progress
              ? `Uploading ${progress.done}/${progress.total}…`
              : 'Uploading…'
            : images.length
              ? 'Add more photos'
              : 'Upload photos'}
        </Btn>
        <Btn
          type="button"
          variant="ghost"
          icon={<Link2 size={15} />}
          disabled={disabled || busy}
          onClick={() => setLinkMode((v) => !v)}
        >
          Use a link
        </Btn>
      </div>

      <p className="tiny muted uploader-hint">
        Take a photo or choose from your gallery — up to {max}. Large photos are resized
        automatically. The first photo is the cover.
      </p>

      {linkMode && (
        <div className="row uploader-link">
          <Input
            className="grow"
            value={link}
            onChange={(e) => setLink(e.target.value)}
            placeholder="https://…/product.jpg"
            onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addLink(); } }}
          />
          <Btn type="button" onClick={addLink}>Add</Btn>
        </div>
      )}

      {error && (
        <p className="tiny err-text uploader-error" role="alert">
          <AlertCircle size={13} /> {error}
        </p>
      )}

      {images.length > 0 && (
        <ul className="uploader-grid" data-testid="uploader-grid">
          {images.map((url, i) => (
            <li key={`${url}-${i}`} className={`uploader-thumb ${i === 0 ? 'is-cover' : ''}`}>
              <img src={resolveImageUrl(url)} alt={i === 0 ? 'Cover photo' : `Photo ${i + 1}`} loading="lazy" />
              {i === 0 && <span className="uploader-cover-tag">Cover</span>}
              <div className="uploader-thumb-actions">
                {i !== 0 && (
                  <button
                    type="button"
                    className="uploader-icon-btn"
                    title="Make cover photo"
                    aria-label={`Make photo ${i + 1} the cover`}
                    onClick={() => makeCover(i)}
                  >
                    <Star size={14} />
                  </button>
                )}
                <button
                  type="button"
                  className="uploader-icon-btn danger"
                  title="Remove photo"
                  aria-label={`Remove photo ${i + 1}`}
                  onClick={() => remove(i)}
                >
                  <Trash2 size={14} />
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export default ImageUploader;
