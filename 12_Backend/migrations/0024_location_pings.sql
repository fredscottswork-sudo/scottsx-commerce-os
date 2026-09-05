-- Location pings: every position a signed-in user shares (Nearby screen,
-- live tracking). users.lat/lng keeps the LATEST; this table keeps history so
-- the admin map can show where people were and how the fix improved.
CREATE TABLE IF NOT EXISTS location_pings (
  id          bigserial PRIMARY KEY,
  user_id     uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  lat         double precision NOT NULL,
  lng         double precision NOT NULL,
  accuracy_m  integer,
  village     text,
  city        text,
  region      text,
  country     text,
  source      text NOT NULL DEFAULT 'nearby',
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_location_pings_user_time ON location_pings (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_location_pings_time ON location_pings (created_at DESC);
