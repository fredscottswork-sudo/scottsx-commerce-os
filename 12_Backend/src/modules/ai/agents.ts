/**
 * ScottsTechX — AI agents.
 *
 * Each agent is a role-specialised persona over the same grounded catalog
 * retrieval. The client picks an agent (or lets the router choose) and the
 * agent decides what context to retrieve and how to answer.
 *
 *   shopping   — buyer: find/compare/recommend products
 *   negotiator — buyer: price advice, bargaining scripts, deal spotting
 *   support    — either: orders, refunds, delivery, account help
 *   listing    — seller: write titles/descriptions, price a product
 *   growth     — seller: what to stock, pricing strategy, store performance
 *   store      — any: questions about the marketplace itself
 */
import type pg from 'pg';
import {
  parseIntent,
  retrieveProducts,
  retrieveSellers,
  storeOverview,
  productsToContext,
  fmtUgx,
  type Intent,
  type RetrievedProduct,
} from './catalog-context.js';

export type AgentId = 'shopping' | 'negotiator' | 'support' | 'listing' | 'growth' | 'store';

export interface AgentDef {
  id: AgentId;
  name: string;
  tagline: string;
  audience: 'buyer' | 'seller' | 'both';
  icon: string;
  starters: string[];
}

export const AGENTS: AgentDef[] = [
  {
    id: 'shopping',
    name: 'Shopping Assistant',
    tagline: 'Finds the right product at the right price',
    audience: 'buyer',
    icon: 'shopping-bag',
    starters: [
      'Find me a smartphone under 800k',
      'Best rated laptops in Kampala',
      'Compare the top two TVs you have',
    ],
  },
  {
    id: 'negotiator',
    name: 'Deal Finder',
    tagline: 'Spots discounts and helps you bargain',
    audience: 'buyer',
    icon: 'tag',
    starters: [
      'What are the best deals right now?',
      'Is this a fair price for a smartwatch?',
      'Help me negotiate with the seller',
    ],
  },
  {
    id: 'support',
    name: 'Support Agent',
    tagline: 'Orders, refunds, delivery and account help',
    audience: 'both',
    icon: 'life-buoy',
    starters: ['Where is my order?', 'How do refunds work?', 'How do I pay with MoMo?'],
  },
  {
    id: 'listing',
    name: 'Listing Copilot',
    tagline: 'Writes and prices your product listings',
    audience: 'seller',
    icon: 'sparkles',
    starters: [
      'Write a listing for a used iPhone 13',
      'Suggest a price for Ankara dresses',
      'Improve my product description',
    ],
  },
  {
    id: 'growth',
    name: 'Growth Advisor',
    tagline: 'What to stock and how to sell more',
    audience: 'seller',
    icon: 'trending-up',
    starters: [
      'What should I stock next?',
      'How is my store performing?',
      'Which categories sell best?',
    ],
  },
  {
    id: 'store',
    name: 'Store Guide',
    tagline: 'Knows the whole ScottsTechX marketplace',
    audience: 'both',
    icon: 'compass',
    starters: ['What can I buy here?', 'Which stores are verified?', 'How many sellers are there?'],
  },
];

export function getAgent(id?: string): AgentDef {
  return AGENTS.find((a) => a.id === id) ?? AGENTS[0];
}

/** Pick the best agent for a prompt when the client didn't specify one. */
export function routeAgent(prompt: string, role: 'buyer' | 'seller' | 'admin' = 'buyer'): AgentId {
  const p = prompt.toLowerCase();
  if (/refund|order|delivery|shipping|track|payment|account|password|login|support|complain/.test(p))
    return 'support';
  if (role === 'seller') {
    if (/write|describe|description|title|list(ing)?|photo|upload|price my|how much should/.test(p))
      return 'listing';
    if (/stock|sell more|performance|revenue|growth|trend|competitor|market/.test(p)) return 'growth';
  }
  if (/deal|discount|cheap|bargain|negotiat|offer|sale/.test(p)) return 'negotiator';
  if (/how many|what is scottstechx|about the (app|store|platform)|verified stores?/.test(p))
    return 'store';
  return 'shopping';
}

