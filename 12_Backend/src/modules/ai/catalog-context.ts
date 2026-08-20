/**
 * ScottsTechX — AI catalog grounding.
 *
 * This is what makes the assistant "know the entire store" rather than
 * hallucinate: every AI turn first retrieves the *actual* rows that match the
 * user's question, then those rows are (a) fed to the LLM as context and
 * (b) returned to the client as structured product cards.
 *
 * It is deliberately provider-independent: the same retrieval powers the
 * offline engine and the LLM path, so answers stay grounded either way.
 */
import type pg from 'pg';

export interface RetrievedProduct {
  id: string;
  title: string;
  category: string;
  brand: string;
  priceMinor: number;
  stockQuantity: number;
  rating: number;
  ratingCount: number;
  imageUrl: string;
  isFlashDeal: boolean;
  discountPercent: number;
  sellerId: string;
  sellerName: string;
  city: string;
  verified: boolean;
}

export interface RetrievedSeller {
  id: string;
  storeName: string;
  city: string;
  rating: number;
  verified: boolean;
  productCount: number;
}

export interface Intent {
  /** Free-text keywords, stop-words stripped. */
  keywords: string[];
  /** A greeting or chit-chat with no shopping request in it. */
  isGreeting: boolean;
  category?: string;
  maxPriceMinor?: number;
  minPriceMinor?: number;
  wantsCheapest: boolean;
  wantsBest: boolean;
  wantsNearby: boolean;
  wantsDeals: boolean;
  city?: string;
}

/**
 * Words that describe HOW to search rather than WHAT to find. They are already
 * captured as structured intent (wantsDeals, wantsCheapest, ...) so they must
 * not also be matched against product titles - "what deals are on today" was
 * searching for the literal words "deals" and "today", matching nothing, and
 * reporting an empty catalogue while six flash deals were live.
 */
const INTENT_WORDS = new Set([
  'deal','deals','discount','discounts','offer','offers','sale','sales','flash','promo','promos',
  'bargain','bargains','today','now','currently','available','stock','new','latest','trending',
  'popular','recommend','recommendation','recommendations','suggest','suggestion','options','option',
  'sell','sells','selling','have','got','carry','stocked','anything','everything','all','show','see',
  'rated','rating','ratings','review','reviews','star','stars','deal',
]);

const STOP_WORDS = new Set([
  'a','an','the','is','are','am','do','does','did','i','you','me','my','we','us','it','of','for','to','in','on','at','and','or','but','with','without','what','which','who','whom','how','can','could','would','should','will','shall','have','has','had','want','need','looking','look','find','show','get','buy','purchase','please','some','any','best','good','cheap','cheapest','near','nearby','me','there','here','from','under','below','above','over','than','then','that','this','these','those','be','been','being','was','were','about','tell','give','list','something','anything','thing','things','product','products','item','items','store','stores','seller','sellers','shop','shops','ugx','shillings','shilling','money','price','prices','cost','costs',
]);

/** Escape user input before embedding it in a Postgres regex. */
function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** Two-letter product nouns that must survive the length filter. */
const SHORT_PRODUCT_WORDS = new Set(['tv', 'pc', 'ac', 'hd', '4k', 'ps4', 'ps5', 'usb', 'cpu', 'gpu', 'ram', 'ssd', 'fan']);

const UG_CITIES = ['kampala', 'entebbe', 'jinja', 'mbarara', 'gulu', 'mbale', 'masaka', 'arua', 'lira', 'fort portal'];

/**
 * Domain synonyms. Shoppers type "phone" but the catalog says "iPhone 15 Pro"
 * or "Galaxy A55"; without this the best matches are missed entirely.
 */
const SYNONYMS: Record<string, string[]> = {
  phone: ['iphone', 'galaxy', 'smartphone', 'mobile', 'tecno', 'infinix', 'redmi', 'pixel'],
  smartphone: ['iphone', 'galaxy', 'phone', 'mobile'],
  laptop: ['macbook', 'notebook', 'thinkpad', 'chromebook', 'ultrabook'],
  computer: ['laptop', 'macbook', 'desktop', 'pc'],
  tv: ['television', 'smart tv', 'led', 'oled'],
  television: ['tv', 'smart tv'],
  headphone: ['earbud', 'earphone', 'airpod', 'headset'],
  earbuds: ['earbud', 'airpod', 'headphone'],
  watch: ['smartwatch', 'wristwatch'],
  shoes: ['sneaker', 'footwear', 'boot', 'trainers'],
  dress: ['gown', 'ankara', 'kitenge'],
  fridge: ['refrigerator', 'freezer'],
  speaker: ['soundbar', 'bluetooth speaker'],
  charger: ['powerbank', 'power bank', 'adapter'],
  bag: ['handbag', 'backpack', 'purse'],
};

