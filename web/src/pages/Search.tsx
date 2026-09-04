import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Sparkles, SlidersHorizontal, X, Image as ImageIcon, Mic, MicOff, Search as SearchIcon, Loader2,
} from 'lucide-react';
import { productService, aiService } from '../api/services';
import type { Product, Facets } from '../api/types';
import { formatUgx } from '../api/types';
import { useCart } from '../store/CartContext';
import { useToast } from '../store/ToastContext';
import { ProductGrid } from '../components/ProductCard';
import { VisualSearch } from '../components/VisualSearch';
import {
  Btn, Empty, ErrorBox, SkeletonGrid, Field, Input, Select, Switch, Pagination, Modal, RichText,
} from '../components/ui';
import { useSeo } from '../hooks/useSeo';

type Sort = 'relevance' | 'newest' | 'price_asc' | 'price_desc' | 'rating' | 'popular';

const SORTS: { id: Sort; label: string }[] = [
  { id: 'relevance', label: 'Best match' },
  { id: 'newest', label: 'Newest' },
  { id: 'price_asc', label: 'Price: low → high' },
  { id: 'price_desc', label: 'Price: high → low' },
  { id: 'rating', label: 'Top rated' },
  { id: 'popular', label: 'Most viewed' },
];

const PAGE_SIZE = 24;

/** Minimal typing for the vendor-prefixed Web Speech API. */
type SpeechRecognitionLike = {
  lang: string; continuous: boolean; interimResults: boolean;
  start: () => void; stop: () => void;
  onresult: ((e: any) => void) | null;
  onerror: ((e: any) => void) | null;
  onend: (() => void) | null;
};

