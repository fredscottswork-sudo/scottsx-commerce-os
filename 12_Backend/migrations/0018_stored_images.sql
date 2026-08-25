-- 0018_stored_images.sql — self-hosted image storage (Firebase-less fallback)
--
-- Sellers upload product photos from their phone. When the deployment has
-- Firebase Storage configured the bytes go there; otherwise they land here so
-- the upload flow works on a bare Postgres deployment too.
--
-- `key` is "{userId}-{timestamp}-{16 random base64url chars}.{ext}" — the
-- random part is what makes the key unguessable, which is what allows the
-- GET endpoint to be public (product images must be visible to signed-out
-- buyers) without exposing anything else.

CREATE TABLE IF NOT EXISTS stored_images (
  key          text PRIMARY KEY,
  data         bytea NOT NULL,
  content_type text NOT NULL,
  uploader_id  uuid REFERENCES users(id) ON DELETE SET NULL,
  created_at   timestamptz NOT NULL DEFAULT now()
);