/**
 * Expand one user word into every catalog spelling worth matching:
 * the word itself, its singular/plural variants, and domain synonyms.
 */
export function expandTerm(word: string): string[] {
  const w = word.toLowerCase();
  const out = new Set<string>([w]);

  // Plural → singular
  if (w.endsWith('ies') && w.length > 4) out.add(`${w.slice(0, -3)}y`);
  if (w.endsWith('es') && w.length > 3) out.add(w.slice(0, -2));
  if (w.endsWith('s') && !w.endsWith('ss') && w.length > 3) out.add(w.slice(0, -1));
  // Singular → plural
  out.add(`${w}s`);

  for (const variant of [...out]) {
    for (const syn of SYNONYMS[variant] ?? []) out.add(syn);
  }
  return [...out];
}

/** Parse a natural-language shopping question into structured filters. */
export function parseIntent(prompt: string, knownCategories: string[] = []): Intent {
  const lower = prompt.toLowerCase();

  // "under 500k", "below 200,000", "less than 1m", "max 50000"
  let maxPriceMinor: number | undefined;
  const maxMatch = lower.match(/(?:under|below|less than|max|up to|cheaper than)\s*(?:ugx|shs?)?\s*([\d,.]+)\s*(k|m)?/);
  if (maxMatch) maxPriceMinor = parseAmount(maxMatch[1], maxMatch[2]);

  let minPriceMinor: number | undefined;
  const minMatch = lower.match(/(?:over|above|more than|at least|from|min)\s*(?:ugx|shs?)?\s*([\d,.]+)\s*(k|m)?/);
  if (minMatch) minPriceMinor = parseAmount(minMatch[1], minMatch[2]);

  const category = knownCategories.find((c) => lower.includes(c.toLowerCase()));
  const city = UG_CITIES.find((c) => lower.includes(c));

  // Short words are usually noise ("to", "of") but a handful are real product
  // nouns. Dropping them made "tv" match nothing, which fell through to an
  // unfiltered query and returned the entire catalogue.
  const keywords = lower
    .replace(/[^a-z0-9\s]/g, ' ')
    .split(/\s+/)
    .filter(
      (w) =>
        (w.length > 2 || SHORT_PRODUCT_WORDS.has(w)) &&
        !STOP_WORDS.has(w) &&
        !INTENT_WORDS.has(w) &&
        !/^\d+$/.test(w)
    )
    .slice(0, 8);

  // "hi" / "hello" used to fall through to an unfiltered query and dump eight
  // random products at somebody who had only said hello. A greeting with no
  // product words in it should be answered, not searched.
  const greeting = /^\s*(hi|hey|hello|yo|hola|howdy|good\s+(morning|afternoon|evening)|habari|oli otya|thanks|thank you|ok|okay)\b[\s!.,]*$/i.test(prompt.trim());
  const isGreeting = greeting && keywords.length === 0;

  return {
    keywords,
    isGreeting,
    category,
    maxPriceMinor,
    minPriceMinor,
    wantsCheapest: /cheap|cheapest|affordable|budget|lowest|least expensive/.test(lower),
    wantsBest: /best|top|highest rated|recommend|quality|premium/.test(lower),
    wantsNearby: /near|nearby|close|around me|closest|my area/.test(lower),
    wantsDeals: /deal|discount|offer|sale|flash|promo|bargain/.test(lower),
    city: city ? city.charAt(0).toUpperCase() + city.slice(1) : undefined,
  };
}

function parseAmount(numeric: string, suffix?: string): number {
  let n = Number(numeric.replace(/[,.](?=\d{3}\b)/g, '').replace(/,/g, ''));
  if (Number.isNaN(n)) return 0;
  if (suffix === 'k') n *= 1_000;
  if (suffix === 'm') n *= 1_000_000;
  return Math.round(n);
}

