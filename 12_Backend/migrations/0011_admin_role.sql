-- 0011_admin_role.sql — platform admin role for the web console.
-- Additive: extends the role check without touching existing rows.

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('buyer', 'seller', 'admin'));
