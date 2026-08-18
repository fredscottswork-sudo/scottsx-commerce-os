-- 0004_conversations.sql — messaging: conversations, messages, read receipts

CREATE TABLE IF NOT EXISTS conversations (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  buyer_id      uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  seller_id     uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  product_id    uuid REFERENCES products (id) ON DELETE SET NULL,
  last_message  text NOT NULL DEFAULT '',
  last_time     timestamptz,
  created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_conversations_buyer ON conversations (buyer_id);
CREATE INDEX IF NOT EXISTS idx_conversations_seller ON conversations (seller_id);

CREATE TABLE IF NOT EXISTS messages (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  conversation_id uuid NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
  sender_id       uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  text            text NOT NULL DEFAULT '',
  image_url       text,
  created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_messages_conversation ON messages (conversation_id, created_at);

-- Read receipts: last time each participant read the thread.
CREATE TABLE IF NOT EXISTS message_reads (
  conversation_id uuid NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
  user_id         uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  last_read_at    timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (conversation_id, user_id)
);
