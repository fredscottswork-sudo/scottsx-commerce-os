#!/usr/bin/env node
/**
 * ScottsTechX — marketplace seed.
 *
 * Run standalone:   node seed_marketplace.mjs
 * (The server also runs this automatically when the products table is empty.)
 *
 * Seeds 6 Ugandan sellers + 24 products with real Unsplash image URLs.
 * Idempotent: it wipes the seed-owned rows and re-inserts fresh data.
 */
import pg from 'pg';
import bcrypt from 'bcryptjs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import dotenv from 'dotenv';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
dotenv.config({ path: path.join(__dirname, '.env') });

const { Client } = pg;

const DB_URL =
  process.env.DATABASE_URL ||
  `postgresql://${process.env.PG_USER || 'app'}:${process.env.PG_PASSWORD || 'app'}@127.0.0.1:${process.env.PG_PORT || 5433}/${process.env.PG_DB || 'scottstechx'}`;

const IMG = (id, extra = '') =>
  `https://images.unsplash.com/${id}?w=800${extra}`;

// ── 6 sellers (Ugandan stores) ─────────────────────────────────────────────
const CITIES = {
  Kampala: { lat: 0.3476, lng: 32.5825 },
  Entebbe: { lat: 0.0611, lng: 32.4444 },
  Jinja: { lat: 0.4255, lng: 33.2041 },
  Mbarara: { lat: -0.6072, lng: 30.6545 },
  Gulu: { lat: 2.7724, lng: 32.2881 },
  Mbale: { lat: 1.0747, lng: 34.1761 },
};

const SELLERS = [
  {
    email: 'techhub@scottstechx.ug',
    storeName: 'Tech Hub Uganda',
    description: 'Genuine electronics — phones, laptops and accessories with warranty.',
    city: 'Kampala',
    verified: true,
    rating: 4.7,
    phone: '+256 700 111 222',
  },
  {
    email: 'fashionhouse@scottstechx.ug',
    storeName: 'Fashion House',
    description: 'Ankara, kitenge and everyday fashion made for Uganda.',
    city: 'Entebbe',
    verified: true,
    rating: 4.5,
    phone: '+256 702 222 333',
  },
  {
    email: 'sneakerking@scottstechx.ug',
    storeName: 'Sneaker King',
    description: 'Genuine trainers, runners and sports gear at fair prices.',
    city: 'Jinja',
    verified: false,
    rating: 4.4,
    phone: '+256 774 333 444',
  },
  {
    email: 'homebeyond@scottstechx.ug',
    storeName: 'Home & Beyond',
    description: 'Home essentials, groceries and auto accessories delivered in Kampala.',
    city: 'Kampala',
    verified: false,
    rating: 4.3,
    phone: '+256 705 444 555',
  },
  {
    email: 'glamour@scottstechx.ug',
    storeName: 'Glamour Cosmetics',
    description: 'Beauty and skincare — lipsticks, soaps and body care.',
    city: 'Kampala',
    verified: true,
    rating: 4.6,
    phone: '+256 787 555 666',
  },
  {
    email: 'ugandacrafts@scottstechx.ug',
    storeName: 'Uganda Crafts',
    description: 'Handwoven baskets and crafts from local artisans.',
    city: 'Jinja',
    verified: true,
    rating: 4.8,
    phone: '+256 772 666 777',
  },
];

