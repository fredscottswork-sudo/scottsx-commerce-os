-- 0010_orders_payments.sql — payment tracking columns for orders (Nylon Pay)

ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_provider text NOT NULL DEFAULT 'nylonpay';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_reference text;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_link text;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS nylon_transaction_id text;

CREATE INDEX IF NOT EXISTS idx_orders_payment_reference ON orders (payment_reference);
