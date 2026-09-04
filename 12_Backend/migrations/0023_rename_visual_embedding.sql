-- Rename visual_embedding -> visual_search_embedding to match the Roboflow
-- workflow's output name exactly (the spec's field is visual_search_embedding).
-- Applies to databases migrated before 0023; fresh databases get the right
-- name straight from 0022_vision_ai.sql.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'products' AND column_name = 'visual_embedding'
  ) THEN
    ALTER TABLE products RENAME COLUMN visual_embedding TO visual_search_embedding;
  END IF;
END $$;