export function agentSystemPrompt(agent: AgentDef, role: string): string {
  const base =
    'You are the ScottsTechX AI for a Ugandan e-commerce marketplace (prices in UGX). ' +
    'You are given LIVE CATALOG CONTEXT retrieved from the real database. ' +
    'Ground every factual claim about products, prices, stock, sellers and cities in that context. ' +
    'Never invent a product, price or seller that is not in the context. ' +
    'If the context is empty, say so plainly and suggest a broader search. ' +
    'Be concise, warm and practical. Use short paragraphs and bullet points. ' +
    `The user's role is ${role}.`;

  const persona: Record<AgentId, string> = {
    shopping:
      'You are a personal shopping assistant. Recommend specific items from the context, ' +
      'explain briefly why each fits, and compare on price, rating and stock.',
    negotiator:
      'You are a deal expert. Highlight discounts, flag over/under-priced items relative to ' +
      'similar catalog entries, and give the buyer a short, polite bargaining script when useful.',
    support:
      'You are a customer-support agent. Give clear step-by-step help on orders, refunds, ' +
      'delivery, payments (MTN MoMo / Airtel Money / card / cash on delivery) and account settings. ' +
      'If something needs a human, tell the user to switch the support screen to Admin mode.',
    listing:
      'You are a seller copilot. Produce marketplace-ready titles (max 70 chars), persuasive ' +
      'descriptions, the right category, and a price range justified by comparable catalog items. ' +
      'Remind the seller that new listings need admin approval before buyers can see them.',
    growth:
      'You are a growth advisor for a seller. Use catalog and category data to advise on what to ' +
      'stock, how to price against competitors, and how to improve store ratings.',
    store:
      'You are a guide to the marketplace itself. Answer with real counts, categories and cities ' +
      'from the context.',
  };
  return `${base}\n\n${persona[agent.id]}`;
}

export interface GroundedContext {
  intent: Intent;
  products: RetrievedProduct[];
  contextText: string;
  overview: Awaited<ReturnType<typeof storeOverview>>;
}

/** Retrieve everything the agent needs to answer this prompt. */
export async function buildContext(
  db: pg.Pool,
  agent: AgentDef,
  prompt: string
): Promise<GroundedContext> {
  const overview = await storeOverview(db);
  const categories = (overview.categories ?? []).map((c: any) => c.category);
  const intent = parseIntent(prompt, categories);

  const limit = agent.id === 'growth' || agent.id === 'store' ? 12 : 8;
  const products = await retrieveProducts(db, intent, limit);

  const parts: string[] = [];
  parts.push(
    `MARKETPLACE: ${overview.productCount} approved products from ${overview.sellerCount} sellers. ` +
      `Prices ${fmtUgx(overview.minPrice)}–${fmtUgx(overview.maxPrice)}. ` +
      `${overview.dealCount} active flash deals.`
  );
  parts.push(
    `CATEGORIES: ${(overview.categories ?? [])
      .map((c: any) => `${c.category} (${c.count})`)
      .join(', ')}`
  );
  parts.push(`MATCHING PRODUCTS:\n${productsToContext(products)}`);

  if (intent.wantsNearby || agent.id === 'store') {
    const sellers = await retrieveSellers(db, intent, 6);
    if (sellers.length) {
      parts.push(
        `STORES: ${sellers
          .map(
            (s) =>
              `${s.storeName}${s.verified ? ' (verified)' : ''} — ${s.city || 'Uganda'}, ` +
              `${s.productCount} products, rating ${s.rating}/5`
          )
          .join('\n')}`
      );
    }
  }

  return { intent, products, contextText: parts.join('\n\n'), overview };
}

