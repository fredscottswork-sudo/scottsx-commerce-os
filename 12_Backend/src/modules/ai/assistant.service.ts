/**
 * ScottsTechX — AI assistant service.
 *
 * Talks to OpenRouter (default model meta-llama/llama-3.3-70b-instruct).
 * When LLM_API_KEY is empty the service degrades to a deterministic offline
 * fallback so the API surface stays testable.
 */
import { ServiceUnavailableError } from '../../errors.js';

const OPENROUTER_URL = 'https://openrouter.ai/api/v1/chat/completions';
const APIFREELLM_URL = 'https://apifreellm.com/api/v1/chat';

/** True when ANY AI provider has a key configured. */
export function aiConfigured(): boolean {
  return Boolean(process.env.LLM_API_KEY || process.env.APIFREELLM_API_KEY);
}

function activeProvider(): string {
  return (process.env.AI_PROVIDER || 'openrouter').toLowerCase();
}

function systemPrompt(): string {
  return (
    'You are the ScottsTechX AI assistant for a Ugandan e-commerce marketplace. ' +
    'Use the live catalog context provided in the user message to give store-specific ' +
    'answers about products, sellers, prices and locations. Prices are in UGX. ' +
    'Format answers with short paragraphs and bullet points. Be concise and friendly.'
  );
}

export interface AskOptions {
  prompt: string;
  screen?: string;
  history?: Array<{ role: 'user' | 'assistant'; content: string }>;
}

/** Fold system + history + user prompt into one message (apifreellm style). */
function foldMessages(prompt: string, history: AskOptions['history']): string {
  const parts = [systemPrompt()];
  for (const h of history ?? []) {
    parts.push(`${h.role === 'user' ? 'User' : 'Assistant'}: ${h.content}`);
  }
  parts.push(`User: ${prompt}`);
  return parts.join('\n\n');
}

/**
 * Ask the LLM. Returns { text, provider, model }.
 * Throws ServiceUnavailableError when no key is configured or the upstream fails.
 */
export async function askAi({ prompt, screen, history = [] }: AskOptions) {
  if (!aiConfigured()) {
    throw new ServiceUnavailableError(
      'AI is not configured — set LLM_API_KEY (OpenRouter) or APIFREELLM_API_KEY in 12_Backend/.env'
    );
  }
  if (activeProvider() === 'apifreellm') {
    return askApiFreeLlm({ prompt, screen, history });
  }
  return askOpenRouter({ prompt, screen, history });
}

async function askOpenRouter({ prompt, screen, history = [] }: AskOptions) {
  const key = process.env.LLM_API_KEY;
  if (!key) throw new ServiceUnavailableError('LLM_API_KEY (OpenRouter) is not set');
  const model = process.env.AI_MODEL || 'meta-llama/llama-3.3-70b-instruct';
  const messages = [
    { role: 'system', content: systemPrompt() },
    ...history.map((h) => ({ role: h.role, content: h.content })),
    { role: 'user', content: prompt },
  ];

  const res = await fetch(OPENROUTER_URL, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${key}`,
      'Content-Type': 'application/json',
      'HTTP-Referer': 'https://scottstechx.app',
      'X-Title': 'ScottsTechX',
    },
    body: JSON.stringify({ model, messages, max_tokens: 700 }),
  });

  if (!res.ok) {
    const text = await res.text();
    throw new ServiceUnavailableError(`OpenRouter error ${res.status}: ${text.slice(0, 200)}`);
  }
  const data = await res.json();
  const text = data?.choices?.[0]?.message?.content ?? '';
  return { text: String(text), provider: 'openrouter', model, screen: screen ?? 'generic' };
}

async function askApiFreeLlm({ prompt, screen, history = [] }: AskOptions) {
  const key = process.env.APIFREELLM_API_KEY;
  if (!key) throw new ServiceUnavailableError('APIFREELLM_API_KEY is not set');

  const res = await fetch(APIFREELLM_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${key}`,
    },
    body: JSON.stringify({ message: foldMessages(prompt, history) }),
  });
  const data = await res.json().catch(() => ({} as Record<string, unknown>));

  if (!res.ok) {
    const code = Number(data.code ?? res.status);
    const msg = String(data.error ?? res.statusText ?? 'unknown error');
    if (code === 403 && /datacenter|cloud IP/i.test(msg)) {
      throw new ServiceUnavailableError(
        'apifreellm free tier is not available from datacenter/cloud IPs — run the backend from a residential connection, upgrade to premium, or set AI_PROVIDER=openrouter with an LLM_API_KEY.'
      );
    }
    throw new ServiceUnavailableError(`apifreellm error ${res.status}: ${msg.slice(0, 200)}`);
  }

  const text =
    (data as any).message ??
    (data as any).data?.message ??
    (data as any).choices?.[0]?.message?.content ??
    (data as any).text ??
    '';
  if (!text) throw new ServiceUnavailableError('apifreellm returned an empty response');
  return {
    text: String(text),
    provider: 'apifreellm',
    model: String((data as any).model ?? 'apifreellm'),
    screen: screen ?? 'generic',
  };
}

/** Deterministic fallback used when no LLM key is configured. */
export function offlineFallback(prompt: string, screen: string) {
  const p = prompt.toLowerCase();
  if (p.includes('near') || p.includes('nearby')) {
    return {
      text:
        'You can browse sellers near you from the Nearby tab — pick a city chip (Kampala, Entebbe, Jinja, Mbarara, Gulu or Mbale), set a radius, and the list shows verified sellers closest to you first.',
      provider: 'offline-fallback',
      model: 'none',
      screen,
    };
  }
  if (p.includes('cheapest') || p.includes('price') || p.includes('lowest')) {
    return {
      text:
        'Open the Home tab and sort products by price to find the best deal. Flash deals (with discount % badges) are the cheapest picks right now — they are refreshed from the live catalog.',
      provider: 'offline-fallback',
      model: 'none',
      screen,
    };
  }
  return {
    text:
      'I’m running in offline-fallback mode because no LLM_API_KEY is configured in 12_Backend/.env. ' +
      'Set your OpenRouter key to get live, catalog-aware answers. For now, try asking about nearby sellers or flash deals.',
    provider: 'offline-fallback',
    model: 'none',
    screen,
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
    title: match.title,
    description:
      'Carefully sourced and inspected before listing. Fast delivery within Kampala and across Uganda, with Cash-on-Delivery available. Message the seller for more photos or a bulk discount.',
    category: match.category,
    suggestedPriceMinor: 0,
  };
}
