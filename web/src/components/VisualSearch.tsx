/**
 * VisualSearch — "snap it, find it" for the marketplace.
 *
 * One shared surface used in three places:
 *   • the STX AI page (inline card, results shown in place),
 *   • the topbar camera modal (results handed to the /search page),
 *   • the search page's own image modal (results replace the listing grid).
 *
 * Guests need no account: /ai/image-upload-search is public. Photos are
 * compressed on-device before upload, and the filename + optional hint are
 * sent along as search signal so the tool is still useful before a vision
 * model key is configured.
 */
import { useRef, useState } from 'react';
import { Camera, Link2, Loader2, Sparkles, Upload, X } from 'lucide-react';
import type { AiSearchResult } from '../api/types';
import { searchByImageUrl, searchByUploadedImage } from '../lib/imageSearch';
import { useToast } from '../store/ToastContext';
import { ProductGrid } from './ProductCard';
import { Btn } from './ui';

export function VisualSearch({
  onResults,
  showResults = true,
  compact = false,
}: {
  onResults?: (r: AiSearchResult) => void;
  showResults?: boolean;
  compact?: boolean;
}) {
  const { toast } = useToast();
  const fileRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState('');
  const [url, setUrl] = useState('');
  const [hint, setHint] = useState('');
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<AiSearchResult | null>(null);

  const [dragOver, setDragOver] = useState(false);

  const pick = (f: File | null) => {
    if (!f) return;
    if (!/^image\//.test(f.type)) {
      toast('Please choose an image file (JPEG, PNG or WEBP)', 'warning');
      return;
    }
    setFile(f);
    setUrl('');
    if (preview) URL.revokeObjectURL(preview);
    setPreview(URL.createObjectURL(f));
  };

  const clear = () => {
    if (preview) URL.revokeObjectURL(preview);
    setFile(null);
    setPreview('');
    setUrl('');
    setResult(null);
    if (fileRef.current) fileRef.current.value = '';
  };

  const run = async () => {
    if (!file && !url.trim()) {
      toast('Upload a photo or paste an image URL first', 'warning');
      return;
    }
    setBusy(true);
    try {
      const r = file
        ? await searchByUploadedImage(file, hint.trim() || undefined)
        : await searchByImageUrl(url.trim(), hint.trim() || undefined);
      setResult(r);
      onResults?.(r);
      if (r.products.length === 0) toast('No visual matches — try adding a hint', 'info');
    } catch (e: any) {
      toast(e?.message || 'Image search failed', 'error');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div
      className={`visual-search ${compact ? 'compact' : ''}`}
      onPaste={(e) => {
        const f = Array.from(e.clipboardData?.files ?? []).find((x) => x.type.startsWith('image/'));
        if (f) {
          e.preventDefault();
          pick(f);
        }
      }}
    >
      <div className="row-between mb-12">
        <h3 className="card-title"><Camera size={16} /> Search by photo</h3>
        {result && (
          <Btn size="sm" variant="ghost" icon={<X size={13} />} onClick={clear}>Clear</Btn>
        )}
      </div>

      <div
        className={`vs-drop ${dragOver ? 'vs-drop--over' : ''}`}
        onClick={() => fileRef.current?.click()}
        onDrop={(e) => {
          e.preventDefault();
          setDragOver(false);
          pick(Array.from(e.dataTransfer?.files ?? []).find((x) => x.type.startsWith('image/')) ?? null);
        }}
        onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
        onDragLeave={() => setDragOver(false)}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); fileRef.current?.click(); } }}
      >
        {preview ? (
          <img src={preview} alt="Selected" className="vs-preview" />
        ) : (
          <span className="vs-drop-inner">
            <Upload size={20} />
            <span className="semi">Drop a photo here or click to choose</span>
            <span className="tiny muted-2">JPEG, PNG or WEBP — compressed on your device</span>
          </span>
        )}
      </div>
      <input
        ref={fileRef}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        className="visually-hidden"
        aria-label="Upload a photo to search by"
        onChange={(e) => pick(e.target.files?.[0] ?? null)}
      />

      <div className="vs-or"><span>or paste a link</span></div>

      <div className="searchbar searchbar-sm">
        <Link2 size={15} className="muted-2" />
        <input
          value={url}
          onChange={(e) => { setUrl(e.target.value); if (file) { setFile(null); if (preview) URL.revokeObjectURL(preview); setPreview(''); } }}
          placeholder="https://…/photo.jpg"
          aria-label="Image URL"
        />
      </div>

      <div className="mt-10">
        <input
          className="input"
          value={hint}
          onChange={(e) => setHint(e.target.value)}
          placeholder="What is it? e.g. red Nike trainers (optional, sharpens the match)"
          aria-label="Describe the item (optional)"
        />
      </div>

      <Btn variant="primary" className="mt-12 grow" onClick={() => void run()} loading={busy} icon={<Sparkles size={15} />}>
        Find matches
      </Btn>

      {showResults && result && (
        <div className="mt-16">
          <p className="tiny semi muted mb-8">{result.explanation}</p>
          {result.products.length > 0 ? (
            <ProductGrid products={result.products.slice(0, 12)} />
          ) : (
            <p className="tiny muted-2">
              No visual matches — try a more specific hint, or browse the catalogue below.
            </p>
          )}
        </div>
      )}

      {busy && (
        <p className="tiny muted-2 mt-10 row" style={{ gap: 7 }}>
          <Loader2 size={13} className="anim-spin" /> Reading the photo and searching…
        </p>
      )}
    </div>
  );
}
