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
  type AgentId,
} from './agents.js';
import { parseIntent, retrieveProducts, fmtUgx } from './catalog-context.js';
import { analyzeImage, rankByEmbedding, roboflowConfigured } from '../vision/roboflow.service.js';

/** How long interactive image search waits on the Roboflow workflow (ms).
 *  Generous enough for a serverless cold start, short enough that the photo
 *  search still feels instant-ish; the answer always falls back gracefully. */
const VISION_SEARCH_DEADLINE_MS = 6000;
/** How long interactive image search waits on the NVIDIA/LLM describe call (ms). */
const VISION_DESCRIBE_DEADLINE_MS = 8000;

/** NVIDIA NIM (OpenAI-compatible) endpoints. */
const NVIDIA_BASE_URL = 'https://integrate.api.nvidia.com/v1/chat/completions';
/** Chat/agent model — strong general LLM (text only). */
const NVIDIA_CHAT_MODEL = 'nvidia/nemotron-3-ultra-550b-a55b';
/** Vision caption model — must accept image_url (nemotron-3-ultra is text-only). */
const NVIDIA_VISION_MODEL = 'moonshotai/kimi-k3';

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

export interface AskOptions {
  db: pg.Pool;
  prompt: string;
  screen?: string;
  agent?: string;
  role?: 'buyer' | 'seller' | 'admin';
  userId?: string;
  history?: Array<{ role: 'user' | 'assistant'; content: string }>;
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
}

/**
 * The single entry point every AI surface calls (buyer chat, seller copilot,
 * AI search bar, support AI mode).
 */
