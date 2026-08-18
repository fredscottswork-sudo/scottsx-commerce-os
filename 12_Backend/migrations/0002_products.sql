-- 0002_products.sql — products + product_media

CREATE TABLE IF NOT EXISTS products (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  seller_id        uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  title            text NOT NULL,
  description      text NOT NULL DEFAULT '',
  category         text NOT NULL DEFAULT 'Other',
  brand            text NOT NULL DEFAULT '',
  price_minor      bigint NOT NULL DEFAULT 0,
  old_price_minor  bigint,
  stock_quantity   integer NOT NULL DEFAULT 1,
  image_url        text NOT NULL DEFAULT '',
  rating           numeric(3,2) NOT NULL DEFAULT 0,
  rating_count     integer NOT NULL DEFAULT 0,
  is_flash_deal    boolean NOT NULL DEFAULT false,
  discount_percent integer NOT NULL DEFAULT 0,
  location         text NOT NULL DEFAULT '',
  created_at       timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_products_seller ON products (seller_id);
CREATE INDEX IF NOT EXISTS idx_products_category ON products (category);
CREATE INDEX IF NOT EXISTS idx_products_flash ON products (is_flash_deal) WHERE is_flash_deal = true;

CREATE TABLE IF NOT EXISTS product_media (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id  uuid NOT NULL REFERENCES products (id) ON DELETE CASCADE,
  url         text NOT NULL,
  sort_order  integer NOT NULL DEFAULT 0,
  created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_product_media_product ON product_media (product_id);
