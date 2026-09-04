-- 0021_remove_payments.sql — the marketplace has no payment processing.
--
-- Online payment was removed entirely: no Stripe, no Nylon Pay, no saved
-- payment methods, no seller mobile-money / bank details. Orders are
-- messaging-first inquiries (cash on delivery / agreed in chat), so the
-- payment tracking columns on orders are gone too.

DROP TABLE IF EXISTS payment_methods;

ALTER TABLE store_settings
  DROP COLUMN IF EXISTS momo_number,
  DROP COLUMN IF EXISTS bank_name,
  DROP COLUMN IF EXISTS bank_account;

ALTER TABLE orders
  DROP COLUMN IF EXISTS payment_provider,
  DROP COLUMN IF EXISTS payment_reference,
  DROP COLUMN IF EXISTS payment_link,
  DROP COLUMN IF EXISTS nylon_transaction_id;

DROP INDEX IF EXISTS idx_payment_methods_user;
DROP INDEX IF EXISTS idx_orders_payment_reference;
