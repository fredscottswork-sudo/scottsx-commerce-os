-- Seller-uploaded images.
--
-- Sellers work from a phone, where the photo lives in the camera roll and there
-- is no URL to paste. Uploads therefore have to be first-class.
--
-- The bytes live in Postgres rather than on disk because the deploy target
-- (Render/Cloud Run) has an ephemeral filesystem — anything written next to the
-- process disappears on the next deploy, silently breaking every listing photo.
-- Firebase Storage is still used when it is configured; this is the fallback
-- that makes uploads work with no configuration at all.
--
-- Images are small (validated and re-encoded to <= 512 KB before insert) and
-- served with a long-lived immutable cache header keyed by content hash, so the
-- database is read once per image per client.

CREATE TABLE IF NOT EXISTS uploaded_images (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id     uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  -- sha256 of the bytes: identical re-uploads collapse onto one row.
  sha256       text NOT NULL,
  mime_type    text NOT NULL,
  byte_size    integer NOT NULL,
  width        integer,
  height       integer,
  data         bytea NOT NULL,
  purpose      text NOT NULL DEFAULT 'product',
  created_at   timestamptz NOT NULL DEFAULT now()
);

-- One stored copy per owner per identical image.
CREATE UNIQUE INDEX IF NOT EXISTS uploaded_images_owner_sha_idx
  ON uploaded_images (owner_id, sha256);

CREATE INDEX IF NOT EXISTS uploaded_images_owner_idx
  ON uploaded_images (owner_id, created_at DESC);
