-- Email verification codes.
--
-- Registration previously inserted users with email_verified = true, so any
-- address at all — including ones that do not exist — became a full account.
-- A code is now mailed (or, with no SMTP configured, logged and returned in
-- dev) and the account stays unverified until it is confirmed.

CREATE TABLE IF NOT EXISTS email_verifications (
  id           bigserial PRIMARY KEY,
  user_id      uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  -- Store only a hash: a leaked table must not hand out working codes.
  code_hash    text NOT NULL,
  purpose      text NOT NULL DEFAULT 'signup',
  attempts     int  NOT NULL DEFAULT 0,
  expires_at   timestamptz NOT NULL,
  consumed_at  timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS email_verifications_user_idx
  ON email_verifications (user_id, purpose, consumed_at);

-- Existing accounts (seed data, current users) keep their access.
UPDATE users SET email_verified = true WHERE email_verified IS NULL;
