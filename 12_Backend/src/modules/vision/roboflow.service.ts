/**
 * ScottsTechX — Roboflow vision integration.
 *
 * One workflow endpoint powers every vision surface in the app (listing
 * moderation, photo search, AI listing generation) so a single model/decision
 * change in Roboflow propagates everywhere. The API key is read server-side
 * only — it is never shipped to the browser.
 *
 *   POST https://serverless.roboflow.com/{workspace}/workflows/{workflow}
 *   Authorization: Bearer $ROBOFLOW_API_KEY
 *   { "inputs": { "image": { "type": "url" | "base64", "value": "..." } } }
 *
 * The workflow is expected to return (at any nesting depth) a decision plus
 * optional metadata:
 *   decision             approved | manual_review | rejected | needs_better_image
 *   category             e.g. "Electronics"
 *   subcategory          e.g. "Headphones"
 *   product_title        e.g. "Sony WH-1000XM4"
 *   tags                 ["wireless", "noise cancelling"]
 *   rejection_reasons    ["low resolution", "watermark"]
 *   visual_search_embedding  number[] (cosine-compared for photo search)
 *
 * Everything degrades gracefully: without a key, or on timeout / non-2xx /
 * unparseable payload, callers get `null` and fall back to the existing
 * catalogue-driven behaviour. Vision infrastructure can never take a listing
 * down, only speed up or sharpen its path.
 */

interface RoboflowInput {
  imageUrl?: string;
  imageData?: string; // data URL or bare base64
}

export interface VisionAnalysis {
  decision: 'approved' | 'manual_review' | 'rejected' | 'needs_better_image';
  category?: string;
  subcategory?: string;
  productTitle?: string;
  tags: string[];
  rejectionReasons: string[];
  embedding: number[] | null;
  checkedAt: string;
}

const FALLBACK_WORKFLOW_URL =
  'https://serverless.roboflow.com/fredscottswork-gmail-com/workflows/active-learning';

/** True when the Roboflow key is configured (server-side only). */
export function roboflowConfigured(): boolean {
  return Boolean(process.env.ROBOFLOW_API_KEY?.trim());
}

function workflowUrl(): string {
  return process.env.ROBOFLOW_WORKFLOW_URL?.trim() || FALLBACK_WORKFLOW_URL;
}

function timeoutMs(): number {
  const raw = Number(process.env.ROBOFLOW_TIMEOUT_MS);
  if (!Number.isFinite(raw) || raw <= 0) return 10_000;
  return Math.min(Math.max(raw, 2_000), 30_000);
}

/** Normalise a key for comparison: "visualSearchEmbedding" == "visual_search_embedding". */
function normKey(k: string): string {
  return k.toLowerCase().replace(/[^a-z0-9]/g, '');
}

/** Recursively find the first value for any of the given keys. */
function dig(node: unknown, names: string[], depth = 0): unknown {
  if (!node || typeof node !== 'object' || depth > 6) return undefined;
  const wanted = names.map(normKey);
  for (const [k, v] of Object.entries(node as Record<string, unknown>)) {
    if (wanted.includes(normKey(k)) && v !== undefined && v !== null) return v;
  }
  for (const v of Object.values(node as Record<string, unknown>)) {
    const found = dig(v, names, depth + 1);
    if (found !== undefined) return found;
  }
  return undefined;
}

function asString(v: unknown): string | undefined {
  if (typeof v === 'string') {
    const s = v.trim();
    return s ? s : undefined;
  }
  if (typeof v === 'number' || typeof v === 'boolean') return String(v);
  // Workflow runners often wrap scalars: { value: "Electronics" }, { label: … }.
  if (v && typeof v === 'object' && !Array.isArray(v)) {
    const inner = dig(v, ['value', 'name', 'label', 'text', 'string', 'class_name', 'prediction', 'result'], 0);
    if (inner !== undefined && inner !== v) return asString(inner);
  }
  if (Array.isArray(v)) return asString(v[0]);
  return undefined;
}

