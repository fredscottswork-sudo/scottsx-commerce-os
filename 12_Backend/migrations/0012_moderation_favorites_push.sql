-- 0012_moderation_favorites_push.sql
-- Adds the pillars this release is built on:
--   1. Product moderation  — nothing is publicly visible until an admin approves it.
--   2. Favourites          — buyers follow sellers; drives push fan-out.
--   3. Device tokens       — real phone push (FCM) targeting.
--   4. Live seller location— nearby stores re-sort as the buyer moves, and the
--                            seller pin sticks at its last known point when the
--                            seller stops sharing location.
--   5. Search acceleration — trigram + composite indexes for the catalog search.

-- ── 1. Product moderation ───────────────────────────────────────────────────
ALTER TABLE products ADD COLUMN IF NOT EXISTS status text NOT NULL DEFAULT 'approved';
ALTER TABLE products DROP CONSTRAINT IF EXISTS products_status_check;
ALTER TABLE products ADD CONSTRAINT products_status_check
  CHECK (status IN ('draft', 'pending', 'approved', 'rejected', 'suspended'));

ALTER TABLE products ADD COLUMN IF NOT EXISTS rejection_reason text NOT NULL DEFAULT '';
ALTER TABLE products ADD COLUMN IF NOT EXISTS submitted_at timestamptz;
ALTER TABLE products ADD COLUMN IF NOT EXISTS reviewed_at timestamptz;
ALTER TABLE products ADD COLUMN IF NOT EXISTS reviewed_by uuid REFERENCES users (id) ON DELETE SET NULL;
ALTER TABLE products ADD COLUMN IF NOT EXISTS view_count integer NOT NULL DEFAULT 0;

-- Everything already in the catalog when this migration runs was seeded/trusted.
UPDATE products SET status = 'approved', submitted_at = COALESCE(submitted_at, created_at),
                    reviewed_at = COALESCE(reviewed_at, created_at)
 WHERE status = 'approved' AND submitted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_products_status ON products (status);
CREATE INDEX IF NOT EXISTS idx_products_status_created ON products (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_products_seller_status ON products (seller_id, status);

-- Moderation audit trail — every decision is recorded, never overwritten.
CREATE TABLE IF NOT EXISTS product_reviews (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id uuid NOT NULL REFERENCES products (id) ON DELETE CASCADE,
  admin_id   uuid REFERENCES users (id) ON DELETE SET NULL,
  action     text NOT NULL CHECK (action IN ('submitted', 'approved', 'rejected', 'suspended', 'reinstated')),
  reason     text NOT NULL DEFAULT '',
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_product_reviews_product ON product_reviews (product_id, created_at DESC);

-- ── 2. Favourites (buyer follows seller) ────────────────────────────────────
CREATE TABLE IF NOT EXISTS favorite_sellers (
  user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  seller_id  uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, seller_id)
);
CREATE INDEX IF NOT EXISTS idx_favorite_sellers_seller ON favorite_sellers (seller_id);

-- ── 3. Device tokens for real push ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS device_tokens (
  token      text PRIMARY KEY,
  user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  platform   text NOT NULL DEFAULT 'android' CHECK (platform IN ('android', 'ios', 'web')),
  created_at timestamptz NOT NULL DEFAULT now(),
  last_seen  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_device_tokens_user ON device_tokens (user_id);

-- Notifications gain a deep-link payload so a tap opens the right screen.
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS data jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS image_url text NOT NULL DEFAULT '';
CREATE INDEX IF NOT EXISTS idx_notifications_user_created ON notifications (user_id, created_at DESC);

-- ── 4. Live seller location ─────────────────────────────────────────────────
-- location_sharing=false keeps the LAST known lat/lng on the map (sticky pin).
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS location_sharing boolean NOT NULL DEFAULT false;
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS location_updated_at timestamptz;
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS last_lat double precision;
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS last_lng double precision;
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS is_open boolean NOT NULL DEFAULT true;

-- Backfill the sticky pin from the configured store coordinates.
UPDATE store_settings
   SET last_lat = COALESCE(last_lat, lat),
       last_lng = COALESCE(last_lng, lng)
 WHERE lat IS NOT NULL AND lng IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_store_settings_live ON store_settings (location_sharing)
  WHERE location_sharing = true;

-- ── 5. Search acceleration ──────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_products_title_trgm ON products USING gin (title gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_products_brand_trgm ON products USING gin (brand gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_products_fts ON products
  USING gin (to_tsvector('english', title || ' ' || description || ' ' || category || ' ' || brand));
CREATE INDEX IF NOT EXISTS idx_products_price ON products (price_minor);

-- Recent buyer searches power "continue where you left off" + AI personalisation.
CREATE TABLE IF NOT EXISTS search_history (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  query      text NOT NULL,
  mode       text NOT NULL DEFAULT 'text' CHECK (mode IN ('text', 'ai', 'image', 'voice')),
  results    integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_search_history_user ON search_history (user_id, created_at DESC);

-- ── 6. Support threads (admin mode + AI mode) ───────────────────────────────
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS mode text NOT NULL DEFAULT 'admin';
ALTER TABLE support_tickets DROP CONSTRAINT IF EXISTS support_tickets_mode_check;
ALTER TABLE support_tickets ADD CONSTRAINT support_tickets_mode_check CHECK (mode IN ('admin', 'ai'));
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS support_replies (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  ticket_id  uuid NOT NULL REFERENCES support_tickets (id) ON DELETE CASCADE,
  author_id  uuid REFERENCES users (id) ON DELETE SET NULL,
  author_role text NOT NULL DEFAULT 'user' CHECK (author_role IN ('user', 'admin', 'ai')),
  body       text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_support_replies_ticket ON support_replies (ticket_id, created_at ASC);

-- ── 7. Product reviews by buyers (ratings that actually come from orders) ───
CREATE TABLE IF NOT EXISTS product_ratings (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id uuid NOT NULL REFERENCES products (id) ON DELETE CASCADE,
  user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  stars      integer NOT NULL CHECK (stars BETWEEN 1 AND 5),
  comment    text NOT NULL DEFAULT '',
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (product_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_product_ratings_product ON product_ratings (product_id);

-- ── 8. Cart (the "best way to acquire products") ────────────────────────────
CREATE TABLE IF NOT EXISTS cart_items (
  user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  product_id uuid NOT NULL REFERENCES products (id) ON DELETE CASCADE,
  quantity   integer NOT NULL DEFAULT 1 CHECK (quantity > 0),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, product_id)
);
CREATE INDEX IF NOT EXISTS idx_cart_items_user ON cart_items (user_id);
