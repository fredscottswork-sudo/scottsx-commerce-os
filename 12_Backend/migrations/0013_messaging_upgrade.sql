-- ScottsTechX 0013 — messaging upgrade.
--
-- Brings the chat system up to (and past) the Alibaba/Jiji feature bar:
--   * rich message kinds: text, image, offer, system
--   * price offers negotiated inside the thread (accept / decline / counter)
--   * per-message delivery + read receipts
--   * typing indicators
--   * per-user pin / archive / mute state on a conversation
--   * soft delete (retract) of a sent message
--
-- Every statement is written to be idempotent so the runner can re-apply it.

-- --------------------------------------------------------------------------
-- messages: kind + offer payload + retraction
-- --------------------------------------------------------------------------
ALTER TABLE messages ADD COLUMN IF NOT EXISTS kind text NOT NULL DEFAULT 'text';
ALTER TABLE messages ADD COLUMN IF NOT EXISTS attachment_name text;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS product_id uuid REFERENCES products(id) ON DELETE SET NULL;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS offer_minor bigint;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS offer_status text;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS offer_quantity integer NOT NULL DEFAULT 1;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS reply_to_id uuid REFERENCES messages(id) ON DELETE SET NULL;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS deleted_at timestamptz;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS delivered_at timestamptz NOT NULL DEFAULT now();

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'messages_kind_check') THEN
    ALTER TABLE messages ADD CONSTRAINT messages_kind_check
      CHECK (kind IN ('text', 'image', 'offer', 'system'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'messages_offer_status_check') THEN
    ALTER TABLE messages ADD CONSTRAINT messages_offer_status_check
      CHECK (offer_status IS NULL OR offer_status IN ('pending', 'accepted', 'declined', 'countered', 'withdrawn'));
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_messages_conversation_created
  ON messages (conversation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_offer_pending
  ON messages (conversation_id) WHERE offer_status = 'pending';

-- --------------------------------------------------------------------------
-- conversations: subject/product context + activity bookkeeping
-- --------------------------------------------------------------------------
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS last_sender_id uuid REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS message_count integer NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_conversations_buyer_time ON conversations (buyer_id, last_time DESC);
CREATE INDEX IF NOT EXISTS idx_conversations_seller_time ON conversations (seller_id, last_time DESC);

-- Backfill counters for threads that already exist.
UPDATE conversations c
SET message_count = sub.n
FROM (SELECT conversation_id, COUNT(*)::int AS n FROM messages GROUP BY conversation_id) sub
WHERE sub.conversation_id = c.id AND c.message_count <> sub.n;

UPDATE conversations c
SET last_sender_id = sub.sender_id
FROM (
  SELECT DISTINCT ON (conversation_id) conversation_id, sender_id
  FROM messages ORDER BY conversation_id, created_at DESC
) sub
WHERE sub.conversation_id = c.id AND c.last_sender_id IS DISTINCT FROM sub.sender_id;

-- --------------------------------------------------------------------------
-- Per-user conversation state (pin / archive / mute)
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS conversation_state (
  conversation_id uuid NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  user_id         uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  pinned          boolean NOT NULL DEFAULT false,
  archived        boolean NOT NULL DEFAULT false,
  muted           boolean NOT NULL DEFAULT false,
  updated_at      timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (conversation_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_conversation_state_user ON conversation_state (user_id);

-- --------------------------------------------------------------------------
-- Typing indicators — tiny, hot table; rows are overwritten in place.
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS typing_state (
  conversation_id uuid NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  user_id         uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  typing_at       timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (conversation_id, user_id)
);

-- --------------------------------------------------------------------------
-- Per-message read receipts: who has seen which message.
-- --------------------------------------------------------------------------
ALTER TABLE message_reads ADD COLUMN IF NOT EXISTS last_read_message_id uuid REFERENCES messages(id) ON DELETE SET NULL;

-- --------------------------------------------------------------------------
-- Saved quick replies (canned responses) — sellers answer FAQs in one tap.
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS quick_replies (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  text       text NOT NULL,
  sort_order integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_quick_replies_user ON quick_replies (user_id, sort_order);
