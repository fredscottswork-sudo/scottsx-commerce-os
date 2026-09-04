-- 0021_user_location_confirmation.sql — separate GPS from village naming
-- Fix Nearby showing wrong village by storing GPS separately from human locality
-- and allowing user-confirmed villages that are never overwritten by geocoder.

-- Users: precise GPS + accuracy + confirmed locality
ALTER TABLE users ADD COLUMN IF NOT EXISTS gps_accuracy double precision;
ALTER TABLE users ADD COLUMN IF NOT EXISTS district text;
ALTER TABLE users ADD COLUMN IF NOT EXISTS village_source text CHECK (village_source IN ('gps_geocoder', 'user_confirmed', 'manual_search', 'offline_gazetteer', 'google')) DEFAULT 'gps_geocoder';
ALTER TABLE users ADD COLUMN IF NOT EXISTS village_confirmed boolean NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS neighbourhood text;
ALTER TABLE users ADD COLUMN IF NOT EXISTS suburb text;
ALTER TABLE users ADD COLUMN IF NOT EXISTS location_confidence double precision;
ALTER TABLE users ADD COLUMN IF NOT EXISTS location_alternatives jsonb;

-- Store settings: same separation for sellers if they want to confirm their store village
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS gps_accuracy double precision;
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS district text;
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS village_source text CHECK (village_source IN ('gps_geocoder', 'user_confirmed', 'manual_search', 'offline_gazetteer', 'google')) DEFAULT 'gps_geocoder';
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS village_confirmed boolean NOT NULL DEFAULT false;
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS neighbourhood text;
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS suburb text;
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS location_confidence double precision;

-- Index for confirmed locations
CREATE INDEX IF NOT EXISTS idx_users_village_confirmed ON users (village_confirmed) WHERE village_confirmed = true;
CREATE INDEX IF NOT EXISTS idx_users_gps ON users (lat, lng) WHERE lat IS NOT NULL AND lng IS NOT NULL;
