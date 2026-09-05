/**
 * ScottsTechX — AI assistant service.
 *
 * Two execution paths, one behaviour contract:
 *
 *   LLM path      — OpenRouter (or apifreellm) when a key is configured. The
 *                   model receives LIVE CATALOG CONTEXT retrieved from Postgres,
 *                   so answers are grounded in the real store.
 *   Offline path  — no key configured. The same retrieval runs and a
 *                   deterministic composer writes the answer from the real rows.
 *
 * Either way the caller gets `{ text, products, agent, provider }`, so the UI
 * renders real product cards regardless of whether an LLM is available.
 */
import type pg from 'pg';
import { ServiceUnavailableError } from '../../errors.js';
import {
  AGENTS,
  getAgent,
  routeAgent,
  agentSystemPrompt,
  buildContext,
  composeOfflineAnswer,
  composeGuideAnswer,
  type AgentId,
} from './agents.js';
import { parseIntent, retrieveProducts, productsToContext, fmtUgx, type RetrievedProduct } from './catalog-context.js';
import { analyzeImage, rankByEmbedding, roboflowConfigured } from '../vision/roboflow.service.js';

/** How long interactive image search waits on the Roboflow workflow (ms).
 *  Generous enough for a serverless cold start, short enough that the photo
 *  search still feels instant-ish; the answer always falls back gracefully. */
const VISION_SEARCH_DEADLINE_MS = 6000;
/** How long interactive image search waits on the NVIDIA/LLM describe call (ms). */
const VISION_DESCRIBE_DEADLINE_MS = 8000;
/**
 * The CHAT already waits on the LLM, so a photo ask can afford the time a
 * serverless Roboflow cold start and a cold kimi-k3 call really need (the
 * 10s interactive caps routinely dropped both on Render — the user's photo
 * was answered with "analysis timed out" even though chat worked).
 */
const VISION_CHAT_SEARCH_DEADLINE_MS = 25_000;
const VISION_CHAT_DESCRIBE_DEADLINE_MS = 40_000;

/** NVIDIA NIM (OpenAI-compatible) endpoints. */
const NVIDIA_BASE_URL = 'https://integrate.api.nvidia.com/v1/chat/completions';
/** Chat/agent model — strong general LLM (text only). */
const NVIDIA_CHAT_MODEL = 'nvidia/nemotron-3-ultra-550b-a55b';
/**
 * Vision caption model — MUST accept image_url (nemotron-3-ultra is
 * text-only). Default is meta/llama-3.2-11b-vision-instruct: the live
 * diagnostics showed moonshotai/kimi-k3 HANGS on image input for this key
 * (25s abort, no HTTP error), so kimi-k3 is only a fallback candidate now.
 */
const NVIDIA_VISION_MODEL = 'meta/llama-3.2-11b-vision-instruct';
/** How long ONE vision-model attempt may take before the next candidate gets
 *  the image (a hanging model must not burn the whole chat deadline). */
const VISION_ATTEMPT_TIMEOUT_MS = 12_000;

/** True when an NVIDIA NIM key is configured (server-side only). */
export function nvidiaVisionConfigured(): boolean {
  return Boolean(process.env.NVIDIA_API_KEY?.trim());
}
import { PRODUCT_SELECT, rowsToProducts } from '../products/products.service.js';

/**
 * The chat-completions endpoint.
 *
 * OpenRouter by default, but every major provider (OpenAI, Groq, Together,
 * DeepInfra, a self-hosted vLLM or Ollama) speaks the same
 * /chat/completions shape, so pointing LLM_BASE_URL at one of them is enough
 * to switch — no code change. This was hardcoded, which meant the only way to
 * use a different provider was to edit the source.
 *
 * Set the full endpoint, e.g.
 *   LLM_BASE_URL=https://api.openai.com/v1/chat/completions
 *   LLM_BASE_URL=https://api.groq.com/openai/v1/chat/completions
 */
function openRouterUrl(): string {
  return process.env.LLM_BASE_URL || 'https://openrouter.ai/api/v1/chat/completions';
}

/**
 * How long to wait for the model before giving up and answering from the
 * catalogue instead.
 *
 * 25s suits a fast chat model, but reasoning models (GLM, DeepSeek-R1, QwQ)
 * think before they answer and can legitimately take longer — with a fixed
 * 25s cap they'd get cut off mid-thought and every reply would silently come
 * from the offline composer. Clamped to 5–120s so a typo can't hang a request
 * or disable the timeout entirely.
 */
function llmTimeoutMs(): number {
  const raw = Number(process.env.LLM_TIMEOUT_MS);
  if (!Number.isFinite(raw) || raw <= 0) return 25_000;
  return Math.min(Math.max(raw, 5_000), 120_000);
}
const APIFREELLM_URL = 'https://apifreellm.com/api/v1/chat';

export { AGENTS };

/** True when ANY AI provider has a key configured (chat + vision). */
export function aiConfigured(): boolean {
  return Boolean(
    process.env.LLM_API_KEY || process.env.APIFREELLM_API_KEY || process.env.NVIDIA_API_KEY
  );
}

/** Which OpenAI-compatible endpoint backs the LLM chat path. */
function resolveLlmEndpoint(): {
  url: string;
  key: string;
  model: string;
  provider: string;
  headers: Record<string, string>;
} {
  const nvidiaKey = process.env.NVIDIA_API_KEY?.trim();
  const llmKey = process.env.LLM_API_KEY?.trim();
  const freeKey = process.env.APIFREELLM_API_KEY?.trim();
  const explicit = (process.env.AI_PROVIDER || '').toLowerCase();

  // NVIDIA NIM: used when explicitly selected, or as the fallback provider
  // when it is the only key configured (the common "one key does everything"
  // deployment — vision captions and chat share it).
  if (nvidiaKey && (explicit === 'nvidia' || (!llmKey && !freeKey))) {
    return {
      url: process.env.NVIDIA_BASE_URL?.trim() || NVIDIA_BASE_URL,
      key: nvidiaKey,
      model: nvidiaModel(),
      provider: 'nvidia',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${nvidiaKey}`,
      },
    };
  }

  if (llmKey) {
    return {
      url: process.env.LLM_BASE_URL || 'https://openrouter.ai/api/v1/chat/completions',
      key: llmKey,
      model: process.env.AI_MODEL || 'meta-llama/llama-3.3-70b-instruct',
      provider: explicit === 'apifreellm' ? 'apifreellm' : 'openrouter',
      headers: {
        Authorization: `Bearer ${llmKey}`,
        'Content-Type': 'application/json',
        'HTTP-Referer': 'https://scottstechx.app',
        'X-Title': 'ScottsTechX',
      },
    };
  }

  throw new ServiceUnavailableError('No LLM key configured (NVIDIA_API_KEY or LLM_API_KEY)');
}

/** Vision describer readiness: NVIDIA NIM, OpenRouter vision, or Roboflow. */
export function visionCaptionConfigured(): boolean {
  return nvidiaVisionConfigured() || Boolean(process.env.LLM_API_KEY);
}

/**
 * Live diagnostic probe of the NVIDIA endpoint: performs a real (tiny) chat
 * request and reports the outcome. Used by GET /ai/diagnostics so a broken
 * model name / key / credit issue is visible without digging through logs.
 * Never returns the key.
 */
export interface NvidiaProbeResult {
  configured: boolean;
  url: string;
  model: string;
  ok: boolean;
  status?: number;
  error?: string;
  latencyMs?: number;
  models?: {
    ok: boolean;
    status?: number;
    error?: string;
    ids?: string[] | null;
    modelFound?: boolean;
    latencyMs?: number;
  };
  completion?: {
    streamOk: boolean;
    status?: number;
    error?: string;
    firstByteMs?: number;
    latencyMs?: number;
  };
  /** Does the configured VISION model actually accept an image and answer?
   *  The chat probe never exercises image_url — this one does, with a real
   *  (tiny) image, so "captions silently return nothing" becomes visible. */
  vision?: { ok: boolean; status?: number; error?: string; latencyMs?: number };
}

/** 1×1 PNG used to test that the vision model accepts image_url input. */
const NVIDIA_VISION_PROBE_IMAGE =
  'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=';

/** Probe the configured vision caption model with a real image payload. */
async function probeNvidiaVision(): Promise<{ ok: boolean; status?: number; error?: string; latencyMs?: number } | undefined> {
  if (!process.env.NVIDIA_API_KEY?.trim()) return undefined;
  const url = process.env.NVIDIA_BASE_URL?.trim() || NVIDIA_BASE_URL;
  const started = Date.now();
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 25_000);
  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${process.env.NVIDIA_API_KEY}` },
      body: JSON.stringify({
        model: nvidiaVisionModel(),
        max_tokens: 16,
        temperature: 0,
        messages: [
          {
            role: 'user',
            content: [
              { type: 'text', text: 'Name the main object in this image in 2-4 words.' },
              { type: 'image_url', image_url: { url: NVIDIA_VISION_PROBE_IMAGE } },
            ],
          },
        ],
      }),
      signal: controller.signal,
    });
    if (!res.ok) {
      const body = await res.text().catch(() => '');
      return { ok: false, status: res.status, error: body.slice(0, 300), latencyMs: Date.now() - started };
    }
    const data = await res.json();
    const text = extractReply(data?.choices?.[0]?.message);
    return {
      ok: Boolean(text),
      error: text ? undefined : 'vision model returned an empty response',
      latencyMs: Date.now() - started,
    };
  } catch (err) {
    return { ok: false, error: err instanceof Error ? err.message : String(err), latencyMs: Date.now() - started };
  } finally {
    clearTimeout(timer);
  }
}

