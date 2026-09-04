-- 0003_store_settings.sql — seller store settings (the 9 sections flatten into one row)

CREATE TABLE IF NOT EXISTS store_settings (
  user_id             uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,

  -- store-profile
  store_name          text NOT NULL DEFAULT '',
  store_description   text NOT NULL DEFAULT '',
  store_logo_url      text NOT NULL DEFAULT '',

  -- business-info
  legal_name          text NOT NULL DEFAULT '',
  tin                 text NOT NULL DEFAULT '',
  business_email      text NOT NULL DEFAULT '',
  business_phone      text NOT NULL DEFAULT '',

  -- store-location
  address             text NOT NULL DEFAULT '',
  pickup_instructions text NOT NULL DEFAULT '',
  service_radius_km   integer NOT NULL DEFAULT 20,
  lat                 double precision,
  lng                 double precision,
  city                text NOT NULL DEFAULT '',

  -- delivery
  delivery_fee_ugx    integer NOT NULL DEFAULT 0,
  free_above_ugx      integer NOT NULL DEFAULT 0,
  cod_enabled         boolean NOT NULL DEFAULT true,

  -- notifications
  notif_order_updates boolean NOT NULL DEFAULT true,
  notif_buyer_messages boolean NOT NULL DEFAULT true,
  notif_marketing     boolean NOT NULL DEFAULT false,
  notif_weekly_digest boolean NOT NULL DEFAULT true,

  -- security
  two_factor_enabled  boolean NOT NULL DEFAULT false,

  -- policies
  returns_window_days integer NOT NULL DEFAULT 7,
  refund_policy       text NOT NULL DEFAULT '',
  terms               text NOT NULL DEFAULT '',

  -- help
  contact_email       text NOT NULL DEFAULT '',
  contact_phone       text NOT NULL DEFAULT '',

  -- storefront facts (seeded / computed)
  verified            boolean NOT NULL DEFAULT false,
  rating              numeric(3,2) NOT NULL DEFAULT 0,

  updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_store_settings_city ON store_settings (city);
