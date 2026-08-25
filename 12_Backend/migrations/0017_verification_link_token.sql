-- Verification by LINK.
--
-- Verification originally worked only by six-digit code: the user read the
-- code out of the email and typed it back into the site. That is the fallback,
-- not the thing people expect - every mainstream marketplace sends a link you
-- click once.
--
-- A link needs a secret that can travel in a URL. The six-digit code cannot:
-- it is short enough to brute force when it is not rate limited behind a
-- typing UI, and it is deliberately low-entropy for human transcription. So
-- each verification row gains a separate high-entropy token.
--
-- Only the SHA-256 of the token is stored, exactly as for the code: the token
-- is a bearer credential - anyone holding it can verify the address - so a
-- leaked database must not hand out working links.
--
-- Nullable because rows written before this migration have no token, and
-- because a token is generated per issue rather than backfilled. Those old
-- rows simply keep working as code-only rows until they expire (15 minutes).

ALTER TABLE email_verifications
  ADD COLUMN IF NOT EXISTS token_hash text;

-- The link lookup is by token alone (the visitor is not signed in when they
-- click it, so there is no user_id to narrow by). Without an index that is a
-- full scan on every click.
CREATE INDEX IF NOT EXISTS email_verifications_token_idx
  ON email_verifications (token_hash);