export async function probeNvidia(): Promise<NvidiaProbeResult> {
  const url = process.env.NVIDIA_BASE_URL?.trim() || NVIDIA_BASE_URL;
  const model = nvidiaModel();
  if (!process.env.NVIDIA_API_KEY?.trim()) {
    return { configured: false, url, model, ok: false, error: 'NVIDIA_API_KEY not set' };
  }

  // 1. Key + reachability + model availability via GET /v1/models (fast, and
  //    a 401/403 is decisive; a network error is decisive too).
  const models = await listNvidiaModels();
  // ids is trimmed in the response to keep it readable, so also report the
  // configured model's presence explicitly (the first 40 are alphabetical and
  // can end before the "nvidia/…" block, which made it LOOK missing).
  const modelFound = models.ids?.includes(model) ?? false;
  const base: NvidiaProbeResult = {
    configured: true,
    url,
    model,
    ok: false,
    models: {
      ok: models.ok,
      status: models.status,
      error: models.error,
      ids: models.ids?.slice(0, 40),
      modelFound,
      latencyMs: models.latencyMs,
    },
  };
  if (!models.ok) {
    base.ok = false;
    base.status = models.status;
    base.error = `models endpoint: ${models.error ?? `HTTP ${models.status}`}`;
    return base;
  }

  // 2. Does the configured model exist? If not, say so BEFORE wasting a slot.
  if (!modelFound) {
    base.ok = false;
    base.error = `model "${model}" not in /v1/models for this key`;
    return base;
  }

  // 3. Streaming probe: measures time-to-first-byte. If headers+tokens arrive,
  //    the setup is fully working and any later slowness is model latency.
  const started = Date.now();
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 45_000);
  let res: Response | undefined;
  try {
    res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${process.env.NVIDIA_API_KEY}` },
      body: JSON.stringify({
        model,
        stream: true,
        max_tokens: 8,
        temperature: 0,
        messages: [{ role: 'user', content: 'Reply with the single word OK.' }],
      }),
      signal: controller.signal,
    });
    if (!res.ok) {
      const body = await res.text().catch(() => '');
      base.status = res.status;
      base.error = `completion HTTP ${res.status}: ${body.slice(0, 300)}`;
      base.completion = { streamOk: false, status: res.status, error: body.slice(0, 300) };
      return base;
    }
    const reader = res.body?.getReader();
    const decoder = new TextDecoder();
    let firstByteMs = 0;
    let firstChunk = '';
    const firstByte = new Promise<boolean>((resolve) => {
      const read = async () => {
        if (!reader) return resolve(false);
        const { done, value } = await reader.read();
        if (done) return resolve(false);
        firstByteMs = Date.now() - started;
        firstChunk = decoder.decode(value, { stream: true }).slice(0, 200);
        resolve(true);
      };
      void read();
    });
    const gotByte = await Promise.race([
      firstByte,
      new Promise<boolean>((resolve) => setTimeout(() => resolve(false), 40_000)),
    ]);
    base.ok = gotByte;
    base.completion = {
      streamOk: gotByte,
      firstByteMs: gotByte ? firstByteMs : undefined,
      latencyMs: Date.now() - started,
    };
    base.error = gotByte ? undefined : `no first token within 40s (chunk: "${firstChunk.slice(0, 80)}")`;
    if (gotByte) base.latencyMs = firstByteMs;
    // The chat probe never exercises image_url — that is exactly the silent
    // failure the user hit ("detected" fell back to their own question).
    // Probe the vision model with a real image so a caption timeout or a
    // non-multimodal model is visible in diagnostics.
    base.vision = await probeNvidiaVision();
    return base;
  } catch (err) {
    const e = err as { name?: string; message?: string };
    base.error = `${e.name ?? 'error'}: ${e.message ?? String(err)}`;
    base.completion = { streamOk: false, error: base.error, latencyMs: Date.now() - started };
    return base;
  } finally {
    clearTimeout(timer);
    // drain remaining stream in the background so socket isn't leaked
    void (async () => {
      try {
        const r = res?.body?.getReader();
        while (r) {
          const { done } = await r.read();
          if (done) break;
        }
      } catch { /* ignore */ }
    })();
  }
}

/** Provider/model the UI should display for the configured chat engine. */
export function llmStatusSummary(): { provider: string; model: string } {
  if (!aiConfigured()) return { provider: 'scottstechx-local', model: 'catalog-grounded' };
  try {
    const ep = resolveLlmEndpoint();
    return { provider: ep.provider, model: ep.model };
  } catch {
    return { provider: 'scottstechx-local', model: 'catalog-grounded' };
  }
}

function activeProvider(): string {
  return (process.env.AI_PROVIDER || 'openrouter').toLowerCase();
}

/** One SSE event emitted while a chat answer streams. */
export interface AskStreamEvent {
  type: 'stage' | 'reasoning' | 'delta' | 'answer' | 'error';
  /** reasoning_content / content delta. */
  text?: string;
  /** Final full answer (replaces accumulated deltas; carries products/meta). */
  answer?: AskResult;
  message?: string;
}

export interface AskOptions {
  db: pg.Pool;
  prompt: string;
  screen?: string;
  agent?: string;
  role?: 'buyer' | 'seller' | 'admin';
  userId?: string;
  history?: Array<{ role: 'user' | 'assistant'; content: string }>;
  /** Attached photo (base64 data URL) — analyzed server-side; text-only chat
   *  models never receive the bytes, only the analysis. */
  imageData?: string;
  /** Attached photo as a public URL. */
  imageUrl?: string;
  /** When set, the NVIDIA chat path streams reasoning/content deltas through
   *  this callback (the SSE transport). Other providers emit one delta. The
   *  final AskResult is still returned normally. */
  onStream?: (event: AskStreamEvent) => void;
}

export interface AskResult {
  text: string;
  provider: string;
  model: string;
  screen: string;
  agent: { id: AgentId; name: string; tagline: string };
  products: unknown[];
  grounded: boolean;
  /** Set when the provider call failed and we fell back — shows the real
   *  reason (e.g. "nvidia error 401: …") instead of a mysterious label. */
  llmError?: string;
  /** Present when a photo was attached: what the vision pipeline saw and how
   *  many live listings it matched. `error` explains when nothing was
   *  detected (provider timeout/failure) instead of hiding it. */
  photoAnalysis?: { detected: string; matchCount: number; error?: string };
}

/**
 * The single entry point every AI surface calls (buyer chat, seller copilot,
 * AI search bar, support AI mode).
 */
export async function ask(opts: AskOptions): Promise<AskResult> {
  const { db, prompt, screen = 'generic', role = 'buyer', history = [], imageData, imageUrl } = opts;

  const emit = opts.onStream ?? (() => {});
  const agent = getAgent(opts.agent ?? routeAgent(prompt, role));

  // Dashboard guide: role-scoped how-to, never a catalogue search and never
  // product cards. The LLM (when configured) gets only the tour for THIS role.
  if (agent.id === 'guide') {
    const meta = { screen, agent: { id: agent.id, name: agent.name, tagline: agent.tagline }, products: [], grounded: true };
    const offline = composeGuideAnswer(role, prompt);
    if (!aiConfigured()) return { text: offline, provider: 'scottstechx-local', model: 'dashboard-guide', ...meta };
    try {
      emit({ type: 'stage', text: 'Reading your dashboard…' });
      const llm = await askOpenRouter(agentSystemPrompt(agent, role), prompt, history.slice(-8), emit);
      return { text: llm.text || offline, provider: llm.provider, model: llm.model, ...meta };
    } catch {
      return { text: offline, provider: 'scottstechx-local', model: 'dashboard-guide', ...meta };
    }
  }
  emit({ type: 'stage', text: 'Searching the catalogue…' });
  const ctx = await buildContext(db, agent, prompt);

  // ── Attached photo: analyze it with the real vision pipeline (Roboflow
  //    labels + embedding, NVIDIA/LLM caption) and ground the answer in the
  //    LIVE matches. The chat model itself may be text-only (nemotron-3) —
  //    it never sees the bytes, it reads the analysis + matched listings as
  //    text, so it can genuinely answer "what is this / can I buy it / is it
  //    a fair price" without ever claiming it cannot see photos.
  let photoAnalysis: { detected: string; matchCount: number; error?: string } | undefined;
  let photoMatches: RetrievedProduct[] = [];
  let photoContext = '';
  if (imageData || imageUrl) {
    emit({ type: 'stage', text: 'Analyzing your photo…' });
    try {
      // Chat can wait longer than the interactive search modal: a serverless
      // Roboflow cold start and a cold kimi-k3 call routinely exceed the 6s/8s
      // interactive caps — the user's photo ask timed out on BOTH while chat
      // itself worked.
      const found = await imageSearch(
        db,
        {
          imageData,
          imageUrl,
          hint: prompt,
          searchDeadlineMs: VISION_CHAT_SEARCH_DEADLINE_MS,
          describeDeadlineMs: VISION_CHAT_DESCRIBE_DEADLINE_MS,
        },
        12
      );
      const rawDetected = found.detected?.trim() || '';
      // The search terms always include the user's own prompt — when no
      // caption/labels came back, "detected" is empty now; never present the
      // question (or a fragment of it) as the item in the photo.
      const promptFragment = prompt.trim().slice(0, 32).toLowerCase();
      const detectedOk =
        Boolean(rawDetected) &&
        !/^(what|which|how|is|are|do|does|can|could|find|show|compare|tell|why|where|who|when)\b/i.test(
          rawDetected
        ) &&
        (prompt.trim().length < 8 || !rawDetected.toLowerCase().includes(promptFragment));
      photoMatches = detectedOk ? ((found.products ?? []) as RetrievedProduct[]) : [];
      photoAnalysis = {
        detected: detectedOk ? rawDetected : 'could not identify this photo',
        matchCount: photoMatches.length,
        ...(detectedOk ? {} : { error: found.visionError ?? 'no detection came back from the vision providers' }),
      };
      const blocks: string[] = [];
      if (detectedOk) {
        blocks.push(`Detected: ${rawDetected}`);
        if (found.query) blocks.push(`Search terms used: ${found.query}`);
        if (found.visionError) blocks.push(`Vision notes (other provider): ${found.visionError}`);
        blocks.push(
          `LIVE catalogue matches (ranked, with price/stock/seller):\n${productsToContext(photoMatches)}`
        );
      } else {
        blocks.push(
          `The platform could not identify the item in the photo (${photoAnalysis.error}). ` +
            'Do not invent what the photo shows; say so plainly and suggest trying another angle or describing it in words.'
        );
      }
      photoContext = blocks.join('\n');
    } catch (err) {
      console.warn(`[ai] photo analysis failed (${err instanceof Error ? err.message : 'error'})`);
      photoAnalysis = {
        detected: 'could not identify this photo',
        matchCount: 0,
        error: err instanceof Error ? err.message.slice(0, 200) : 'photo analysis failed',
      };
    }
  }

  const meta = {
    screen,
    agent: { id: agent.id, name: agent.name, tagline: agent.tagline },
    // When the strict search misses we still talk about the relaxed matches in
    // the answer text, so ship those same products as cards — otherwise the
    // reply names items the shopper has no way to tap through to. A photo ask
    // shows the photo's matches first (they ARE the answer).
    products: photoMatches.length
      ? photoMatches
      : ctx.products.length
        ? ctx.products
        : ctx.fallbackProducts,
    grounded: true,
  };

  if (!aiConfigured()) {
    return {
      text: photoAnalysis
        ? composePhotoAnswer(photoAnalysis, photoMatches)
        : composeOfflineAnswer(agent, prompt, ctx),
      provider: 'scottstechx-local',
      model: 'catalog-grounded',
      photoAnalysis,
      ...meta,
    };
  }

  const system = agentSystemPrompt(agent, role);
  const grounded =
    `LIVE CATALOG CONTEXT (retrieved from the database just now):\n${ctx.contextText}\n\n` +
    (photoContext
      ? `PHOTO ANALYSIS (the user attached this photo — this is what the photo shows, treat it as ground truth, never say you cannot see it):\n${photoContext}\n\n`
      : '') +
    `USER QUESTION: ${prompt}`;

  emit({ type: 'stage', text: 'Thinking…' });
  try {
    const llm =
      activeProvider() === 'apifreellm'
        ? await askApiFreeLlm(system, grounded, history)
        : await askOpenRouter(system, grounded, history, opts.onStream);
    return { text: llm.text, provider: llm.provider, model: llm.model, photoAnalysis, ...meta };
  } catch (err) {
    // A provider outage must never take the assistant down — fall back to the
    // grounded local composer and label it honestly, carrying the real error
    // so the UI can explain it (and so a bad model name is visible, not
    // swallowed). With a photo, the local answer is built from the real
    // analysis + matches instead of a generic catalogue blast.
    const reason = err instanceof Error ? err.message : String(err);
    console.warn(`[ai] LLM unavailable, falling back to composer: ${reason.slice(0, 300)}`);
    emit({ type: 'error', message: reason.slice(0, 200) });
    return {
      text: photoAnalysis
        ? composePhotoAnswer(photoAnalysis, photoMatches)
        : composeOfflineAnswer(agent, prompt, ctx),
      provider: 'scottstechx-local (llm unavailable)',
      model: 'catalog-grounded',
      llmError: reason.slice(0, 200),
      photoAnalysis,
      ...meta,
    };
  }
}

/** Grounded answer built purely from the photo analysis + live matches. */
function composePhotoAnswer(
  analysis: { detected: string; matchCount: number; error?: string },
  matches: RetrievedProduct[]
): string {
  const identified = analysis.detected && !/^could not identify/.test(analysis.detected);
  const lines = identified
    ? [`From your photo I can see: **${analysis.detected}**.`]
    : [
        `I could not identify the item in your photo${analysis.error ? ` (${analysis.error})` : ''}.`,
        'Try a clearer, straight-on photo, or just describe it — for example "red sneakers" or "55-inch smart TV".',
      ];
  if (matches.length) {
    lines.push('', 'Here is what the live catalogue has for it:');
    for (const p of matches.slice(0, 6)) {
      lines.push(
        `• **${p.title}** — ${fmtUgx(p.priceMinor)} | ${
          p.stockQuantity > 0 ? `${p.stockQuantity} in stock` : '**out of stock**'
        } | seller ${p.sellerName}${p.verified ? ' ✓' : ''}${p.city ? `, ${p.city}` : ''}${
          p.isFlashDeal ? ` · 🔥 ${p.discountPercent}% off` : ''
        }`
      );
    }
    lines.push('', 'Ask me about any of these — price, stock, seller or how to order.');
  } else {
    lines.push(
      '',
      'I could not match that photo to anything currently listed. Describe it in a few words or try another angle, and I will search again.'
    );
  }
  return lines.join('\n');
}

/**
 * Pull the user-facing answer out of an OpenAI-style message.
 *
 * Reasoning models (GLM, DeepSeek-R1, QwQ, o1-style endpoints) don't behave
 * like plain chat models: they either put their thinking in a separate
 * `reasoning_content` field and the answer in `content`, or they inline the
 * thinking in `content` wrapped in <think> tags. Two things go wrong if we
 * just read `content`:
 *
 *   1. The private chain-of-thought gets shown to the shopper.
 *   2. When a model puts everything in `reasoning_content` and leaves
 *      `content` empty, we treat a perfectly good answer as a failure and
 *      fall back offline.
 *
 * So: strip the thinking, and only use `reasoning_content` as a last resort
 * when there's no real content — a partial answer beats no answer.
 */
export function extractReply(message: unknown): string {
  const msg = (message ?? {}) as Record<string, unknown>;

  // `content` is usually a string but the spec allows an array of parts.
  const raw = Array.isArray(msg.content)
    ? msg.content
        .map((part) =>
          typeof part === 'string' ? part : String((part as Record<string, unknown>)?.text ?? '')
        )
        .join('')
    : String(msg.content ?? '');

  const cleaned = stripThinking(raw);
  if (cleaned) return cleaned;

  // Nothing usable in `content` — salvage the reasoning field rather than
  // throwing away a response the provider already charged us for.
  return stripThinking(String(msg.reasoning_content ?? msg.reasoning ?? ''));
}

/** Remove <think>/<reasoning> blocks, including an unclosed trailing one. */
function stripThinking(input: string): string {
  return input
    .replace(/<(think|thinking|reasoning)>[\s\S]*?<\/\1>/gi, '')
    // A truncated response can open a think block and never close it; drop
    // everything after it so we never surface half a thought.
    .replace(/<(think|thinking|reasoning)>[\s\S]*$/i, '')
    .trim();
}

/**
 * Fallback models, ordered by preference. IMPORTANT: names are only used when
 * they appear in THIS key's actual GET /v1/models response — the catalog moves
 * (e.g. this key lists "moonshotai/kimi-k2.6", not "kimi-k2-instruct"), and a
 * fallback that 404s is no fallback. nvidiaFallbackModels() also appends any
 * other instruct/chat-style listed model as a last resort.
 */
const NVIDIA_FALLBACK_PREFERENCE = [
  'moonshotai/kimi-k2.6',
  'moonshotai/kimi-k3',
  'meta/llama-3.2-11b-vision-instruct',
  'deepseek-ai/deepseek-v4-pro-0813',
  'mistralai/mistral-large-2-instruct',
  'minimaxai/minimax-m3',
  'google/gemma-3-12b-it',
  'google/gemma-3-4b-it',
];

/**
 * Vision fallbacks: only models that accept image_url. kimi-k3 is LAST — on
 * the production key it hangs on image input (live diagnostics abort at 25s
 * with no HTTP error), so it must not be reached by default.
 */
const NVIDIA_VISION_FALLBACK_PREFERENCE = [
  'meta/llama-3.2-90b-vision-instruct',
  'microsoft/phi-3-vision-128k-instruct',
  'moonshotai/kimi-k3',
];

/**
 * Build the fallback chain from the models the key can actually use.
 * With `useAllOnUnknown` (the key's /v1/models listing itself was overloaded
 * or timed out) the preference list is used UNFILTERED: a 503 on the listing
 * says nothing about which completion models are usable, and collapsing the
 * chain to the primary is exactly how a transient overload becomes a hard
 * "llm unavailable" fallback. A preference entry that 404s during completion
 * just gets skipped by the per-model error handling.
 */
function nvidiaFallbackModels(
  ids: string[] | null,
  primary: string,
  preference: string[] = NVIDIA_FALLBACK_PREFERENCE,
  useAllOnUnknown = false
): string[] {
  const listed = new Set(ids ?? []);
  const preferred = preference.filter((m) => m !== primary && (useAllOnUnknown || listed.has(m)));
  if (preferred.length) return preferred;
  // Nothing from the preference list exists on this key — use any listed
  // instruct/chat-style model rather than failing offline.
  return (ids ?? [])
    .filter(
      (m) =>
        m !== primary &&
        /instruct|chat|kimi|nemotron|qwen|llama|gemma|mistral|deepseek|minimax/i.test(m) &&
        !/guard|code|deplot|kosmos|fuyu|diffusion/i.test(m)
    )
    .slice(0, 3);
}

/** Resolve which NVIDIA model to use (NVIDIA_MODEL wins, then NVIDIA_VISION_MODEL). */
/** Chat model (text): NVIDIA_MODEL wins, else the nemotron-3-ultra default. */
function nvidiaModel(): string {
  return process.env.NVIDIA_MODEL?.trim() || NVIDIA_CHAT_MODEL;
}

/** Vision caption model: NVIDIA_VISION_MODEL wins, else kimi-k3 (image-capable). */
function nvidiaVisionModel(): string {
  return process.env.NVIDIA_VISION_MODEL?.trim() || NVIDIA_VISION_MODEL;
}

/** Reasoning prefill matches the user's NIM snippet (extra_body equivalent). */
function nvidiaThinkingEnabled(): boolean {
  return (process.env.NVIDIA_ENABLE_THINKING ?? 'true').toLowerCase() !== 'false';
}

/**
 * One OpenAI-compatible chat completion against NVIDIA NIM, with a model
 * fallback chain: a 4xx that mentions the model name usually means "model not
 * found on this account", so try the next known-good model before giving up.
 * Auth/credit/network errors are NOT retried (a different model won't fix
 * those). Returns { text, model } or throws.
 */
/** Timeout for the model completion itself (generous: reasoning models and
 *  cold starts can take a while). Env override NVIDIA_TIMEOUT_MS. */
function nvidiaTimeoutMs(): number {
  const raw = Number(process.env.NVIDIA_TIMEOUT_MS);
  if (!Number.isFinite(raw) || raw <= 0) return 120_000;
  return Math.min(Math.max(raw, 10_000), 180_000);
}

/** Short timeout for the /v1/models health+key check (fail fast). */
const NVIDIA_MODELS_TIMEOUT_MS = 10_000;

/** How long a /v1/models listing result is cached (default 60s; tests use a
 *  short TTL so stub failures are observable without waiting). */
function nvidiaModelsCacheTtlMs(): number {
  const raw = Number(process.env.NVIDIA_MODELS_CACHE_TTL_MS);
  if (!Number.isFinite(raw) || raw <= 0) return 60_000;
  return Math.min(Math.max(raw, 250), 300_000);
}

let nvidiaModelsCache: { at: number; ids: string[] | null; ok: boolean; status?: number } | null = null;

/** Derive the /v1/models endpoint from the chat-completions URL. */
function nvidiaModelsUrl(): string {
  const base = process.env.NVIDIA_BASE_URL?.trim() || NVIDIA_BASE_URL;
  return base.replace(/\/chat\/completions\/?$/i, '') + '/models';
}

/**
 * List models available to this key via GET /v1/models. Result is the fastest
 * decisive check: 401 = bad key, 403 = no permission, network error = can't
 * reach NVIDIA at all, 200 = the exact model IDs we may use. Cached 60s.
 */
async function listNvidiaModels(): Promise<{
  ok: boolean;
  status?: number;
  error?: string;
  ids: string[] | null;
  latencyMs: number;
}> {
  const key = process.env.NVIDIA_API_KEY?.trim();
  if (!key) return { ok: false, error: 'NVIDIA_API_KEY not set', ids: null, latencyMs: 0 };
  if (nvidiaModelsCache && Date.now() - nvidiaModelsCache.at < nvidiaModelsCacheTtlMs()) {
    return {
      ok: nvidiaModelsCache.ok,
      status: nvidiaModelsCache.status,
      ids: nvidiaModelsCache.ids,
      latencyMs: 0,
    };
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), NVIDIA_MODELS_TIMEOUT_MS);
  const t0 = Date.now();
  try {
    const res = await fetch(nvidiaModelsUrl(), {
      method: 'GET',
      headers: { Authorization: `Bearer ${key}` },
      signal: controller.signal,
    });
    const text = await res.text().catch(() => '');
    let ids: string[] | null = null;
    if (res.ok) {
      try {
        ids = ((JSON.parse(text) as { data?: Array<{ id?: string }> }).data ?? [])
          .map((m) => m.id ?? '')
          .filter(Boolean);
      } catch {
        ids = null;
      }
    }
    // Only cache authoritative answers: a 200 listing, or a definitive auth
    // rejection. Transient 5xx overloads (and network blips) self-heal in
    // seconds — caching them would make chat + diagnostics report failure
    // for a full minute after recovery.
    if (res.ok || res.status === 401 || res.status === 403) {
      nvidiaModelsCache = { at: Date.now(), ids, ok: res.ok, status: res.status };
    }
    return {
      ok: res.ok,
      status: res.status,
      error: res.ok ? undefined : text.slice(0, 300),
      ids,
      latencyMs: Date.now() - t0,
    };
  } catch (err) {
    // No cache write: a fetch blip is transient by definition.
    return {
      ok: false,
      error: err instanceof Error ? err.message : String(err),
      ids: null,
      latencyMs: Date.now() - t0,
    };
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Read an OpenAI-compatible SSE stream and invoke onChunk for every parsed
 * JSON payload. Handles chunk splits, CRLF and the [DONE] sentinel; ignores
 * keep-alive comments. Returns the last `model` field seen (or '').
 */
async function readSseStream(
  res: Response,
  onChunk: (obj: Record<string, unknown>) => void
): Promise<void> {
  const reader = res.body?.getReader();
  if (!reader) return;
  const decoder = new TextDecoder();
  let buf = '';
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buf += decoder.decode(value, { stream: true });
    let nl = buf.indexOf('\n');
    while (nl >= 0) {
      const line = buf.slice(0, nl).replace(/\r$/, '');
      buf = buf.slice(nl + 1);
      nl = buf.indexOf('\n');
      if (!line.startsWith('data:')) continue;
      const payload = line.slice(5).trim();
      if (!payload || payload === '[DONE]') continue;
      try {
        onChunk(JSON.parse(payload) as Record<string, unknown>);
      } catch {
        /* partial/invalid frame — ignore */
      }
    }
  }
}

async function nvidiaChatCompletion(
  messages: Array<Record<string, unknown>>,
  maxTokens: number,
  opts: {
    model?: string;
    think?: boolean;
    attemptTimeoutMs?: number;
    fallback?: string[];
    /** When set, `stream: true` is sent and deltas are pushed to this sink
     *  (reasoning & content separately) as they arrive. The accumulated full
     *  text is still returned. */
    onStream?: (chunk: { reasoning?: string; content?: string; model?: string }) => void;
  } = {}
): Promise<{ text: string; model: string }> {
  const key = process.env.NVIDIA_API_KEY?.trim();
  if (!key) throw new ServiceUnavailableError('NVIDIA_API_KEY is not set');
  const url = process.env.NVIDIA_BASE_URL?.trim() || NVIDIA_BASE_URL;
  const primary = opts.model || nvidiaModel();
  const think = opts.think ?? nvidiaThinkingEnabled();

  // Fail fast on bad key / unreachable host instead of hanging 25s. A 200
  // (or a list-shaped failure that doesn't mention auth) also lets us pick a
  // model that actually exists on this account.
  const models = await listNvidiaModels();
  if (!models.ok) {
    const msg = `${models.error ?? `HTTP ${models.status}`}`;
    if (models.status === 401 || models.status === 403 || /unauthorized|forbidden|invalid.*key|api.?key/i.test(msg)) {
      throw new ServiceUnavailableError(`nvidia key rejected (${models.status}): ${msg.slice(0, 200)}`);
    }
    if (/fetch failed|abort|timed? ?out|ECONN|ENOTFOUND|socket/i.test(msg)) {
      throw new ServiceUnavailableError(`nvidia endpoint unreachable: ${msg.slice(0, 200)}`);
    }
  }
  // Chain = configured primary + models the key can actually use (in
  // preference order, then any listed instruct/chat-style model as a last
  // resort). When the /v1/models listing itself failed (503 overload, timeout)
  // the chain is NOT collapsed: use the full preference list so a fallback
  // model can still answer while the service is briefly overloaded.
  const listingDown = !models.ok && models.status !== 401 && models.status !== 403;
  const candidates = [
    ...new Set([
      primary,
      ...nvidiaFallbackModels(models.ok ? models.ids : null, primary, opts.fallback, listingDown),
    ]),
  ];

  // Transient failures (NVIDIA's 'Service temporarily overloaded' 503, rate
  // limits, 5xx) recover in seconds. Retry the same model twice with backoff,
  // then move down the chain — a talking answer from kimi-k2 beats an offline
  // fallback. Hard errors (auth/credits) and network failures abort
  // immediately: no model switch fixes those.
  const TRANSIENT_HTTP = new Set([408, 429, 500, 502, 503, 504]);
  const budgetMs = nvidiaTimeoutMs();
  const startedAt = Date.now();
  let lastError: unknown;
  let hardStop = false;

  for (const model of candidates) {
    if (hardStop) break;
    let attemptsLeft = 3; // 2 retries on this model before trying the next
    while (attemptsLeft > 0 && !hardStop) {
      attemptsLeft--;
      const remaining = budgetMs - (Date.now() - startedAt);
      if (remaining < 3000 && attemptsLeft > 0) {
        lastError ??= new ServiceUnavailableError('nvidia retry budget exhausted');
        hardStop = true;
        break;
      }
      const controller = new AbortController();
      // A vision attempt gets a SHORT cap so a model that hangs on image_url
      // yields to the next candidate instead of burning the whole deadline;
      // chat keeps the full remaining budget (reasoning is legitimately slow).
      const attemptMs = opts.attemptTimeoutMs ? Math.min(remaining, opts.attemptTimeoutMs) : remaining;
      const timer = setTimeout(() => controller.abort(), attemptMs);
      try {
        const res = await fetch(url, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${key}` },
          body: JSON.stringify({
            model,
            messages,
            max_tokens: maxTokens,
            temperature: 0.4,
            // Reasoning models (nemotron-3) expect the thinking toggle as
            // chat_template_kwargs — the raw-JSON equivalent of the Python SDK's
            // extra_body={"chat_template_kwargs":{"enable_thinking":true}}.
            ...(think ? { chat_template_kwargs: { enable_thinking: true } } : {}),
            ...(opts.onStream ? { stream: true } : {}),
          }),
          signal: controller.signal,
        });
        if (!res.ok) {
          const body = await res.text().catch(() => '');
          const err = new ServiceUnavailableError(
            `nvidia error ${res.status}: ${body.slice(0, 300)}`
          ) as ServiceUnavailableError & { nvidiaStatus?: number; nvidiaBody?: string };
          err.nvidiaStatus = res.status;
          err.nvidiaBody = body;
          console.warn(`[ai] nvidia ${model} -> HTTP ${res.status}: ${body.slice(0, 200)}`);
          throw err;
        }
        if (opts.onStream) {
          // Streaming: push reasoning/content deltas through the sink as they
          // arrive (time-to-first-token is what makes chat FEEL fast), then
          // return the accumulated answer so callers behave identically.
          let content = '';
          let reasoning = '';
          let streamedModel = model;
          await readSseStream(res, (obj) => {
            if (typeof obj.model === 'string' && obj.model) streamedModel = obj.model;
            const delta = (obj as { choices?: Array<{ delta?: Record<string, unknown> }> })
              .choices?.[0]?.delta;
            if (!delta) return;
            const r = typeof delta.reasoning_content === 'string' ? delta.reasoning_content : '';
            const c = typeof delta.content === 'string' ? delta.content : '';
            if (r) {
              reasoning += r;
              opts.onStream?.({ reasoning: r });
            }
            if (c) {
              content += c;
              opts.onStream?.({ content: c });
            }
          });
          const text = extractReply({ content, reasoning_content: reasoning });
          if (!text) throw new ServiceUnavailableError('nvidia returned an empty stream');
          return { text, model: streamedModel };
        }
        const data = await res.json();
        const text = extractReply(data?.choices?.[0]?.message);
        if (!text) throw new ServiceUnavailableError('nvidia returned an empty response');
        return { text, model };
      } catch (err) {
        lastError = err;
        const status = (err as { nvidiaStatus?: number }).nvidiaStatus ?? 0;
        const body = String((err as { nvidiaBody?: string }).nvidiaBody ?? '');
        const aborted =
          err instanceof Error && (err.name === 'AbortError' || /abort/i.test(err.message));
        const modelIssue = (status === 400 || status === 404 || status === 422) && /model/i.test(body);
        const hard = status === 401 || status === 402 || status === 403;
        const transient = TRANSIENT_HTTP.has(status) || status === 0; // 0 = network/timeout
        if (modelIssue) {
          console.warn(`[ai] nvidia model "${model}" rejected — trying next.`);
          break; // wrong model name — the next candidate may be listed
        }
        if (hard) {
          hardStop = true;
          break; // auth / credits / no permission — no model will fix it
        }
        if (aborted) {
          // Our attempt timer fired: the model is hanging or too slow (the
          // kimi-k3 vision hang). Retrying it wastes the same time again —
          // move straight to the next candidate. The overall budget still
          // bounds the total.
          console.warn(`[ai] nvidia "${model}" attempt timed out after ${attemptMs}ms — trying next.`);
          break;
        }
        if (transient && attemptsLeft > 0) {
          const backoff = Math.min(
            500 + 900 * (2 - attemptsLeft) + Math.round(Math.random() * 300),
            Math.max(remaining - 1000, 100)
          );
          console.warn(`[ai] nvidia ${model} -> transient ${status}, retrying in ${backoff}ms`);
          await new Promise((r) => setTimeout(r, backoff));
          continue; // retry the same model
        }
        if (status === 0) hardStop = true; // network/egress — next model uses the same network
        break; // retries exhausted on this model — try the next usable one
      } finally {
        clearTimeout(timer);
      }
    }
  }
  throw lastError instanceof Error ? lastError : new ServiceUnavailableError('nvidia call failed');
}

