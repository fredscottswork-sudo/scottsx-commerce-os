-- 0009_indexes.sql — extra indexes for the hot query paths

CREATE INDEX IF NOT EXISTS idx_messages_sender ON messages (sender_id);
CREATE INDEX IF NOT EXISTS idx_conversations_last_time ON conversations (last_time DESC);
CREATE INDEX IF NOT EXISTS idx_products_created ON products (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_store_settings_verified ON store_settings (verified) WHERE verified = true;