// ── 24 products (4 Electronics, 4 Fashion, 4 Sports, 4 Beauty, 2 Home, 2 Groceries, 2 Automotive, 2 more) ──
const PRODUCTS = [
  // Electronics (6)
  { seller: 'techhub@scottstechx.ug', title: 'Samsung Galaxy A55 5G 128GB', category: 'Electronics', brand: 'Samsung', price: 1650000, old: 1800000, stock: 12, img: IMG('photo-1610792516775-01de03eae630'), rating: 4.6, count: 84, flash: false, discount: 0, city: 'Kampala' },
  { seller: 'techhub@scottstechx.ug', title: 'iPhone 15 Pro 256GB Natural Titanium', category: 'Electronics', brand: 'Apple', price: 4500000, old: 4800000, stock: 6, img: IMG('photo-1592286927505-1def25115558'), rating: 4.9, count: 121, flash: false, discount: 0, city: 'Kampala' },
  { seller: 'techhub@scottstechx.ug', title: 'MacBook Air M2 13-inch (2024)', category: 'Electronics', brand: 'Apple', price: 7800000, old: null, stock: 4, img: IMG('photo-1517336714731-489689fd1ca8'), rating: 4.8, count: 47, flash: false, discount: 0, city: 'Kampala' },
  { seller: 'techhub@scottstechx.ug', title: '55-inch 4K Smart TV with HDR', category: 'Electronics', brand: 'Samsung', price: 2900000, old: 3500000, stock: 8, img: IMG('photo-1593359677879-a4bb92f829d1'), rating: 4.7, count: 66, flash: true, discount: 17, city: 'Kampala' },
  { seller: 'techhub@scottstechx.ug', title: 'Power Bank 20000mAh Fast Charge', category: 'Electronics', brand: 'Anker', price: 185000, old: null, stock: 40, img: IMG('photo-1609091839311-d5365f9ff1c5'), rating: 4.5, count: 190, flash: false, discount: 0, city: 'Kampala' },
  { seller: 'techhub@scottstechx.ug', title: 'Wireless Headphones with Mic', category: 'Electronics', brand: 'Sony', price: 320000, old: 380000, stock: 25, img: IMG('photo-1505740420928-5e560c06d30e'), rating: 4.6, count: 143, flash: false, discount: 0, city: 'Kampala' },
  // Fashion (4)
  { seller: 'fashionhouse@scottstechx.ug', title: 'Ankara Maxi Dress — African Print', category: 'Fashion', brand: 'Fashion House', price: 85000, old: 105000, stock: 30, img: IMG('photo-1591561954557-26941169b49e'), rating: 4.7, count: 58, flash: true, discount: 19, city: 'Entebbe' },
  { seller: 'fashionhouse@scottstechx.ug', title: 'Kitenge Two-Piece Set', category: 'Fashion', brand: 'Fashion House', price: 95000, old: null, stock: 22, img: IMG('photo-1591561954557-26941169b49e', '&h=900&fit=crop'), rating: 4.4, count: 34, flash: false, discount: 0, city: 'Entebbe' },
  { seller: 'fashionhouse@scottstechx.ug', title: 'Classic Leather Wristwatch', category: 'Fashion', brand: 'Timex', price: 240000, old: 290000, stock: 15, img: IMG('photo-1523275335684-37898b6baf30'), rating: 4.3, count: 71, flash: false, discount: 0, city: 'Entebbe' },
  { seller: 'fashionhouse@scottstechx.ug', title: 'Designer Ankara Gown', category: 'Fashion', brand: 'Fashion House', price: 130000, old: null, stock: 18, img: IMG('photo-1591561954557-26941169b49e', '&h=1000&fit=crop'), rating: 4.6, count: 26, flash: false, discount: 0, city: 'Entebbe' },
  // Sports (4)
  { seller: 'sneakerking@scottstechx.ug', title: 'Nike Air Zoom Running Shoes', category: 'Sports', brand: 'Nike', price: 420000, old: 500000, stock: 14, img: IMG('photo-1542291026-7eec264c27ff'), rating: 4.8, count: 132, flash: true, discount: 16, city: 'Jinja' },
  { seller: 'sneakerking@scottstechx.ug', title: 'Adidas Ultraboost Trainers', category: 'Sports', brand: 'Adidas', price: 380000, old: null, stock: 10, img: IMG('photo-1542291026-7eec264c27ff', '&h=800&fit=crop'), rating: 4.5, count: 88, flash: false, discount: 0, city: 'Jinja' },
  { seller: 'sneakerking@scottstechx.ug', title: 'Basketball High-Tops', category: 'Sports', brand: 'Puma', price: 350000, old: 390000, stock: 9, img: IMG('photo-1542291026-7eec264c27ff', '&h=850&fit=crop'), rating: 4.2, count: 41, flash: false, discount: 0, city: 'Jinja' },
  { seller: 'sneakerking@scottstechx.ug', title: 'Trail Running Sneakers', category: 'Sports', brand: 'New Balance', price: 310000, old: null, stock: 16, img: IMG('photo-1542291026-7eec264c27ff', '&h=950&fit=crop'), rating: 4.4, count: 53, flash: false, discount: 0, city: 'Jinja' },
  // Beauty (4)
  { seller: 'glamour@scottstechx.ug', title: 'Matte Lipstick Set — 6 Shades', category: 'Beauty', brand: 'Glamour', price: 65000, old: 82000, stock: 60, img: IMG('photo-1586495777744-4413f21062fa'), rating: 4.6, count: 96, flash: true, discount: 21, city: 'Kampala' },
  { seller: 'glamour@scottstechx.ug', title: 'Liquid Lipstick Trio', category: 'Beauty', brand: 'Glamour', price: 55000, old: null, stock: 45, img: IMG('photo-1586495777744-4413f21062fa', '&h=900&fit=crop'), rating: 4.3, count: 62, flash: false, discount: 0, city: 'Kampala' },
  { seller: 'glamour@scottstechx.ug', title: 'Shea Butter Beauty Soap', category: 'Beauty', brand: 'Glamour', price: 18000, old: 22000, stock: 120, img: IMG('photo-1600857544200-b2f666a9a2ec'), rating: 4.5, count: 210, flash: false, discount: 0, city: 'Kampala' },
  { seller: 'glamour@scottstechx.ug', title: 'Skincare Cleansing Bar', category: 'Beauty', brand: 'Glamour', price: 15000, old: null, stock: 140, img: IMG('photo-1600857544200-b2f666a9a2ec', '&h=800&fit=crop'), rating: 4.4, count: 177, flash: false, discount: 0, city: 'Kampala' },
  // Home & Living (2)
  { seller: 'ugandacrafts@scottstechx.ug', title: 'Handwoven Storage Basket', category: 'Home & Living', brand: 'Uganda Crafts', price: 45000, old: 56000, stock: 50, img: IMG('photo-1556909114-f6e7ad7d3136'), rating: 4.9, count: 78, flash: true, discount: 20, city: 'Jinja' },
  { seller: 'ugandacrafts@scottstechx.ug', title: 'Rattan Laundry Basket', category: 'Home & Living', brand: 'Uganda Crafts', price: 38000, old: null, stock: 35, img: IMG('photo-1556909114-f6e7ad7d3136', '&h=850&fit=crop'), rating: 4.7, count: 44, flash: false, discount: 0, city: 'Jinja' },
  // Groceries (2)
  { seller: 'homebeyond@scottstechx.ug', title: 'Basmati Rice 5kg — Premium', category: 'Groceries', brand: 'Home & Beyond', price: 52000, old: 58000, stock: 80, img: IMG('photo-1586201375761-83885001b20f'), rating: 4.5, count: 150, flash: false, discount: 0, city: 'Kampala' },
  { seller: 'homebeyond@scottstechx.ug', title: 'Sunflower Cooking Oil 5L', category: 'Groceries', brand: 'Home & Beyond', price: 68000, old: 82000, stock: 65, img: IMG('photo-1474979266404-7eaacbcd87c5'), rating: 4.4, count: 118, flash: true, discount: 17, city: 'Kampala' },
  // Automotive (2)
  { seller: 'homebeyond@scottstechx.ug', title: '17-inch All-Weather Tire', category: 'Automotive', brand: 'Bridgestone', price: 350000, old: null, stock: 20, img: IMG('photo-1568844293986-8d0400bd4745'), rating: 4.2, count: 19, flash: false, discount: 0, city: 'Kampala' },
  { seller: 'homebeyond@scottstechx.ug', title: 'SUV All-Terrain Tire Pair', category: 'Automotive', brand: 'Goodyear', price: 720000, old: 800000, stock: 8, img: IMG('photo-1568844293986-8d0400bd4745', '&h=800&fit=crop'), rating: 4.1, count: 12, flash: false, discount: 0, city: 'Kampala' },
];