async function askOpenRouter(
  system: string,
  userContent: string,
  history: Array<{ role: 'user' | 'assistant'; content: string }>,
  onStream?: (ev: AskStreamEvent) => void
) {
  // Provider-agnostic: NVIDIA NIM (with model fallback) or any OpenAI-compatible
  // endpoint (OpenRouter, OpenAI, Groq…). Named askOpenRouter historically; the
  // returned provider name tells the UI which one answered.
  const ep = resolveLlmEndpoint();

  const messages = [
    { role: 'system', content: system },
    ...history.slice(-8).map((h) => ({ role: h.role, content: h.content })),
    { role: 'user', content: userContent },
  ];

  if (ep.provider === 'nvidia') {
    // nemotron-3-ultra is a thinking model — give it room (reasoning consumes
    // tokens; short replies get cut otherwise). The user's snippet uses 16384.
    const r = await nvidiaChatCompletion(messages, 8192, {
      model: nvidiaModel(),
      think: true,
      ...(onStream
        ? {
            onStream: (chunk) => {
              if (chunk.reasoning) onStream({ type: 'reasoning', text: chunk.reasoning });
              if (chunk.content) onStream({ type: 'delta', text: chunk.content });
            },
          }
        : {}),
    });
    return { text: r.text, provider: 'nvidia', model: r.model };
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), llmTimeoutMs());
  try {
    const res = await fetch(ep.url, {
      method: 'POST',
      headers: ep.headers,
      body: JSON.stringify({ model: ep.model, messages, max_tokens: 2048, temperature: 0.4, ...(onStream ? { stream: true } : {}) }),
      signal: controller.signal,
    });
    if (!res.ok) {
      const text = await res.text();
      throw new ServiceUnavailableError(`${ep.provider} error ${res.status}: ${text.slice(0, 200)}`);
    }
    if (onStream) {
      let content = '';
      let reasoning = '';
      await readSseStream(res, (obj) => {
        const delta = (obj as { choices?: Array<{ delta?: Record<string, unknown> }> })
          .choices?.[0]?.delta;
        if (!delta) return;
        const r = typeof delta.reasoning_content === 'string' ? delta.reasoning_content : '';
        const c = typeof delta.content === 'string' ? delta.content : '';
        if (r) {
          reasoning += r;
          onStream({ type: 'reasoning', text: r });
        }
        if (c) {
          content += c;
          onStream({ type: 'delta', text: c });
        }
      });
      const text = extractReply({ content, reasoning_content: reasoning });
      if (!text) throw new ServiceUnavailableError(`${ep.provider} returned an empty stream`);
      return { text, provider: ep.provider, model: ep.model };
    }
    const data = await res.json();
    const text = extractReply(data?.choices?.[0]?.message);
    if (!text) throw new ServiceUnavailableError(`${ep.provider} returned an empty response`);
    return { text, provider: ep.provider, model: ep.model };
  } finally {
    clearTimeout(timeout);
  }
}