/**
 * Deterministic, catalog-grounded answer used when no LLM key is set.
 * This is NOT a canned string — it reads the retrieved rows and composes a
 * real answer, so the assistant is genuinely useful with zero configuration.
 */
export function composeOfflineAnswer(
  agent: AgentDef,
  prompt: string,
  ctx: GroundedContext
): string {
  const { products, intent, overview } = ctx;
  const lines: string[] = [];

  if (agent.id === 'store') {
    lines.push(
      `**ScottsTechX marketplace right now**`,
      ``,
      `• **${overview.productCount} products** live from **${overview.sellerCount} sellers**`,
      `• Prices from **${fmtUgx(overview.minPrice)}** to **${fmtUgx(overview.maxPrice)}**`,
      `• **${overview.dealCount}** flash deals running`,
      ``,
      `**Top categories:** ${(overview.categories ?? [])
        .slice(0, 8)
        .map((c: any) => `${c.category} (${c.count})`)
        .join(' · ')}`
    );
    return lines.join('\n');
  }

  if (agent.id === 'support') {
    return supportAnswer(prompt);
  }

  if (agent.id === 'listing') {
    return listingAnswer(prompt, products);
  }

  if (!products.length) {
    lines.push(
      `I couldn't find anything matching **${prompt.trim()}** in the live catalog right now.`,
      ``,
      `Try a broader search — the marketplace currently has ${overview.productCount} products across ` +
        `${(overview.categories ?? [])
          .slice(0, 6)
          .map((c: any) => c.category)
          .join(', ')}.`
    );
    return lines.join('\n');
  }

  if (agent.id === 'growth') {
    const byCat = (overview.categories ?? []).slice(0, 5);
    lines.push(
      `**Category demand snapshot**`,
      ``,
      ...byCat.map((c: any) => `• **${c.category}** — ${c.count} listings on the platform`),
      ``,
      `Categories with fewer listings face less competition; categories with many listings have proven demand.`,
      ``,
      `**Comparable items to price against:**`
    );
  } else if (intent.wantsCheapest) {
    lines.push(`**Cheapest matches** for "${prompt.trim()}":`, ``);
  } else if (intent.wantsDeals) {
    lines.push(`**Best deals** I can see right now:`, ``);
  } else {
    lines.push(`Here's what I found for "${prompt.trim()}":`, ``);
  }

  for (const p of products.slice(0, 5)) {
    const deal = p.isFlashDeal ? ` · 🔥 **-${p.discountPercent}%**` : '';
    const stock = p.stockQuantity > 0 ? `${p.stockQuantity} in stock` : '**out of stock**';
    lines.push(
      `• **${p.title}** — ${fmtUgx(p.priceMinor)}${deal}`,
      `  ${p.rating}/5 (${p.ratingCount} reviews) · ${stock} · ${p.sellerName}` +
        `${p.verified ? ' ✓' : ''}${p.city ? ` · ${p.city}` : ''}`
    );
  }

  const cheapest = [...products].sort((a, b) => a.priceMinor - b.priceMinor)[0];
  const best = [...products].sort((a, b) => b.rating - a.rating)[0];
  lines.push(``);
  if (cheapest && best && cheapest.id !== best.id) {
    lines.push(
      `**Best value:** ${cheapest.title} at ${fmtUgx(cheapest.priceMinor)}.`,
      `**Highest rated:** ${best.title} at ${best.rating}/5.`
    );
  } else if (best) {
    lines.push(`**My pick:** ${best.title} — ${fmtUgx(best.priceMinor)}, rated ${best.rating}/5.`);
  }

  if (agent.id === 'negotiator' && cheapest) {
    lines.push(
      ``,
      `**Bargaining tip:** open by asking the seller for their best price on ` +
        `*${cheapest.title}* — mention you've seen similar listings from ` +
        `${fmtUgx(cheapest.priceMinor)} and ask whether delivery can be included.`
    );
  }

  return lines.join('\n');
}

