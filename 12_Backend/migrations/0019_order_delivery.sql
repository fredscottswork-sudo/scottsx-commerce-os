-- 0019_order_delivery.sql — preserve the delivery details chosen at checkout.
--
-- Checkout already collected these values in the web client, but the old
-- endpoint discarded them after creating the order. Snapshots are stored on
-- the order so deleting or editing a saved address cannot change an existing
-- delivery, and sellers can see what the buyer actually submitted.

ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_address text NOT NULL DEFAULT '';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_phone text NOT NULL DEFAULT '';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_note text NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS idx_orders_delivery_phone ON orders (delivery_phone);
