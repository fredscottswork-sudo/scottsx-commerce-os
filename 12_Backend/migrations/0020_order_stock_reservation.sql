-- 0020_order_stock_reservation.sql — make inventory reservations explicit.
--
-- A COD cart checkout decrements stock immediately. Gateway checkout used to
-- wait until its webhook, allowing two pending payments to claim the same
-- unit. This flag makes the reservation idempotent and lets failed gateway
-- payments return the units exactly once.

ALTER TABLE orders ADD COLUMN IF NOT EXISTS stock_reserved boolean NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_orders_stock_reserved ON orders (stock_reserved) WHERE stock_reserved = true;
