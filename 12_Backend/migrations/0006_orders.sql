-- 0006_orders.sql — orders + order_items (revenue/stats source for sellers)

CREATE TABLE IF NOT EXISTS orders (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  buyer_id       uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  seller_id      uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  product_id     uuid REFERENCES products (id) ON DELETE SET NULL,
  product_title  text NOT NULL DEFAULT '',
  price_minor    bigint NOT NULL DEFAULT 0,
  quantity       integer NOT NULL DEFAULT 1,
  status         text NOT NULL DEFAULT 'pending'
                 CHECK (status IN ('pending', 'paid', 'shipped', 'delivered', 'cancelled', 'refunded')),
  created_at     timestamptz NOT NULL DEFAULT now(),
  updated_at     timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_orders_buyer ON orders (buyer_id);
CREATE INDEX IF NOT EXISTS idx_orders_seller ON orders (seller_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders (status);

CREATE TABLE IF NOT EXISTS order_items (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id   uuid NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
  product_id uuid NOT NULL REFERENCES products (id) ON DELETE CASCADE,
  title      text NOT NULL DEFAULT '',
  price_minor bigint NOT NULL DEFAULT 0,
  quantity   integer NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items (order_id);