function supportAnswer(prompt: string): string {
  const p = prompt.toLowerCase();
  if (/refund|return/.test(p)) {
    return [
      '**Refunds & returns**',
      '',
      '1. Open **Orders** and select the order in question.',
      '2. Tap **Request refund** and choose a reason.',
      '3. The seller has 48 hours to respond; the claim then escalates to ScottsTechX support.',
      '',
      'Most stores accept returns within **7 days** of delivery. You can track every claim under **Refunds**.',
      '',
      'Need a human? Switch the support screen to **Admin mode** and our team will reply on this thread.',
    ].join('\n');
  }
  if (/order|track|delivery|shipping|where is/.test(p)) {
    return [
      '**Tracking your order**',
      '',
      '• Go to **Orders** — every order shows a live status: pending → paid → shipped → delivered.',
      '• Delivery is arranged by the seller; their fee and free-delivery threshold are on the store page.',
      '• You can message the seller directly from the order for an ETA.',
      '',
      'If an order is stuck, open a ticket in **Admin mode** and support will step in.',
    ].join('\n');
  }
  if (/pay|momo|money|card|cash/.test(p)) {
    return [
      '**Payment options**',
      '',
      '• **MTN Mobile Money** and **Airtel Money** — you get an approval prompt on your phone.',
      '• **Card** payments are supported at checkout.',
      '• **Cash on delivery** is available from sellers who enable it (shown on the store page).',
      '',
      'Save a default method under **Payment methods** to check out faster next time.',
    ].join('\n');
  }
  if (/seller|sell|become/.test(p)) {
    return [
      '**Becoming a seller**',
      '',
      '1. Open **Profile → Become a seller**.',
      '2. Verify your email address.',
      '3. Fill in your store profile and location.',
      '',
      'You can then list products. **Every new listing is reviewed by an admin before buyers can see it** — approval usually takes a short while, and you get a notification either way.',
    ].join('\n');
  }
  return [
    "**I'm here to help.** I can assist with:",
    '',
    '• Orders and delivery tracking',
    '• Refunds and returns',
    '• Payments (MoMo, card, cash on delivery)',
    '• Account and store settings',
    '',
    'Tell me what you need, or switch to **Admin mode** to reach a human on the support team.',
  ].join('\n');
}

function listingAnswer(prompt: string, products: RetrievedProduct[]): string {
  const subject = prompt
    .replace(/write|create|make|a |an |the |listing|description|for|title|please/gi, '')
    .trim();
  const name = subject || 'your product';
  const lines: string[] = [];

  lines.push(`**Suggested title**`, `${titleCase(name)} — Quality Guaranteed, Fast Delivery`, ``);
  lines.push(
    `**Suggested description**`,
    `${titleCase(name)} in excellent condition, carefully inspected before listing. ` +
      `Delivered anywhere in Kampala and across Uganda, with cash on delivery available. ` +
      `Message me for extra photos, bulk pricing or a quick delivery quote.`,
    ``
  );

  if (products.length) {
    const prices = products.map((p) => p.priceMinor).sort((a, b) => a - b);
    const lo = prices[0];
    const hi = prices[prices.length - 1];
    const mid = prices[Math.floor(prices.length / 2)];
    lines.push(
      `**Pricing guidance** (from ${products.length} comparable listings)`,
      `• Market range: **${fmtUgx(lo)} – ${fmtUgx(hi)}**`,
      `• Typical price: **${fmtUgx(mid)}**`,
      `• To sell fast, price just under **${fmtUgx(mid)}**.`,
      ``
    );
  }

  lines.push(
    `**Before you publish**`,
    `• Add at least 3 clear photos on a plain background.`,
    `• Set an accurate stock count.`,
    `• Your listing goes to **admin review** — you'll be notified once it's approved and live.`
  );
  return lines.join('\n');
}

function titleCase(s: string): string {
  return s.replace(/\b\w/g, (c) => c.toUpperCase());
}
