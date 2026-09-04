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
    starters: ['Where is my order?', 'How do refunds work?', 'How do I pay for an order?'],
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
      'delivery, how payment is arranged with the seller (ScottsTechX does not process payments — ' +
      'buyers and sellers agree in chat, usually cash on delivery or a bank transfer) and account settings. ' +
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
  /** Near-misses shown when the strict search finds nothing. */
  fallbackProducts: RetrievedProduct[];
  contextText: string;
  overview: Awaited<ReturnType<typeof storeOverview>>;
}

/** One catalog row rendered as a chat bullet. */
function productLine(p: RetrievedProduct): string {
  const deal = p.isFlashDeal ? ` · 🔥 **-${p.discountPercent}%**` : '';
  const stock = p.stockQuantity > 0 ? `${p.stockQuantity} in stock` : '**out of stock**';
  return (
    `• **${p.title}** — ${fmtUgx(p.priceMinor)}${deal}\n` +
    `  ${p.rating}/5 (${p.ratingCount} reviews) · ${stock} · ${p.sellerName}` +
    `${p.verified ? ' ✓' : ''}${p.city ? ` · ${p.city}` : ''}`
  );
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
  // A greeting is not a search. Running one returned eight arbitrary products
  // to somebody who just said "hi".
  const products = intent.isGreeting ? [] : await retrieveProducts(db, intent, limit);

  // When the strict search finds nothing, work out what IS available instead of
  // replying with a dead end. Drop the price cap first (the usual cause - the
  // budget is simply below anything in stock), then drop keywords to the single
  // strongest one.
  let fallbackProducts: RetrievedProduct[] = [];
  if (!intent.isGreeting && products.length === 0) {
    if (intent.maxPriceMinor || intent.minPriceMinor) {
      fallbackProducts = await retrieveProducts(
        db, { ...intent, maxPriceMinor: undefined, minPriceMinor: undefined }, 5
      );
    }
    if (!fallbackProducts.length && intent.keywords.length > 1) {
      fallbackProducts = await retrieveProducts(
        db,
        { ...intent, keywords: [intent.keywords[intent.keywords.length - 1]], maxPriceMinor: undefined },
        5
      );
    }
    if (!fallbackProducts.length && intent.category) {
      fallbackProducts = await retrieveProducts(
        db, { ...intent, keywords: [], maxPriceMinor: undefined }, 5
      );
    }
  }

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

  return { intent, products, fallbackProducts, contextText: parts.join('\n\n'), overview };
}

/**
 * Deterministic, catalog-grounded answer used when no LLM key is set.
 * This is NOT a canned string — it reads the retrieved rows and composes a
 * real answer, so the assistant is genuinely useful with zero configuration.
 */
/** What each agent actually does, in its own words. */
function capabilityBlurb(id: AgentId): string {
  switch (id) {
    case 'shopping':
      return [
        'I search the whole catalogue for you and explain the trade-offs:',
        '• Find products by need, budget, brand or category',
        '• Compare two or three options side by side',
        '• Show what is in stock, and which seller has it',
      ].join('\n');
    case 'negotiator':
      return [
        'I watch prices so you do not overpay:',
        '• Spot the live flash deals and genuine discounts',
        '• Tell you whether a price is fair for that item',
        '• Suggest how to open a negotiation with the seller',
      ].join('\n');
    case 'support':
      return [
        'I handle everything after the "buy" button:',
        '• Order status, delivery and tracking',
        '• Refunds, returns and cancellations',
        '• Payment — cash on delivery or any method agreed with the seller',
        '• Account, login and verification problems',
      ].join('\n');
    case 'listing':
      return [
        'I help you publish products that actually sell:',
        '• Write the title, description and specs from a few details',
        '• Suggest a competitive price using what similar items sell for here',
        '• Point out what buyers ask for that your listing is missing',
      ].join('\n');
    case 'growth':
      return [
        'I look at the market and tell you where the money is:',
        '• Which categories are busy and which are crowded',
        '• What to stock next, and at what price',
        '• How your range compares with other sellers',
      ].join('\n');
    case 'store':
    default:
      return [
        'I know the marketplace as a whole:',
        '• What is on sale, from whom, and where they are',
        '• How buying and delivery work here (payment is agreed with the seller in chat)',
        '• Which sellers are verified, and what they specialise in',
      ].join('\n');
  }
}

