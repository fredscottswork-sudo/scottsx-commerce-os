-- 0014 — resolved place for a user's last known position.
--
-- The Nearby screen (app + web) now detects the buyer's position itself and
-- shows it as "village, city, region, country". Storing the resolved parts
-- lets other surfaces (checkout addresses, seller cards, support tickets)
-- reuse the same human-readable location without re-deriving it, and lets a
-- returning user see their location before GPS has produced a fresh fix.
--
-- `users.city` already exists and stays the user-editable free-text field;
-- these columns hold what the geocoder derived from real coordinates.

ALTER TABLE users ADD COLUMN IF NOT EXISTS village          text;
ALTER TABLE users ADD COLUMN IF NOT EXISTS region           text;
ALTER TABLE users ADD COLUMN IF NOT EXISTS country          text;
ALTER TABLE users ADD COLUMN IF NOT EXISTS country_code     text;
ALTER TABLE users ADD COLUMN IF NOT EXISTS place_label      text;
ALTER TABLE users ADD COLUMN IF NOT EXISTS location_updated_at timestamptz;

-- Same for stores: a seller's card can then read "Kabalagala, Kampala".
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS village      text;
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS region       text;
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS country      text;
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS country_code text;
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS place_label  text;