async function askApiFreeLlm(
  system: string,
  userContent: string,
  history: Array<{ role: 'user' | 'assistant'; content: string }>
) {
  const key = process.env.APIFREELLM_API_KEY;
  if (!key) throw new ServiceUnavailableError('APIFREELLM_API_KEY is not set');

  const folded = [system, ...history.slice(-6).map((h) => `${h.role}: ${h.content}`), userContent].join(
    '\n\n'
  );

  const res = await fetch(APIFREELLM_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${key}` },
    body: JSON.stringify({ message: folded }),
  });
  const data: any = await res.json().catch(() => ({}));

  if (!res.ok) {
    const code = Number(data.code ?? res.status);
    const msg = String(data.error ?? res.statusText ?? 'unknown error');
    if (code === 403 && /datacenter|cloud IP/i.test(msg)) {
      throw new ServiceUnavailableError(
        'apifreellm free tier is not available from datacenter/cloud IPs — use AI_PROVIDER=openrouter with an LLM_API_KEY.'
      );
    }
    throw new ServiceUnavailableError(`apifreellm error ${res.status}: ${msg.slice(0, 200)}`);
  }

  const text = stripThinking(
    String(
      data.message ?? data.data?.message ?? data.choices?.[0]?.message?.content ?? data.text ?? ''
    )
  );
  if (!text) throw new ServiceUnavailableError('apifreellm returned an empty response');
  return { text, provider: 'apifreellm', model: String(data.model ?? 'apifreellm') };
}

// ── AI search (the search bar's brain) ──────────────────────────────────────

/**
 * Natural-language search: "cheap phone under 500k in Kampala" becomes real
 * filters plus a one-line explanation of what was understood.
 */
export async function aiSearch(db: pg.Pool, query: string, limit = 24) {
  const cats = await db.query(
    `SELECT DISTINCT category FROM products WHERE status = 'approved'`
  );
  const categories = cats.rows.map((r) => r.category as string);
  const intent = parseIntent(query, categories);
  const products = await retrieveProducts(db, intent, limit);

  const bits: string[] = [];
  if (intent.category) bits.push(`category **${intent.category}**`);
  if (intent.keywords.length) bits.push(`matching **${intent.keywords.join(', ')}**`);
  if (intent.maxPriceMinor) bits.push(`under **${fmtUgx(intent.maxPriceMinor)}**`);
  if (intent.minPriceMinor) bits.push(`over **${fmtUgx(intent.minPriceMinor)}**`);
  if (intent.city) bits.push(`in **${intent.city}**`);
  if (intent.wantsDeals) bits.push('on **flash deal**');
  if (intent.wantsCheapest) bits.push('sorted by **lowest price**');
  else if (intent.wantsBest) bits.push('sorted by **best rated**');

  const explanation = bits.length
    ? `Showing ${products.length} result${products.length === 1 ? '' : 's'} ${bits.join(', ')}.`
    : `Showing ${products.length} result${products.length === 1 ? '' : 's'} for "${query}".`;

  return {
    query,
    explanation,
    intent,
    products,
    filters: {
      category: intent.category ?? null,
      maxPriceMinor: intent.maxPriceMinor ?? null,
      minPriceMinor: intent.minPriceMinor ?? null,
      city: intent.city ?? null,
      flashOnly: intent.wantsDeals,
      sort: intent.wantsCheapest ? 'price_asc' : intent.wantsBest ? 'rating' : 'relevance',
    },
  };
}

// ── Image search ────────────────────────────────────────────────────────────

/**
 * Image search. Roboflow vision is first: the workflow labels the photo and,
 * when it returns a visual embedding, catalogue rows are ranked by cosine
 * similarity against it. The LLM description remains as the fallback when
 * Roboflow is unconfigured or fails, then hint → filename keywords.
 */
export async function imageSearch(
  db: pg.Pool,
  opts: {
    imageUrl?: string;
    imageData?: string;
    hint?: string;
    labels?: string[];
    /** Override the Roboflow deadline (interactive search wants 6s; the chat
     *  can afford 10s because it already waits on the LLM). */
    searchDeadlineMs?: number;
    /** Override the caption deadline (chat uses 25s — a cold kimi-k3 call
     *  easily exceeds the 8s interactive cap). */
    describeDeadlineMs?: number;
  },
  limit = 24
) {
  const raw = opts.imageData || opts.imageUrl || '';
  let terms = [opts.hint ?? '', ...(opts.labels ?? [])]
    .filter(Boolean)
    .join(' ')
    // The hint and a filename-derived label often share words ("nike air-max"
    // + "red Nike trainers") — collapse exact repeats before querying.
    .split(/\s+/)
    .filter((w, i, all) => w && all.indexOf(w) === i)
    .join(' ');

  // Vision in parallel, each under its own deadline so a hung provider never
  // holds the spinner (image search is interactive):
  //   Roboflow — moderation decision + catalogue-trained labels + embedding.
  //   NVIDIA / LLM — general vision captions (what IS in the photo), which is
  //                  what makes search names the item the Roboflow workflow
  //                  may only classify coarsely.
  const searchDeadline = opts.searchDeadlineMs ?? VISION_SEARCH_DEADLINE_MS;
  const describeDeadline = opts.describeDeadlineMs ?? VISION_DESCRIBE_DEADLINE_MS;
  let vision: Awaited<ReturnType<typeof analyzeImage>> = null;
  let caption: { text: string; error?: string } = { text: '' };
  let visionError = '';
  if (raw) {
    // The internal abort must match the outer deadline or the inner 10s cap
    // wins and a serverless cold start is killed before the chat's wait ends.
    // onError captures the REAL reason (e.g. Roboflow's 401 "key not authorized
    // for serverless inference") so "did not answer" is never the whole story.
    const robof = Promise.race([
      analyzeImage({
        imageUrl: opts.imageUrl,
        imageData: opts.imageData,
        timeoutMs: searchDeadline,
        onError: (d) => {
          visionError = visionError ? `${visionError}; ${d}` : d;
        },
      }),
      new Promise<null>((resolve) => setTimeout(() => resolve(null), searchDeadline)),
    ]);
    const captionCall = Promise.race([
      describeImage({ imageUrl: opts.imageUrl, imageData: opts.imageData }),
      new Promise<{ text: string; error?: string }>((resolve) =>
        setTimeout(() => resolve({ text: '', error: `caption timed out after ${describeDeadline}ms` }), describeDeadline)
      ),
    ]);
    [vision, caption] = await Promise.all([robof, captionCall]);
    if (!vision && roboflowConfigured()) {
      if (!visionError) {
        console.warn(`[vision] image search skipped Roboflow after its ${searchDeadline}ms deadline — answering from caption/heuristic.`);
        visionError = `Roboflow analysis did not answer within ${searchDeadline}ms`;
      } else {
        console.warn(`[vision] image search skipped Roboflow: ${visionError.slice(0, 160)}`);
      }
    }
    if (!caption.text) {
      console.warn(`[vision] image search without a caption (${caption.error ?? 'no vision provider configured'})`);
      if (caption.error) visionError = visionError ? `${visionError}; ${caption.error}` : caption.error;
      else if (nvidiaVisionConfigured()) visionError = 'NVIDIA caption did not answer';
    }
  }
  const described = caption.text;

  // Merge every signal; dedupe word-by-word. The caption is the strongest
  // "what is it" signal, so it leads.
  const labelTerms = vision
    ? [vision.productTitle, vision.category, vision.subcategory, ...vision.tags].filter(Boolean).join(' ')
    : '';
  const baseTerms = terms;
  const merged = `${described} ${terms} ${labelTerms}`
    .split(/\s+/)
    .filter((w, i, all) => w && all.indexOf(w) === i)
    .join(' ')
    .trim();
  if (merged) terms = merged;

  if (!terms && opts.imageUrl) {
    // Last resort: mine the filename/URL for keywords.
    terms = decodeURIComponent(opts.imageUrl)
      .split(/[/?#]/)
      .pop()!
      .replace(/[-_.]/g, ' ')
      .replace(/\.(jpg|jpeg|png|webp|gif)$/i, '');
  }

  // Recall ladder so image search is never a dead end: a caption can name the
  // photo so precisely the catalogue has no exact match ("AirSound Pro
  // Headphones") even though the hint/filename would have found plenty.
  let result = await aiSearch(db, terms || 'popular', limit);
  if (!result.products.length && baseTerms && baseTerms !== terms) {
    const retry = await aiSearch(db, baseTerms, limit);
    if (retry.products.length) result = retry;
  }
  if (!result.products.length && terms !== 'popular' && baseTerms !== 'popular') {
    result = await aiSearch(db, 'popular', limit);
  }

  // Visual boost: when the workflow gave an embedding, rank the catalogue by
  // cosine similarity and bubble the closest matches to the top (text-search
  // results stay in the list so recall never drops).
  let rankedIds: string[] = [];
  if (vision?.embedding) {
    const rows = await db.query(
      `SELECT id, visual_search_embedding
         FROM products
        WHERE status = 'approved' AND visual_search_embedding IS NOT NULL`
    );
    rankedIds = rankByEmbedding(rows.rows as any[], vision.embedding, 0.35);
    if (rankedIds.length) {
      const byId = new Map(result.products.map((p: any) => [p.id, p]));
      // Rows ranked by the embedding may not be in the text result — fetch any
      // missing ones so the visual ranking is complete, not just a reorder.
      const missing = rankedIds.filter((id) => !byId.has(id));
      if (missing.length) {
        const fetched = await db.query(
          `${PRODUCT_SELECT} WHERE p.id = ANY($1::uuid[]) AND p.status = 'approved'`,
          [missing]
        );
        for (const row of rowsToProducts(fetched.rows)) {
          if (!byId.has(row.id)) byId.set(row.id, row);
        }
      }
      result.products = rankedIds
        .map((id) => byId.get(id))
        .filter(Boolean)
        .concat(result.products.filter((p: any) => !rankedIds.includes(p.id)))
        .slice(0, limit);
    }
  }

  // What we *tell* the user we detected. Only VISION output may be a
  // detection: the NVIDIA caption (most human-readable) then Roboflow labels.
  // The search terms always include the hint/question, so they can never be
  // shown as "detected" — that is exactly how a photo ask ended up reporting
  // the user's own question as the item. `query` still carries every signal
  // (caption + labels + hint + filename) for the actual catalogue search.
  const visionLabel = vision
    ? [vision.productTitle, vision.category, ...vision.tags].filter(Boolean).join(' ')
    : '';
  const detectedLabel = (described || visionLabel).trim();
  return {
    ...result,
    detected: detectedLabel,
    query: terms,
    visionError: visionError || undefined,
    explanation: detectedLabel
      ? `Image looks like **${detectedLabel}** — ${result.products.length} similar item${
          result.products.length === 1 ? '' : 's'
        } found.`
      : 'Could not read the image — showing popular products instead.',
  };
}

/**
 * Describe a photo with a vision model — NVIDIA NIM (configurable, defaults
 * to the kimi-k3 vision class) first, then the legacy OpenRouter vision path
 * when only LLM_API_KEY is set. Accepts either a public URL or a base64 data
 * URL (file uploads). Never throws; returns { text, error } so callers can
 * say WHY nothing was detected instead of silently degrading.
 */
async function describeImage(input: {
  imageUrl?: string;
  imageData?: string;
}): Promise<{ text: string; error?: string }> {
  const imageUrl = input.imageData || input.imageUrl;
  if (!imageUrl) return { text: '' };

  // ── NVIDIA NIM (preferred: strong general vision captions) ───────────────
  if (process.env.NVIDIA_API_KEY?.trim()) {
    try {
      const r = await nvidiaChatCompletion(
        [
          {
            role: 'user',
            content: [
              {
                type: 'text',
                text: 'What is in this image? Name the product in 2-6 words for an e-commerce search (brand + item type only, no sentence).',
              },
              { type: 'image_url', image_url: { url: imageUrl } },
            ],
          },
        ],
        64,
        // Captions must use an IMAGE-capable model (nemotron-3-ultra is
        // text-only) and skip thinking so the caption is fast. Chain only
        // confirmed image models, with a short per-attempt cap so a model
        // that hangs on image_url (kimi-k3 on this key) yields to the next
        // instead of eating the whole chat deadline.
        {
          model: nvidiaVisionModel(),
          think: false,
          attemptTimeoutMs: VISION_ATTEMPT_TIMEOUT_MS,
          fallback: NVIDIA_VISION_FALLBACK_PREFERENCE,
        }
      );
      return { text: r.text.slice(0, 120) };
    } catch (err) {
      const reason = err instanceof Error ? err.message : String(err);
      console.warn(`[vision] NVIDIA describe failed (${reason.slice(0, 200)})`);
      return { text: '', error: reason.slice(0, 200) };
    }
  }

  // ── Legacy OpenRouter vision path (LLM_API_KEY / AI_VISION_MODEL) ─────────
  const key = process.env.LLM_API_KEY;
  if (!key) return { text: '' };
  const model = process.env.AI_VISION_MODEL || 'meta-llama/llama-3.2-11b-vision-instruct';

  const res = await fetch(openRouterUrl(), {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${key}`,
      'Content-Type': 'application/json',
      'HTTP-Referer': 'https://scottstechx.app',
      'X-Title': 'ScottsTechX',
    },
    body: JSON.stringify({
      model,
      max_tokens: 40,
      messages: [
        {
          role: 'user',
          content: [
            {
              type: 'text',
              text: 'Name this product in 2-5 words for an e-commerce search (brand + item type only, no sentence).',
            },
            { type: 'image_url', image_url: { url: imageUrl } },
          ],
        },
      ],
    }),
  });
  if (!res.ok) {
    const detail = await res.text().catch(() => '');
    return { text: '', error: `vision model HTTP ${res.status}: ${detail.slice(0, 200)}` };
  }
  const data = await res.json();
  // Vision models reason too — strip the thinking or it becomes the caption.
  return { text: extractReply(data?.choices?.[0]?.message).slice(0, 80) };
}