/** Retrieve the catalog rows relevant to an intent (approved products only). */
export async function retrieveProducts(
  db: pg.Pool,
  intent: Intent,
  limit = 8
): Promise<RetrievedProduct[]> {
  const where: string[] = [`p.status = 'approved'`];
  const values: any[] = [];

  if (intent.keywords.length) {
    // Each keyword matches any of its expansions (plural/singular/synonym), so
    // "cheapest phones" also finds "iPhone 15 Pro" and "Samsung Galaxy A55".
    // Match whole words, not substrings. A plain %car% also matches "SkinCARE",
    // which is why "do you sell cars" answered with cleansing bars; %phone%
    // likewise matches "headphones". Postgres \m and \M are word boundaries.
    // Plurals and synonyms are already covered by expandTerm, so recall does
    // not suffer: "headphone" still finds "Wireless Headphones".
    const ors: string[] = [];
    for (const kw of intent.keywords) {
      for (const variant of expandTerm(kw)) {
        values.push(`\\m${escapeRegex(variant)}\\M`);
        const i = values.length;
        ors.push(
          `(p.title ~* $${i} OR p.brand ~* $${i} OR p.category ~* $${i} OR p.description ~* $${i})`
        );
      }
    }
    where.push(`(${ors.join(' OR ')})`);
  }
  if (intent.category) {
    values.push(intent.category);
    where.push(`p.category = $${values.length}`);
  }
  if (intent.maxPriceMinor) {
    values.push(intent.maxPriceMinor);
    where.push(`p.price_minor <= $${values.length}`);
  }
  if (intent.minPriceMinor) {
    values.push(intent.minPriceMinor);
    where.push(`p.price_minor >= $${values.length}`);
  }
  if (intent.city) {
    values.push(`%${intent.city}%`);
    where.push(`(COALESCE(s.city, '') ILIKE $${values.length} OR p.location ILIKE $${values.length})`);
  }
  if (intent.wantsDeals) where.push('p.is_flash_deal = true');

  // Relevance score: a title hit is worth far more than a description hit, so
  // "phone" ranks the iPhone above a highly-rated pair of headphones whose
  // description happens to mention phones. Without this the ORDER BY was pure
  // rating and keyword matches were effectively ignored.
  let relevance = '0';
  if (intent.keywords.length) {
    const scores: string[] = [];
    for (const kw of intent.keywords) {
      const expansions = expandTerm(kw);
      expansions.forEach((variant, idx) => {
        // Whole-word match. A plain %phone% LIKE also matches "Headphones",
        // which outranked the actual iPhone. \m and \M are Postgres word
        // boundaries, so "phone" no longer fires inside "headphones".
        values.push(`\\m${escapeRegex(variant)}\\M`);
        const wordIdx = values.length;
        // Substring match still counts, but for much less.
        values.push(`%${variant}%`);
        const likeIdx = values.length;
        const weight = idx === 0 ? 3 : 1; // literal term beats a synonym

        scores.push(
          `(CASE WHEN p.title ~* $${wordIdx} THEN ${30 * weight} ELSE 0 END)`,
          `(CASE WHEN p.brand ~* $${wordIdx} THEN ${12 * weight} ELSE 0 END)`,
          `(CASE WHEN p.category ~* $${wordIdx} THEN ${8 * weight} ELSE 0 END)`,
          `(CASE WHEN p.description ~* $${wordIdx} THEN ${3 * weight} ELSE 0 END)`,
          `(CASE WHEN p.title ILIKE $${likeIdx} THEN ${2 * weight} ELSE 0 END)`,
          `(CASE WHEN p.description ILIKE $${likeIdx} THEN ${1 * weight} ELSE 0 END)`
        );
      });
    }
    relevance = scores.join(' + ');
  }

  let order = `relevance DESC, p.rating DESC, p.rating_count DESC`;
  if (intent.wantsCheapest) {
    // "cheapest phone" must be the cheapest PHONE, not the cheapest thing that
    // merely mentions phones somewhere. Sorting on price alone returned a pair
    // of headphones ahead of every actual handset, so keep only rows that
    // genuinely matched the keywords and sort those by price.
    // Rank true matches first, then by price - so "cheapest phone" is the
    // cheapest actual phone, not the cheapest thing that merely mentions
    // phones. Only title/brand/category count as "is this kind of thing":
    // a headphone description saying "pairs with your phone" is not a phone,
    // and including the description let it win every cheapest-X query.
    // Built HERE, not earlier, because a parameter that is pushed but never
    // referenced makes Postgres fail with "could not determine data type".
    const kindScores: string[] = [];
    for (const kw of intent.keywords) {
      for (const variant of expandTerm(kw)) {
        values.push(`\\m${escapeRegex(variant)}\\M`);
        const i = values.length;
        kindScores.push(
          `(CASE WHEN p.title ~* $${i} OR p.brand ~* $${i} OR p.category ~* $${i} THEN 1 ELSE 0 END)`
        );
      }
    }
    order = kindScores.length
      ? `(CASE WHEN (${kindScores.join(' + ')}) > 0 THEN 0 ELSE 1 END) ASC, p.price_minor ASC`
      : 'p.price_minor ASC';
  }
  else if (intent.wantsDeals) order = 'p.discount_percent DESC, p.price_minor ASC';
  else if (intent.wantsBest) order = 'p.rating DESC, p.rating_count DESC, relevance DESC';

  values.push(limit);

  const { rows } = await db.query(
    `SELECT p.id, p.title, p.category, p.brand,
            p.price_minor::int AS "priceMinor", p.stock_quantity AS "stockQuantity",
            p.rating::float AS rating, p.rating_count AS "ratingCount",
            COALESCE((SELECT url FROM product_media pm WHERE pm.product_id = p.id ORDER BY sort_order LIMIT 1), p.image_url) AS "imageUrl",
            p.is_flash_deal AS "isFlashDeal", p.discount_percent AS "discountPercent",
            u.id AS "sellerId", COALESCE(s.store_name, u.display_name) AS "sellerName",
            COALESCE(s.city, p.location) AS city, COALESCE(s.verified, false) AS verified,
            (${relevance})::int AS relevance
     FROM products p
     JOIN users u ON u.id = p.seller_id
     LEFT JOIN store_settings s ON s.user_id = p.seller_id
     WHERE ${where.join(' AND ')}
     ORDER BY ${order}
     LIMIT $${values.length}`,
    values
  );
  return rows as RetrievedProduct[];
}

