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

/** True when ANY AI provider has a key configured. */
export function aiConfigured(): boolean {
  return Boolean(process.env.LLM_API_KEY || process.env.APIFREELLM_API_KEY);
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
    // grounded local composer and label it honestly.
    return {
      text: composeOfflineAnswer(agent, prompt, ctx),
      provider: 'scottstechx-local (llm unavailable)',
      model: 'catalog-grounded',
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

async function askOpenRouter(
  system: string,
  userContent: string,
  history: Array<{ role: 'user' | 'assistant'; content: string }>
) {
  const key = process.env.LLM_API_KEY;
  if (!key) throw new ServiceUnavailableError('LLM_API_KEY (OpenRouter) is not set');
  const model = process.env.AI_MODEL || 'meta-llama/llama-3.3-70b-instruct';

  const messages = [
    { role: 'system', content: system },
    ...history.slice(-8).map((h) => ({ role: h.role, content: h.content })),
    { role: 'user', content: userContent },
  ];

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), llmTimeoutMs());
  try {
    const res = await fetch(openRouterUrl(), {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${key}`,
        'Content-Type': 'application/json',
        'HTTP-Referer': 'https://scottstechx.app',
        'X-Title': 'ScottsTechX',
      },
      body: JSON.stringify({ model, messages, max_tokens: 800, temperature: 0.4 }),
      signal: controller.signal,
    });
    if (!res.ok) {
      const text = await res.text();
      throw new ServiceUnavailableError(`OpenRouter error ${res.status}: ${text.slice(0, 200)}`);
    }
    const data = await res.json();
    const text = extractReply(data?.choices?.[0]?.message);
    if (!text) throw new ServiceUnavailableError('OpenRouter returned an empty response');
    return { text, provider: 'openrouter', model };
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
 * Image search. The uploaded photo's labels/filename/hint are turned into a
 * catalog query. With a vision-capable LLM key configured the image is
 * described first; otherwise we fall back to the caller-supplied hint.
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

  if (!terms && raw) {
    const described = await describeImage(raw).catch(() => '');
    terms = described;
  }
  if (!terms && opts.imageUrl) {
    // Last resort: mine the filename/URL for keywords.
    terms = decodeURIComponent(opts.imageUrl)
      .split(/[/?#]/)
      .pop()!
      .replace(/[-_.]/g, ' ')
      .replace(/\.(jpg|jpeg|png|webp|gif)$/i, '');
  }

  const result = await aiSearch(db, terms || 'popular', limit);
  return {
    ...result,
    detected: terms,
    explanation: terms
      ? `Image looks like **${terms}** — ${result.products.length} similar item${
          result.products.length === 1 ? '' : 's'
        } found.`
      : 'Could not read the image — showing popular products instead.',
  };
}

/** Ask a vision model what the photo shows (only when a key is configured). */
async function describeImage(imageUrl: string): Promise<string> {
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
  if (!detected && opts.imageUrl) detected = await describeImage(opts.imageUrl).catch(() => '');

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
