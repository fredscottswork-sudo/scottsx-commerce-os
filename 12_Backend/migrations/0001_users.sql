-- 0001_users.sql — users table (both roles live here; role distinguishes them)

CREATE TABLE IF NOT EXISTS users (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email             text NOT NULL UNIQUE,
  password_hash     text,
  role              text NOT NULL DEFAULT 'buyer' CHECK (role IN ('buyer', 'seller')),
  display_name      text NOT NULL DEFAULT '',
  phone             text NOT NULL DEFAULT '',
  email_verified    boolean NOT NULL DEFAULT false,
  firebase_uid      text UNIQUE,
  google_uid        text UNIQUE,
  profile_photo_url text,
  lat               double precision,
  lng               double precision,
  city              text NOT NULL DEFAULT '',
  created_at        timestamptz NOT NULL DEFAULT now(),
  updated_at        timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);
CREATE INDEX IF NOT EXISTS idx_users_firebase_uid ON users (firebase_uid);
CREATE INDEX IF NOT EXISTS idx_users_role ON users (role);