/** Top stores, optionally filtered to a city the user mentioned. */
export async function retrieveSellers(
  db: pg.Pool,
  intent: Intent,
  limit = 5
): Promise<RetrievedSeller[]> {
  const values: any[] = [];
  let cityFilter = '';
  if (intent.city) {
    values.push(`%${intent.city}%`);
    cityFilter = ` AND s.city ILIKE $${values.length}`;
  }
  values.push(limit);
  const { rows } = await db.query(
    `SELECT u.id, COALESCE(s.store_name, u.display_name) AS "storeName",
            COALESCE(s.city, '') AS city, COALESCE(s.rating, 0)::float AS rating,
            COALESCE(s.verified, false) AS verified,
            (SELECT COUNT(*)::int FROM products p WHERE p.seller_id = u.id AND p.status = 'approved') AS "productCount"
     FROM users u
     LEFT JOIN store_settings s ON s.user_id = u.id
     WHERE u.role = 'seller'${cityFilter}
     ORDER BY s.verified DESC NULLS LAST, s.rating DESC NULLS LAST
     LIMIT $${values.length}`,
    values
  );
  return rows as RetrievedSeller[];
}

/** Whole-store summary — the assistant's "situational awareness". */
export async function storeOverview(db: pg.Pool) {
  const { rows } = await db.query(
    `SELECT
       (SELECT COUNT(*)::int FROM products WHERE status = 'approved') AS "productCount",
       (SELECT COUNT(*)::int FROM users WHERE role = 'seller') AS "sellerCount",
       (SELECT COALESCE(MIN(price_minor), 0)::int FROM products WHERE status = 'approved') AS "minPrice",
       (SELECT COALESCE(MAX(price_minor), 0)::int FROM products WHERE status = 'approved') AS "maxPrice",
       (SELECT COUNT(*)::int FROM products WHERE status = 'approved' AND is_flash_deal) AS "dealCount"`
  );
  const cats = await db.query(
    `SELECT category, COUNT(*)::int AS count FROM products WHERE status = 'approved'
     GROUP BY category ORDER BY count DESC LIMIT 12`
  );
  return { ...rows[0], categories: cats.rows };
}

export function fmtUgx(minor: number): string {
  return `UGX ${Number(minor || 0).toLocaleString('en-UG')}`;
}

/** Render retrieved rows as compact grounding text for the LLM prompt. */
export function productsToContext(products: RetrievedProduct[]): string {
  if (!products.length) return 'No matching products are currently listed.';
  return products
    .map(
      (p, i) =>
        `${i + 1}. ${p.title} — ${fmtUgx(p.priceMinor)} | ${p.category}${p.brand ? ` | ${p.brand}` : ''} | ` +
        `rating ${p.rating}/5 (${p.ratingCount}) | stock ${p.stockQuantity} | ` +
        `seller: ${p.sellerName}${p.verified ? ' (verified)' : ''}${p.city ? `, ${p.city}` : ''}` +
        `${p.isFlashDeal ? ` | FLASH DEAL -${p.discountPercent}%` : ''}`
    )
    .join('\n');
}
