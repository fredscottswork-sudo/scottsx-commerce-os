-- 0005_user_data.sql — addresses, payment methods, bookmarks, preferences, saved locations

CREATE TABLE IF NOT EXISTS addresses (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  label      text NOT NULL DEFAULT 'Home',
  line1      text NOT NULL DEFAULT '',
  city       text NOT NULL DEFAULT '',
  country    text NOT NULL DEFAULT 'Uganda',
  is_default boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_addresses_user ON addresses (user_id);

CREATE TABLE IF NOT EXISTS bookmarks (
  user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  product_id uuid NOT NULL REFERENCES products (id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_bookmarks_user ON bookmarks (user_id);

CREATE TABLE IF NOT EXISTS user_settings (
  user_id             uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
  theme               text NOT NULL DEFAULT 'system' CHECK (theme IN ('light', 'dark', 'system')),
  language            text NOT NULL DEFAULT 'en',
  currency            text NOT NULL DEFAULT 'UGX',
  notify_order_updates boolean NOT NULL DEFAULT true,
  notify_messages     boolean NOT NULL DEFAULT true,
  notify_marketing    boolean NOT NULL DEFAULT false,
  updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS user_locations (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  label      text NOT NULL DEFAULT 'My location',
  lat        double precision NOT NULL,
  lng        double precision NOT NULL,
  city       text NOT NULL DEFAULT '',
  is_default boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_user_locations_user ON user_locations (user_id);