export async function ask(opts: AskOptions): Promise<AskResult> {
  const { db, prompt, screen = 'generic', role = 'buyer', history = [] } = opts;

  const agent = getAgent(opts.agent ?? routeAgent(prompt, role));
  const ctx = await buildContext(db, agent, prompt);

  const meta = {
    screen,
    agent: { id: agent.id, name: agent.name, tagline: agent.tagline },
    // When the strict search misses we still talk about the relaxed matches in
    // the answer text, so ship those same products as cards — otherwise the
    // reply names items the shopper has no way to tap through to.
    products: ctx.products.length ? ctx.products : ctx.fallbackProducts,
    grounded: true,
  };

  if (!aiConfigured()) {
    return {
      text: composeOfflineAnswer(agent, prompt, ctx),
      provider: 'scottstechx-local',
      model: 'catalog-grounded',
      ...meta,
    };
  }

  const system = agentSystemPrompt(agent, role);
  const grounded = `LIVE CATALOG CONTEXT (retrieved from the database just now):\n${ctx.contextText}\n\nUSER QUESTION: ${prompt}`;

  try {
    const llm =
      activeProvider() === 'apifreellm'
        ? await askApiFreeLlm(system, grounded, history)
        : await askOpenRouter(system, grounded, history);
    return { text: llm.text, provider: llm.provider, model: llm.model, ...meta };
  } catch (err) {
    // A provider outage must never take the assistant down — fall back to the
    // grounded local composer and label it honestly, carrying the real error
    // so the UI can explain it (and so a bad model name is visible, not
    // swallowed).
    const reason = err instanceof Error ? err.message : String(err);
    console.warn(`[ai] LLM unavailable, falling back to composer: ${reason.slice(0, 300)}`);
    return {
      text: composeOfflineAnswer(agent, prompt, ctx),
      provider: 'scottstechx-local (llm unavailable)',
      model: 'catalog-grounded',
      llmError: reason.slice(0, 200),
      ...meta,
    };
  }
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
 * NOTE: kimi-k3 and llama-3.2-11b-vision accept image_url, so the chain also
 * serves caption requests when the primary vision model is overloaded.
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

/** Build the fallback chain from the models the key can actually use. */
function nvidiaFallbackModels(ids: string[] | null, primary: string): string[] {
  const listed = new Set(ids ?? []);
  const preferred = NVIDIA_FALLBACK_PREFERENCE.filter((m) => m !== primary && listed.has(m));
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
  if (nvidiaModelsCache && Date.now() - nvidiaModelsCache.at < 60_000) {
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

async function nvidiaChatCompletion(
  messages: Array<Record<string, unknown>>,
  maxTokens: number,
  opts: { model?: string; think?: boolean } = {}
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
  // resort). A hard-coded partner name that 404s would defeat the fallback.
  const candidates = [primary, ...nvidiaFallbackModels(models.ids, primary)];

  // Transient failures (NVIDIA's 'Service temporarily overloaded' 503, rate
  // limits, 5xx) recover in seconds. Retry the same model once, then move
  // down the usable-model chain — a talking answer from kimi-k2 beats an
  // offline fallback. Hard errors (auth/credits) and network failures abort
  // immediately: no model switch fixes those.
  const TRANSIENT_HTTP = new Set([408, 429, 500, 502, 503, 504]);
  const budgetMs = nvidiaTimeoutMs();
  const startedAt = Date.now();
  let lastError: unknown;
  let hardStop = false;

  for (const model of candidates) {
    if (hardStop) break;
    let attemptsLeft = 2; // 1 retry on this model before trying the next
    while (attemptsLeft > 0 && !hardStop) {
      attemptsLeft--;
      const remaining = budgetMs - (Date.now() - startedAt);
      if (remaining < 3000 && attemptsLeft > 0) {
        lastError ??= new ServiceUnavailableError('nvidia retry budget exhausted');
        hardStop = true;
        break;
      }
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), remaining);
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
        const data = await res.json();
        const text = extractReply(data?.choices?.[0]?.message);
        if (!text) throw new ServiceUnavailableError('nvidia returned an empty response');
        return { text, model };
      } catch (err) {
        lastError = err;
        const status = (err as { nvidiaStatus?: number }).nvidiaStatus ?? 0;
        const body = String((err as { nvidiaBody?: string }).nvidiaBody ?? '');
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
        if (transient && attemptsLeft > 0) {
          const backoff = Math.min(300 + 400 * (2 - attemptsLeft) + Math.round(Math.random() * 200), Math.max(remaining - 1000, 100));
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
  history: Array<{ role: 'user' | 'assistant'; content: string }>
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
    const r = await nvidiaChatCompletion(messages, 8192, { model: nvidiaModel(), think: true });
    return { text: r.text, provider: 'nvidia', model: r.model };
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), llmTimeoutMs());
  try {
    const res = await fetch(ep.url, {
      method: 'POST',
      headers: ep.headers,
      body: JSON.stringify({ model: ep.model, messages, max_tokens: 2048, temperature: 0.4 }),
      signal: controller.signal,
    });
    if (!res.ok) {
      const text = await res.text();
      throw new ServiceUnavailableError(`${ep.provider} error ${res.status}: ${text.slice(0, 200)}`);
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
  opts: { imageUrl?: string; imageData?: string; hint?: string; labels?: string[] },
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
  let vision: Awaited<ReturnType<typeof analyzeImage>> = null;
  let described = '';
  if (raw) {
    const robof = Promise.race([
      analyzeImage({ imageUrl: opts.imageUrl, imageData: opts.imageData }),
      new Promise<null>((resolve) => setTimeout(() => resolve(null), VISION_SEARCH_DEADLINE_MS)),
    ]);
    const caption = Promise.race([
      describeImage({ imageUrl: opts.imageUrl, imageData: opts.imageData }),
      new Promise<string>((resolve) => setTimeout(() => resolve(''), VISION_DESCRIBE_DEADLINE_MS)),
    ]);
    [vision, described] = await Promise.all([robof, caption]);
    if (!vision && roboflowConfigured()) {
      console.warn('[vision] image search skipped Roboflow after its deadline — answering from caption/heuristic.');
    }
    if (!described && nvidiaVisionConfigured()) {
      console.warn('[vision] image search skipped NVIDIA caption after its deadline.');
    }
  }

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

  // What we *tell* the user we detected. The NVIDIA caption is the most
  // human-readable; Roboflow labels follow; then merged terms (a
  // decision-only workflow response must never blank the answer). `query` is
  // the full search expression actually used, so the UI/debug can see all
  // signals (caption + labels + hint + filename) merged into one query.
  const visionLabel = vision
    ? [vision.productTitle, vision.category, ...vision.tags].filter(Boolean).join(' ')
    : '';
  const detectedLabel = described || visionLabel || terms;
  return {
    ...result,
    detected: detectedLabel,
    query: terms,
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
 * URL (file uploads). Never throws; returns '' when unconfigured or on error.
 */
async function describeImage(input: { imageUrl?: string; imageData?: string }): Promise<string> {
  const imageUrl = input.imageData || input.imageUrl;
  if (!imageUrl) return '';

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
        // text-only) and skip thinking so the caption is fast.
        { model: nvidiaVisionModel(), think: false }
      );
      return r.text.slice(0, 120);
    } catch (err) {
      console.warn(`[vision] NVIDIA describe failed (${err instanceof Error ? err.name : 'error'})`);
      return '';
    }
  }

  // ── Legacy OpenRouter vision path (LLM_API_KEY / AI_VISION_MODEL) ─────────
  const key = process.env.LLM_API_KEY;
  if (!key) return '';
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
  if (!res.ok) return '';
  const data = await res.json();
  // Vision models reason too — strip the thinking or it becomes the caption.
  return extractReply(data?.choices?.[0]?.message).slice(0, 80);
}

// ── Seller listing generation ───────────────────────────────────────────────

/**
 * Generate a listing from a photo/hint, priced against real comparable rows.
 * Uses the LLM when available and always returns a usable result.
 */
export async function generateProduct(
  db: pg.Pool,
  opts: { imageUrl?: string; hint?: string }
) {
  const hint = (opts.hint ?? '').trim();
  let detected = hint;
  if (!detected && opts.imageUrl) {
    // Roboflow labels are catalogue-trained, so they win when present; the
    // NVIDIA/LLM caption is the fallback (and the general "what is it").
    const vision = await analyzeImage({ imageUrl: opts.imageUrl });
    const labels = [vision?.productTitle, vision?.category, ...(vision?.tags ?? [])]
      .filter(Boolean)
      .join(' ');
    detected =
      labels || (await describeImage({ imageUrl: opts.imageUrl }).catch(() => ''));
  }

  const heuristic = heuristicGenerateProduct(opts.imageUrl ?? '', detected || hint);

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
          '{"title": string (max 70 chars), "description": string (2-3 sentences), "category": string, "brand": string, "suggestedPriceMinor": number (UGX)}. No markdown, no commentary.',
        `Product hint: ${detected || hint || 'unknown product'}\n\nComparable live listings:\n${context}\n\nAvailable categories: ${cats.rows
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
