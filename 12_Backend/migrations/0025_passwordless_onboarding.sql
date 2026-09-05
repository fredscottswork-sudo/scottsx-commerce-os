-- Passwordless sign-in + onboarding.
--
-- role_chosen: a brand-new account (email code or first Google sign-in) has
-- not picked buyer/seller yet; the web sends it through onboarding first.
-- Existing users keep their role and skip it.
ALTER TABLE users ADD COLUMN IF NOT EXISTS role_chosen boolean NOT NULL DEFAULT true;
ALTER TABLE users ALTER COLUMN role_chosen SET DEFAULT false;
-- Login codes live in email_verifications with purpose = 'login'; the code is
-- looked up by email hash so it works before the account has a session.
ALTER TABLE email_verifications ADD COLUMN IF NOT EXISTS email text;
CREATE INDEX IF NOT EXISTS email_verifications_email_idx ON email_verifications (email, purpose, consumed_at);