function asStringArray(v: unknown): string[] {
  if (Array.isArray(v)) return v.map(asString).filter((x): x is string => !!x);
  if (typeof v === 'string') {
    // Some workflows return a comma/newline separated list.
    return v
      .split(/[,\n;]/)
      .map((s) => s.trim().replace(/^["']|["']$/g, ''))
      .filter(Boolean);
  }
  if (v && typeof v === 'object') {
    // Wrapped lists: { value: [...] }, { labels: [...] }, { items: [...] }.
    const inner = dig(v, ['value', 'labels', 'tags', 'items', 'list', 'classes', 'names'], 0);
    if (inner !== undefined && inner !== v) return asStringArray(inner);
  }
  return [];
}

function asEmbedding(v: unknown): number[] | null {
  let arr: unknown = v;
  // Some outputs wrap it: { embedding: [...] }, { vector: [...] },
  // { value: [...] }, { data: [...] } — or a full-image object.
  if (arr && typeof arr === 'object' && !Array.isArray(arr)) {
    const inner = dig(
      arr,
      ['embedding', 'vector', 'visual_search_embedding', 'values', 'value', 'data', 'list', 'array', 'items'],
      0
    );
    if (inner !== undefined) arr = inner;
  }
  if (!Array.isArray(arr)) return null;
  // Arrays of { name, value } pairs are a common model output too.
  const out = arr
    .map((n) => {
      if (typeof n === 'number') return n;
      if (typeof n === 'string') return Number(n);
      if (n && typeof n === 'object') {
        const val = dig(n as Record<string, unknown>, ['value', 'data', 'v', 'embedding'], 0);
        return typeof val === 'number' ? val : Number(val);
      }
      return NaN;
    })
    .filter((n) => Number.isFinite(n));
  return out.length >= 4 ? out : null;
}

function normalizeDecision(raw: unknown): VisionAnalysis['decision'] {
  const s = (asString(raw) || '').toLowerCase().replace(/[\s_-]+/g, '_');
  if (s.startsWith('approve') || s === 'publish' || s === 'live' || s === 'ok') return 'approved';
  if (s.startsWith('reject') || s === 'block' || s === 'blocked') return 'rejected';
  if (s.includes('better') || s.includes('retake') || s.includes('blurry') || s.includes('replace_photo')) {
    return 'needs_better_image';
  }
  // manual_review / review / unknown -> safe default
  return 'manual_review';
}

/**
 * Ask the Roboflow workflow to analyse an image. Returns null when vision is
 * not configured, the call fails, or the payload cannot be understood.
 */
export async function analyzeImage(input: RoboflowInput): Promise<VisionAnalysis | null> {
  const key = process.env.ROBOFLOW_API_KEY?.trim();
  if (!key) return null;

  let image: { type: 'url' | 'base64'; value: string };
  if (input.imageUrl) {
    image = { type: 'url', value: input.imageUrl };
  } else if (input.imageData) {
    const bare = input.imageData.replace(/^data:image\/[a-z0-9.+-]+;base64,/i, '');
    image = { type: 'base64', value: bare };
  } else {
    return null;
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs());
  try {
    const res = await fetch(workflowUrl(), {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${key}`,
      },
      body: JSON.stringify({ inputs: { image } }),
      signal: controller.signal,
    });
    if (!res.ok) {
      // Surfaces as a warning in Render logs — body is truncated and the key
      // is never logged. Callers still fall back gracefully.
      const detail = await res.text().catch(() => '');
      console.warn(`[vision] Roboflow workflow HTTP ${res.status}: ${detail.slice(0, 300)}`);
      return null;
    }
    const payload = await res.json();

    const decision = normalizeDecision(
      dig(payload, ['decision', 'verdict', 'status', 'result', 'moderation', 'moderation_decision'])
    );
    const embedding = asEmbedding(
      dig(payload, ['visual_search_embedding', 'embedding', 'vector', 'image_embedding', 'search_embedding'])
    );
    return {
      decision,
      category: asString(dig(payload, ['category', 'category_name', 'class', 'type'])),
      subcategory: asString(dig(payload, ['subcategory', 'sub_category', 'subcategory_name', 'sub_class'])),
      productTitle: asString(
        dig(payload, ['product_title', 'title', 'product_name', 'prediction', 'name'])
      ),
      tags: asStringArray(
        dig(payload, ['tags', 'labels', 'classes', 'predictions', 'class_names', 'object_classes', 'detections'])
      ),
      rejectionReasons: asStringArray(
        dig(payload, ['rejection_reasons', 'rejectionReasons', 'rejection_reason', 'reasons'])
      ),
      embedding,
      checkedAt: new Date().toISOString(),
    };
  } catch (err) {
    console.warn(
      `[vision] Roboflow workflow call failed (${err instanceof Error ? err.name : 'error'}) — ` +
        'falling back to catalogue search.'
    );
    return null;
  } finally {
    clearTimeout(timer);
  }
}

// ── Embedding scoring ───────────────────────────────────────────────────────

/** Cosine similarity of two equal-length vectors (0 when unusable). */
export function cosineSimilarity(a: number[], b: number[]): number {
  if (!a || !b || a.length === 0 || a.length !== b.length) return 0;
  let dot = 0;
  let na = 0;
  let nb = 0;
  for (let i = 0; i < a.length; i++) {
    dot += a[i] * b[i];
    na += a[i] * a[i];
    nb += b[i] * b[i];
  }
  if (!na || !nb) return 0;
  return dot / (Math.sqrt(na) * Math.sqrt(nb));
}

/**
 * Rank catalogue rows (each with an `id` and `visual_search_embedding`) by cosine
 * similarity to the query embedding. Returns ids best-first. Includes nothing
 * below `threshold` (the vector search is a *booster*, not a gate — the text
 * search still supplies results on its own).
 */
export function rankByEmbedding<T extends { id: string; visual_search_embedding: unknown }>(
  rows: T[],
  query: number[],
  threshold = 0.4
): string[] {
  return rows
    .map((r) => ({
      id: r.id,
      score: cosineSimilarity(query, asEmbedding(r.visual_search_embedding) || []),
    }))
    .filter((x) => x.score >= threshold)
    .sort((a, b) => b.score - a.score)
    .map((x) => x.id);
}

// ── Listing review (moderation routing) ────────────────────────────────────

export type ReviewOutcome =
  | { status: 'approved' | 'pending' | 'rejected'; analysis: VisionAnalysis }
  | { status: 'skipped' };

/** Resolve a stored media URL (possibly API-relative) to a public absolute URL. */
export function publicImageUrl(url: string, baseUrl?: string): string {
  if (!url) return '';
  if (/^https?:\/\//i.test(url)) return url;
  if (!baseUrl) return '';
  const base = baseUrl.replace(/\/+$/, '');
  return url.startsWith('/') ? `${base}${url}` : `${base}/${url}`;
}

/**
 * Run the Roboflow workflow over a listing photo and persist its verdict.
 *
 * Decision → product status:
 *   approved            -> 'approved'   (publish immediately)
 *   manual_review       -> 'pending'    (normal admin queue)
 *   rejected            -> 'rejected'   (blocked, reasons on the listing)
 *   needs_better_image  -> 'rejected'   (seller asked for a clearer photo)
 *
 * Returns `skipped` when no key is configured, the analyse call fails, or the
 * listing has no usable photo — in those cases nothing changes and the normal
 * admin review flow owns the listing.
 */
export async function reviewListing(
  db: import('pg').Pool,
  productId: string,
  imageUrl: string,
  baseUrl?: string
): Promise<ReviewOutcome> {
  const url = publicImageUrl(imageUrl, baseUrl);
  if (!url) return { status: 'skipped' };
  const analysis = await analyzeImage({ imageUrl: url });
  if (!analysis) return { status: 'skipped' };

  // Decision → product status: approved publishes, manual_review queues for
  // the admin, rejected and needs_better_image are blocked (the latter with a
  // clear "upload a sharper photo" reason).
  const status: 'approved' | 'pending' | 'rejected' =
    analysis.decision === 'approved'
      ? 'approved'
      : analysis.decision === 'manual_review'
        ? 'pending'
        : 'rejected';

  const rejectionReason =
    analysis.decision === 'rejected'
      ? analysis.rejectionReasons.length
        ? analysis.rejectionReasons.join('; ')
        : 'This listing was blocked by the automated photo review.'
      : analysis.decision === 'needs_better_image'
        ? 'The photo is unclear — upload a sharper picture of the product so buyers can see it.'
        : '';

  await db.query(
    `UPDATE products
        SET vision_decision = $2,
            vision_rejection_reasons = $3,
            vision_category = $4,
            vision_subcategory = $5,
            vision_title = $6,
            vision_tags = $7,
            visual_search_embedding = $8,
            vision_checked_at = $9,
            status = $10,
            rejection_reason = CASE WHEN $10 = 'rejected' THEN $11 ELSE rejection_reason END,
            submitted_at = CASE WHEN $10 IN ('approved','rejected') THEN now() ELSE submitted_at END,
            updated_at = now()
      WHERE id = $1`,
    [
      productId,
      analysis.decision,
      JSON.stringify(analysis.rejectionReasons),
      analysis.category ?? null,
      analysis.subcategory ?? null,
      analysis.productTitle ?? null,
      JSON.stringify(analysis.tags),
      analysis.embedding ? JSON.stringify(analysis.embedding) : null,
      analysis.checkedAt,
      status,
      rejectionReason,
    ]
  );

  return { status, analysis };
}