export default function Search() {
  useSeo({
    title: 'Search products',
    description:
      'Search thousands of products from verified Ugandan sellers on ScottsTechX. ' +
      'Filter by category, price and location.',
  });

  const [params, setParams] = useSearchParams();
  const { add, favoriteSellerIds, toggleFavoriteSeller } = useCart();
  const { toast } = useToast();

  const q = params.get('q') ?? '';
  const category = params.get('category') ?? '';
  const brand = params.get('brand') ?? '';
  const sort = (params.get('sort') as Sort) || 'relevance';
  const page = Number(params.get('page') || 1);
  const minPrice = params.get('minPrice') ?? '';
  const maxPrice = params.get('maxPrice') ?? '';
  const minRating = params.get('minRating') ?? '';
  const verifiedOnly = params.get('verifiedOnly') === '1';
  const inStock = params.get('inStock') === '1';
  const flashOnly = params.get('flashOnly') === '1';

  const [input, setInput] = useState(q);
  const [products, setProducts] = useState<Product[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [facets, setFacets] = useState<Facets | null>(null);
  const [filtersOpen, setFiltersOpen] = useState(false);

  const [suggestions, setSuggestions] = useState<{ label: string; kind: string }[]>([]);
  const [showSuggest, setShowSuggest] = useState(false);

  const [aiBusy, setAiBusy] = useState(false);
  const [aiNote, setAiNote] = useState('');
  const [imageOpen, setImageOpen] = useState(false);
  const [listening, setListening] = useState(false);
  const recognitionRef = useRef<SpeechRecognitionLike | null>(null);
  const watermark = useRotatingPlaceholder(SEARCH_WATERMARKS);

  useEffect(() => { setInput(q); }, [q]);

  // The topbar camera modal stashes its result and navigates here; pick it
  // up so the shopper lands straight on their visual matches.
  useEffect(() => {
    const stashed = consumeStashedImageSearch();
    if (!stashed) return;
    setProducts(stashed.products);
    setTotal(stashed.products.length);
    setAiNote(stashed.explanation);
  }, []);

  const patch = useCallback((next: Record<string, string | null>, resetPage = true) => {
    const p = new URLSearchParams(params);
    for (const [k, v] of Object.entries(next)) {
      if (v === null || v === '') p.delete(k);
      else p.set(k, v);
    }
    if (resetPage) p.delete('page');
    setParams(p, { replace: true });
  }, [params, setParams]);

  useEffect(() => { productService.facets().then(setFacets).catch(() => undefined); }, []);

  // ── Fetch results whenever the query string changes ──────────────────────
  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError('');
    const query = {
      q: q || undefined,
      category: category || undefined,
      brand: brand || undefined,
      minPrice: minPrice ? Number(minPrice) : undefined,
      maxPrice: maxPrice ? Number(maxPrice) : undefined,
      minRating: minRating ? Number(minRating) : undefined,
      verifiedOnly: verifiedOnly || undefined,
      inStock: inStock || undefined,
      flashOnly: flashOnly || undefined,
      sort,
      page,
      pageSize: PAGE_SIZE,
    };
    const call = q ? productService.search(query) : productService.list(query);
    call
      .then((r) => { if (!alive) return; setProducts(r.products); setTotal(r.total); })
      .catch((e) => { if (alive) setError(e?.message || 'Search failed'); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [q, category, brand, minPrice, maxPrice, minRating, verifiedOnly, inStock, flashOnly, sort, page]);

  // ── Typeahead ────────────────────────────────────────────────────────────
  useEffect(() => {
    if (input.trim().length < 2 || input === q) { setSuggestions([]); return; }
    const t = setTimeout(() => {
      productService.suggest(input.trim())
        .then((r) => setSuggestions(r.suggestions.slice(0, 8)))
        .catch(() => setSuggestions([]));
    }, 180);
    return () => clearTimeout(t);
  }, [input, q]);

  const runSearch = (text: string) => {
    setShowSuggest(false);
    setAiNote('');
    patch({ q: text.trim() || null });
  };

  // ── AI-assisted search: natural language → filters + results ─────────────
  const runAiSearch = async () => {
    const text = input.trim() || q;
    if (!text) { toast('Type what you are looking for first', 'warning'); return; }
    setAiBusy(true);
    setShowSuggest(false);
    try {
      const r = await aiService.search(text, PAGE_SIZE);
      setProducts(r.products);
      setTotal(r.products.length);
      setAiNote(r.explanation);
      // Reflect what the AI understood back into the visible filters.
      patch({
        q: text,
        category: r.filters.category,
        minPrice: r.filters.minPriceMinor ? String(r.filters.minPriceMinor) : null,
        maxPrice: r.filters.maxPriceMinor ? String(r.filters.maxPriceMinor) : null,
        flashOnly: r.filters.flashOnly ? '1' : null,
      });
    } catch (e: any) {
      toast(e?.message || 'AI search failed', 'error');
    } finally {
      setAiBusy(false);
    }
  };

  const onVisualResults = (r: { products: Product[]; explanation: string }) => {
    setProducts(r.products);
    setTotal(r.products.length);
    setAiNote(r.explanation);
    setImageOpen(false);
  };

  const toggleVoice = () => {
    const Ctor = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!Ctor) { toast('Voice search needs Chrome or Edge on this device', 'warning'); return; }
    if (listening) { recognitionRef.current?.stop(); setListening(false); return; }

    const rec: SpeechRecognitionLike = new Ctor();
    rec.lang = 'en-UG';
    rec.continuous = false;
    rec.interimResults = false;
    rec.onresult = async (e: any) => {
      const transcript = e.results?.[0]?.[0]?.transcript ?? '';
      setInput(transcript);
      setListening(false);
      if (!transcript) return;
      setAiBusy(true);
      try {
        const r = await aiService.voiceSearch(transcript);
        setProducts(r.products);
        setTotal(r.products.length);
        setAiNote(r.explanation);
        patch({ q: transcript });
      } catch (err: any) {
        toast(err?.message || 'Voice search failed', 'error');
      } finally {
        setAiBusy(false);
      }
    };
    rec.onerror = () => { setListening(false); toast('Could not hear you — try again', 'error'); };
    rec.onend = () => setListening(false);
    recognitionRef.current = rec;
    rec.start();
    setListening(true);
  };

  const activeFilters = useMemo(() => {
    const chips: { label: string; clear: () => void }[] = [];
    if (category) chips.push({ label: category, clear: () => patch({ category: null }) });
    if (brand) chips.push({ label: brand, clear: () => patch({ brand: null }) });
    if (minPrice) chips.push({ label: `From ${formatUgx(Number(minPrice))}`, clear: () => patch({ minPrice: null }) });
    if (maxPrice) chips.push({ label: `Under ${formatUgx(Number(maxPrice))}`, clear: () => patch({ maxPrice: null }) });
    if (minRating) chips.push({ label: `${minRating}★ and up`, clear: () => patch({ minRating: null }) });
    if (verifiedOnly) chips.push({ label: 'Verified sellers', clear: () => patch({ verifiedOnly: null }) });
    if (inStock) chips.push({ label: 'In stock', clear: () => patch({ inStock: null }) });
    if (flashOnly) chips.push({ label: 'Flash deals', clear: () => patch({ flashOnly: null }) });
    return chips;
  }, [category, brand, minPrice, maxPrice, minRating, verifiedOnly, inStock, flashOnly, patch]);

  const filterPanel = (
    <div className="col">
      <Field label="Category">
        <Select value={category} onChange={(e) => patch({ category: e.target.value || null })}>
          <option value="">All categories</option>
          {facets?.categories.map((c) => <option key={c.name} value={c.name}>{c.name}</option>)}
        </Select>
      </Field>
      <Field label="Brand">
        <Select value={brand} onChange={(e) => patch({ brand: e.target.value || null })}>
          <option value="">All brands</option>
          {facets?.brands.map((b) => <option key={b.name} value={b.name}>{b.name}</option>)}
        </Select>
      </Field>
      <div className="form-row">
        <Field label="Min price (UGX)">
          <Input type="number" min={0} value={minPrice} placeholder="0"
            onChange={(e) => patch({ minPrice: e.target.value || null })} />
        </Field>
        <Field label="Max price (UGX)">
          <Input type="number" min={0} value={maxPrice} placeholder="Any"
            onChange={(e) => patch({ maxPrice: e.target.value || null })} />
        </Field>
      </div>
      <Field label="Minimum rating">
        <Select value={minRating} onChange={(e) => patch({ minRating: e.target.value || null })}>
          <option value="">Any rating</option>
          <option value="4.5">4.5★ and up</option>
          <option value="4">4★ and up</option>
          <option value="3">3★ and up</option>
        </Select>
      </Field>
      <div className="col" style={{ gap: 12, marginTop: 4 }}>
        <Switch checked={verifiedOnly} onChange={(v) => patch({ verifiedOnly: v ? '1' : null })} label="Verified sellers only" />
        <Switch checked={inStock} onChange={(v) => patch({ inStock: v ? '1' : null })} label="In stock only" />
        <Switch checked={flashOnly} onChange={(v) => patch({ flashOnly: v ? '1' : null })} label="Flash deals only" />
      </div>
      <Btn className="mt-8" onClick={() => setParams(q ? new URLSearchParams({ q }) : new URLSearchParams())}>
        Reset all filters
      </Btn>
    </div>
  );

  return (
    <div className="col-lg">
      {/* ── Search bar with AI / image / voice ──────────────────────────── */}
      <div className="search-hero anim-up">
        <div className="searchbar searchbar-lg searchbar--compact" style={{ position: 'relative' }}>
          <SearchIcon size={15} className="muted-2" style={{ flexShrink: 0 }} />
          <input
            value={input}
            onChange={(e) => { setInput(e.target.value); setShowSuggest(true); }}
            onFocus={() => setShowSuggest(true)}
            onBlur={() => setTimeout(() => setShowSuggest(false), 150)}
            onKeyDown={(e) => { if (e.key === 'Enter') runSearch(input); }}
            placeholder="Search products, brands, stores…"
            aria-label="Search"
          />
          {input && (
            <button className="btn btn-ghost btn-icon btn-sm" onClick={() => { setInput(''); runSearch(''); }} aria-label="Clear">
              <X size={13} />
            </button>
          )}
          <button className={`btn btn-icon btn-sm ${listening ? 'btn-danger' : ''}`} onClick={toggleVoice}
            title="Voice search" aria-label="Voice search">
            {listening ? <MicOff size={14} /> : <Mic size={14} />}
          </button>
          <button className="btn btn-icon btn-sm" onClick={() => setImageOpen(true)} title="Search by image" aria-label="Search by image">
            <ImageIcon size={14} />
          </button>
          <Btn variant="primary" size="sm" onClick={runAiSearch} loading={aiBusy} icon={<Sparkles size={12} />}>
            Ask AI
          </Btn>

          {showSuggest && suggestions.length > 0 && (
            <div className="search-panel">
              {suggestions.map((s, i) => (
                <button key={`${s.kind}-${s.label}-${i}`} className="search-row"
                  onMouseDown={(e) => { e.preventDefault(); setInput(s.label); runSearch(s.label); }}>
                  <SearchIcon size={14} className="muted-2" />
                  <span className="grow ellipsis">{s.label}</span>
                  <span className="tiny muted-2">{s.kind}</span>
                </button>
              ))}
            </div>
          )}
        </div>

        {listening && (
          <div className="row mt-8 t-danger tiny semi">
            <span className="pulse-dot" /> Listening… speak now
          </div>
        )}
      </div>

      {aiNote && (
        <div className="card ai-note anim-up">
          <div className="row" style={{ alignItems: 'flex-start', gap: 11 }}>
            <span className="ai-avatar"><Sparkles size={15} /></span>
            <div className="grow"><RichText text={aiNote} /></div>
            <button className="btn btn-ghost btn-icon" onClick={() => setAiNote('')} aria-label="Dismiss"><X size={15} /></button>
          </div>
        </div>
      )}

      {/* ── Toolbar ─────────────────────────────────────────────────────── */}
      <div className="row-between wrap">
        <div className="row wrap" style={{ gap: 8 }}>
          {activeFilters.map((f) => (
            <button key={f.label} className="chip active" onClick={f.clear}>
              {f.label} <X size={12} style={{ marginLeft: 3, verticalAlign: -1 }} />
            </button>
          ))}
        </div>
        <div className="row" style={{ gap: 8 }}>
          <Select aria-label="Sort results" value={sort} onChange={(e) => patch({ sort: e.target.value })} style={{ width: 'auto' }}>
            {SORTS.map((s) => <option key={s.id} value={s.id}>{s.label}</option>)}
          </Select>
          <Btn className="filters-btn" icon={<SlidersHorizontal size={15} />} onClick={() => setFiltersOpen(true)}>
            Filters
          </Btn>
        </div>
      </div>

      {/* ── Results + desktop filter rail ───────────────────────────────── */}
      <div className="search-layout">
        <aside className="card filter-rail">
          <h3 className="card-title mb-12"><SlidersHorizontal size={16} /> Refine</h3>
          {filterPanel}
        </aside>

        <div style={{ minWidth: 0 }}>
          {loading ? (
            <SkeletonGrid count={12} />
          ) : error ? (
            <ErrorBox message={error} onRetry={() => patch({})} />
          ) : products.length === 0 ? (
            <Empty
              icon={<SearchIcon size={28} />}
              title="No products matched"
              subtitle="Try fewer filters, a broader term, or let the AI interpret your request."
              action={<Btn variant="primary" icon={<Sparkles size={15} />} onClick={runAiSearch} loading={aiBusy}>Ask AI instead</Btn>}
            />
          ) : (
            <>
              <ProductGrid
                products={products}
                onAddToCart={(p) => void add(p)}
                onToggleFavorite={(p) => void toggleFavoriteSeller(p.seller.id, p.seller.name)}
                favoriteSellerIds={favoriteSellerIds}
              />
              <Pagination page={page} pageSize={PAGE_SIZE} total={total}
                onPage={(p) => { patch({ page: String(p) }, false); window.scrollTo({ top: 0, behavior: 'smooth' }); }} />
            </>
          )}
        </div>
      </div>

      {/* ── Mobile filter drawer ────────────────────────────────────────── */}
      <Modal open={filtersOpen} onClose={() => setFiltersOpen(false)} title="Filters"
        footer={<Btn variant="primary" onClick={() => setFiltersOpen(false)}>Show results</Btn>}>
        {filterPanel}
      </Modal>

      {/* ── Image search (upload a photo or paste a URL) ─────────────────── */}
      <Modal
        open={imageOpen}
        onClose={() => setImageOpen(false)}
        title="Search by image"
        footer={<Btn onClick={() => setImageOpen(false)}>Close</Btn>}
      >
        <VisualSearch compact showResults={false} onResults={onVisualResults} />
      </Modal>
    </div>
  );
}
