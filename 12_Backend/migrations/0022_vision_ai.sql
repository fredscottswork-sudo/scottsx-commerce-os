-- ScottsTechX — Roboflow vision AI columns.
--
-- Every listing that carries a photo is run through the Roboflow moderation
-- workflow when ROBOFLOW_API_KEY is configured. The decision routes it:
--   approved            -> published immediately
--   manual_review       -> normal admin moderation queue
--   rejected            -> blocked, rejection_reasons shown to the seller
--   needs_better_image  -> blocked, seller asked for another photo
--
-- Metadata produced by the workflow (category/subcategory/title/tags) is
-- stored alongside so moderation and search can use it later. The
-- visual_search_embedding is stored as a JSON array (portable to any Postgres;
-- no pgvector extension required) and compared to query embeddings with cosine
-- similarity at query time.

ALTER TABLE products
  ADD COLUMN IF NOT EXISTS vision_decision        text,
  ADD COLUMN IF NOT EXISTS vision_rejection_reasons jsonb,
  ADD COLUMN IF NOT EXISTS vision_category        text,
  ADD COLUMN IF NOT EXISTS vision_subcategory     text,
  ADD COLUMN IF NOT EXISTS vision_title           text,
  ADD COLUMN IF NOT EXISTS vision_tags            jsonb,
  ADD COLUMN IF NOT EXISTS visual_search_embedding jsonb,
  ADD COLUMN IF NOT EXISTS vision_checked_at      timestamptz;

CREATE INDEX IF NOT EXISTS idx_products_vision_embedding
  ON products (id) WHERE visual_search_embedding IS NOT NULL;