// ── Seller listing generation ───────────────────────────────────────────────

/**
 * Generate a listing from a photo/hint, priced against real comparable rows.
 * Uses the LLM when available and always returns a usable result.
 */
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
async function loadLocalUploadAsDataUrl(db: pg.Pool, url: string): Promise<string | null> {
  const m = url.match(/\/api\/v1\/uploads\/images\/([^/?#]+)/);
  if (!m) return null;
  const key = decodeURIComponent(m[1]);
  try {
    if (UUID_RE.test(key)) {
      const r = await db.query<{ data: Buffer; mime_type: string }>('SELECT data, mime_type FROM uploaded_images WHERE id = $1', [key]);
      if (r.rows[0]) return `data:${r.rows[0].mime_type};base64,${r.rows[0].data.toString('base64')}`;
    }
    const r = await db.query<{ data: Buffer; content_type: string }>('SELECT data, content_type FROM stored_images WHERE key = $1', [key]);
    if (r.rows[0]) return `data:${r.rows[0].content_type};base64,${r.rows[0].data.toString('base64')}`;
  } catch { /* table may not exist on old databases */ }
  return null;
}

export async function generateProduct(
  db: pg.Pool,
  opts: { imageUrl?: string; imageUrls?: string[]; hint?: string }
) {
  const hint = (opts.hint ?? '').trim();
  // Up to 4 photos: the front shot usually names the item, the others add
  // colour / condition / accessories. Each is analysed in parallel and the
  // findings are merged (labels first, captions second, deduplicated).
  const urls = Array.from(new Set([opts.imageUrl, ...(opts.imageUrls ?? [])].filter((u): u is string => Boolean(u && u.trim())))).slice(0, 4);
  let detected = hint;
  const perPhoto: string[] = [];
  if (urls.length) {
    const findings = await Promise.all(urls.map(async (url) => {
      // Photos stored in our own database are only reachable through this
      // API, so hand the vision providers the bytes instead of the URL.
      const local = await loadLocalUploadAsDataUrl(db, url);
      const input = local ? { imageData: local } : { imageUrl: url };
      const [vision, caption] = await Promise.all([
        analyzeImage(input).catch(() => null),
        describeImage(input).catch(() => ({ text: '' })),
      ]);
      const labels = [vision?.productTitle, vision?.category, ...(vision?.tags ?? [])].filter(Boolean).join(' ');
      return [labels, caption.text].filter(Boolean).join(' · ');
    }));
    findings.forEach((f, i) => { if (f) perPhoto.push(`Photo ${i + 1}: ${f}`); });
    const seen = new Set<string>();
    const merged = findings.join(' ').split(/[\s·,]+/).filter((w) => {
      const k = w.toLowerCase();
      if (!k || seen.has(k)) return false;
      seen.add(k);
      return true;
    }).join(' ');
    detected = [hint, merged].filter(Boolean).join(' ');
  }

  const heuristic = heuristicGenerateProduct(urls[0] ?? '', detected || hint);

  // Price against real comparable listings.
  const cats = await db.query(`SELECT DISTINCT category FROM products WHERE status = 'approved'`);
  const intent = parseIntent(detected || heuristic.title, cats.rows.map((r) => r.category));
  const comparables = await retrieveProducts(db, intent, 10);
  const prices = comparables.map((p) => p.priceMinor).sort((a, b) => a - b);
  const suggestedPriceMinor = prices.length ? prices[Math.floor(prices.length / 2)] : 0;

  if (aiConfigured()) {
    try {
      const context = comparables.length
        ? comparables.map((p) => `- ${p.title}: ${fmtUgx(p.priceMinor)} (${p.category})`).join('\n')
        : 'No comparable listings.';
      const llm = await askOpenRouter(
        'You write e-commerce listings for a Ugandan marketplace. Reply ONLY with compact JSON: ' +
          '{"title": string (max 70 chars, specific: brand + model + key spec), "description": string (3-5 sentences: what it is, condition, standout features, what is in the box, who it suits — written to sell, no fluff), "category": string, "brand": string, "suggestedPriceMinor": number (UGX), "highlights": string[] (3-5 short bullet points)}. No markdown, no commentary.',
        `Seller hint: ${hint || '(none)'}\n${perPhoto.length ? `What the ${perPhoto.length} photo${perPhoto.length > 1 ? 's' : ''} show:\n${perPhoto.join('\n')}` : 'No photos.'}\n\nComparable live listings:\n${context}\n\nAvailable categories: ${cats.rows
          .map((r) => r.category)
          .join(', ')}`,
        []
      );
      const json = JSON.parse(llm.text.replace(/^```(?:json)?|```$/g, '').trim());
      return {
        title: String(json.title || heuristic.title).slice(0, 70),
        description: String(json.description || heuristic.description),
        category: String(json.category || heuristic.category),
        brand: String(json.brand ?? ''),
        suggestedPriceMinor: Number(json.suggestedPriceMinor) || suggestedPriceMinor,
        highlights: Array.isArray(json.highlights) ? json.highlights.map(String).slice(0, 5) : [],
        detected: perPhoto,
        photosAnalyzed: urls.length,
        comparables: comparables.slice(0, 5),
        provider: llm.provider,
      };
    } catch {
      /* fall through to heuristic */
    }
  }

  return {
    ...heuristic,
    brand: '',
    suggestedPriceMinor,
    highlights: [] as string[],
    detected: perPhoto,
    photosAnalyzed: urls.length,
    comparables: comparables.slice(0, 5),
    provider: 'scottstechx-local',
  };
}

// ── Heuristic product generation (photo → title/description/category) ──────

const KEYWORD_MAP: Array<{ keys: string[]; title: string; category: string }> = [
  { keys: ['iphone', 'phone', 'samsung', 'android', 'galaxy', 'redmi', 'nokia', 'tecno', 'infinix'], title: 'Premium Smartphone — Like New', category: 'Electronics' },
  { keys: ['laptop', 'macbook', 'thinkpad', 'hp', 'dell', 'asus', 'acer'], title: 'Laptop Computer — Ready to Work', category: 'Electronics' },
  { keys: ['headphone', 'earbud', 'airpod', 'speaker'], title: 'Wireless Headphones / Earbuds', category: 'Electronics' },
  { keys: ['powerbank', 'charger', 'cable', 'adapter'], title: 'Power Bank & Charging Kit', category: 'Electronics' },
  { keys: ['tv', 'television', 'smart tv', 'oled', 'led'], title: 'Smart Television — HD Ready', category: 'Electronics' },
  { keys: ['watch', 'smartwatch', 'wrist'], title: 'Wristwatch / Smartwatch', category: 'Fashion' },
  { keys: ['shoe', 'sneaker', 'nike', 'adidas', 'boot', 'puma'], title: 'Footwear — Genuine Quality', category: 'Sports' },
  { keys: ['dress', 'ankara', 'kitenge', 'gomesi', 'skirt', 'fashion'], title: 'Stylish Ankara / Kitenge Dress', category: 'Fashion' },
  { keys: ['bag', 'handbag', 'backpack', 'purse'], title: 'Fashion Handbag / Backpack', category: 'Fashion' },
  { keys: ['lipstick', 'makeup', 'cosmetic', 'foundation', 'mascara'], title: 'Beauty & Makeup Essentials', category: 'Beauty' },
  { keys: ['soap', 'cream', 'lotion', 'skin', 'shampoo'], title: 'Skincare & Bath Essentials', category: 'Beauty' },
  { keys: ['rice', 'food', 'cooking oil', 'flour', 'sugar', 'beans'], title: 'Groceries — Fresh & Affordable', category: 'Groceries' },
  { keys: ['basket', 'home', 'furniture', 'decor', 'lamp', 'cushion'], title: 'Home & Living Essentials', category: 'Home & Living' },
  { keys: ['tire', 'tyre', 'wheel', 'car', 'auto', 'engine'], title: 'Automotive Accessory', category: 'Automotive' },
];

export function heuristicGenerateProduct(imageUrl: string, hint: string) {
  const haystack = `${imageUrl} ${hint}`.toLowerCase();
  let match = KEYWORD_MAP.find((m) => m.keys.some((k) => haystack.includes(k)));
  if (!match) match = { keys: [], title: 'New Arrival — Quality Product', category: 'Fashion' };
  return {
    title: hint ? `${hint.replace(/\b\w/g, (c) => c.toUpperCase()).slice(0, 60)}` : match.title,
    description:
      'Carefully sourced and inspected before listing. Fast delivery within Kampala and across Uganda, ' +
      'with Cash-on-Delivery available. Message the seller for more photos or a bulk discount.',
    category: match.category,
    suggestedPriceMinor: 0,
  };
}