const SELLER_PASSWORD = 'Seller123!';

async function main() {
  const client = new Client({ connectionString: DB_URL });
  await client.connect();

  console.log('[seed] wiping seed-owned rows…');
  await client.query(`
    TRUNCATE products, product_media, conversations, messages, message_reads,
             bookmarks, order_items, orders, refund_claims RESTART IDENTITY CASCADE;
  `);
  const seedEmails = SELLERS.map((s) => s.email);
  await client.query(`DELETE FROM users WHERE email = ANY($1::text[])`, [seedEmails]);

  console.log('[seed] inserting 6 sellers…');
  const passwordHash = bcrypt.hashSync(SELLER_PASSWORD, 10);
  const sellerIds = {};
  for (const s of SELLERS) {
    const c = CITIES[s.city] ?? CITIES.Kampala;
    const { rows } = await client.query(
      `INSERT INTO users (email, password_hash, role, display_name, phone, email_verified, lat, lng, city)
       VALUES ($1, $2, 'seller', $3, $4, true, $5, $6, $7)
       RETURNING id`,
      [s.email, passwordHash, s.storeName, s.phone, c.lat, c.lng, s.city]
    );
    sellerIds[s.email] = rows[0].id;
    await client.query(
      `INSERT INTO store_settings (
         user_id, store_name, store_description, city, lat, lng, address, verified, rating,
         delivery_fee_ugx, free_above_ugx, cod_enabled, contact_email, contact_phone,
         service_radius_km, returns_window_days
       ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16)`,
      [
        rows[0].id, s.storeName, s.description, s.city, c.lat, c.lng,
        `Shop at ${s.city} central market`, s.verified, s.rating,
        8000, 50000, true, s.email, s.phone, 30, 7,
      ]
    );
  }

  console.log('[seed] inserting 24 products…');
  for (const p of PRODUCTS) {
    const sellerId = sellerIds[p.seller];
    if (!sellerId) {
      console.warn(`[seed] unknown seller for "${p.title}" — skipping`);
      continue;
    }
    const { rows } = await client.query(
      `INSERT INTO products (
         seller_id, title, description, category, brand, price_minor, old_price_minor,
         stock_quantity, image_url, rating, rating_count, is_flash_deal, discount_percent, location
       ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14)
       RETURNING id`,
      [
        sellerId, p.title,
        `Genuine ${p.brand} ${p.title.toLowerCase()} — inspected before listing. Fast delivery in ${p.city} and across Uganda, cash on delivery available. Message the seller for details or bulk pricing.`,
        p.category, p.brand, p.price, p.old ?? null, p.stock, p.img, p.rating, p.count,
        p.flash, p.discount, p.city,
      ]
    );
    await client.query(
      `INSERT INTO product_media (product_id, url, sort_order) VALUES ($1, $2, 0)`,
      [rows[0].id, p.img]
    );
  }

  const counts = await client.query(
    `SELECT (SELECT COUNT(*)::int FROM users WHERE role='seller') AS sellers,
            (SELECT COUNT(*)::int FROM products) AS products`
  );
  console.log(`[seed] done — ${counts.rows[0].sellers} sellers, ${counts.rows[0].products} products`);
  console.log(`[seed] seller login: any seed email + password "${SELLER_PASSWORD}"`);

  await client.end();
}

main().catch((err) => {
  console.error('[seed] failed:', err);
  process.exit(1);
});