export function composeOfflineAnswer(
  agent: AgentDef,
  prompt: string,
  ctx: GroundedContext
): string {
  const { products, intent, overview } = ctx;
  const lines: string[] = [];

  // "What can you do?" is the single most likely opening message, and it is a
  // question about the assistant rather than a product search. Answering it
  // with "I couldn't find anything matching 'what can you help me with?'" made
  // every agent look broken. Each agent introduces its own job and offers real
  // examples drawn from the live catalogue.
  if (intent.isCapabilityQuestion) {
    const starters = agent.starters.slice(0, 3).map((s) => `• "${s.toLowerCase()}"`);
    return [
      `**${agent.name}** — ${agent.tagline.toLowerCase()}.`,
      ``,
      capabilityBlurb(agent.id),
      ``,
      `I can see the whole store: **${overview.productCount} products** from ` +
        `**${overview.sellerCount} sellers**, priced ` +
        `${fmtUgx(overview.minPrice)}–${fmtUgx(overview.maxPrice)}` +
        `${overview.dealCount ? `, with **${overview.dealCount}** flash deals on` : ''}.`,
      ``,
      `Try asking:`,
      ...starters,
    ].join('\n');
  }

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

  // Greet back and offer a way in, rather than apologising for finding no
  // products in response to "hello".
  if (intent.isGreeting) {
    const topCats = (overview.categories ?? []).slice(0, 4).map((c: any) => c.category);
    return [
      `Hi! I'm ${agent.name} — I can see everything in the ScottsTechX store.`,
      ``,
      `Right now there are **${overview.productCount} products** from **${overview.sellerCount} sellers**, ` +
        `priced ${fmtUgx(overview.minPrice)}–${fmtUgx(overview.maxPrice)}` +
        `${overview.dealCount ? `, with **${overview.dealCount}** flash deals running` : ''}.`,
      ``,
      `Ask me things like:`,
      `• "show me phones under 2m"`,
      `• "cheapest laptop you have"`,
      `• "best rated ${topCats[0] ? topCats[0].toLowerCase() : 'electronics'}"`,
      `• "what deals are on today?"`,
    ].join('\n');
  }

  if (!products.length) {
    // Say what IS available near the request instead of a dead end. The
    // fallbacks are attached by buildContext when the strict search misses.
    const near = ctx.fallbackProducts;
    if (near && near.length) {
      // When the shopper named a budget we promise these are "listed by
      // price", so actually sort them cheapest-first — the nearest thing to
      // what they asked for should lead, not whatever the search ranked top.
      const ordered = intent.maxPriceMinor
        ? [...near].sort((a, b) => a.priceMinor - b.priceMinor)
        : near;
      const relaxed = [
        `Nothing matched **${prompt.trim()}** exactly, but here's the closest I have:`,
        ``,
        ...ordered.slice(0, 5).map((p) => productLine(p)),
      ];
      if (intent.maxPriceMinor) {
        relaxed.push(
          ``,
          `*Everything above your ${fmtUgx(intent.maxPriceMinor)} budget has been left out where possible — ` +
            `the closest options are listed by price.*`
        );
      }
      return relaxed.join('\n');
    }
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

  for (const p of products.slice(0, 5)) lines.push(productLine(p));

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
      '**Paying for an order**',
      '',
      '• ScottsTechX does not process payments itself.',
      '• You and the seller agree the method in chat — most orders are cash on delivery, a bank transfer to the seller, or collection at pickup.',
      '• Your order shows the seller’s contact and store details so you can arrange it.',
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
    '• Arranging payment with the seller (cash on delivery or as agreed in chat)',
    '• Account and store settings',
    '',
    'Tell me what you need, or switch to **Admin mode** to reach a human on the support team.',
  ].join('\n');
}

function listingAnswer(prompt: string, products: RetrievedProduct[]): string {
  // Strip the instruction wrapper to leave the product itself. This MUST be
  // word-bounded: the old pattern had bare alternatives like `a ` and `for`,
  // so "what can you help me with" lost the "an" inside "can" and became
  // "What Cyou Help Me With? - Quality Guaranteed" — a title generated from a
  // question. \b prevents matching inside a word.
  const subject = prompt
    .replace(
      /\b(please|write|create|make|generate|draft|suggest|a|an|the|listing|description|title|copy|for|me|my)\b/gi,
      ' '
    )
    .replace(/\s+/g, ' ')
    .trim();

  // If nothing recognisable is left, the user has not named a product yet.
  // Inventing a listing out of their question is worse than asking.
  if (subject.length < 3) {
    return [
      `**Listing Copilot** — tell me what you are selling and I'll write it.`,
      ``,
      `Give me any of these and I'll turn it into a full listing:`,
      `• the item and its condition — "iPhone 13, 128GB, used, clean"`,
      `• a brand and model — "Samsung A55 5G"`,
      `• even a rough note — "gaming laptop, 16gb ram, needs a fast sale"`,
      ``,
      `You'll get a title, a description, pricing guidance from comparable ` +
        `listings already on ScottsTechX, and the details buyers usually ask for.`,
    ].join('\n');
  }

  const name = subject;
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
