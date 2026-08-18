-- 0008_cms.sql — CMS pages (seeded with the founder bio in "about")

CREATE TABLE IF NOT EXISTS cms_pages (
  slug       text PRIMARY KEY,
  title      text NOT NULL,
  body       text NOT NULL DEFAULT '',
  updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO cms_pages (slug, title, body) VALUES
  ('terms', 'Terms of Service',
   E'Welcome to ScottsTechX, a Ugandan e-commerce marketplace.\n\n1. By using the app you agree to these terms.\n2. Buyers and sellers are responsible for the accuracy of the listings and information they share.\n3. Payments are processed through approved channels (Mobile Money, card, or cash on delivery where offered).\n4. ScottsTechX may suspend accounts that violate these terms.\n5. These terms may be updated from time to time; continued use means acceptance.\n\nFor questions, contact support.'),
  ('privacy', 'Privacy Policy',
   E'ScottsTechX respects your privacy.\n\nWe collect account information (name, email, phone), order history, saved addresses and payment method details to operate the marketplace.\n\nWe do not sell your personal data. Messages between buyers and sellers are used to fulfil orders and improve safety.\n\nYou may request deletion of your account and data at any time by contacting support.'),
  ('buyer-protection', 'Buyer Protection',
   E'ScottsTechX Buyer Protection helps you shop safely.\n\n• Verify sellers: check ratings, reviews and the verified badge.\n• Keep conversations inside the app so there is a record.\n• If an item does not arrive or is not as described, open a refund claim within 7 days.\n• Pay with Cash on Delivery where possible for extra safety.\n\nOur support team reviews every refund claim.'),
  ('about', 'About ScottsTechX',
   E'ScottsTechX is a Ugandan e-commerce marketplace connecting local sellers with buyers across Kampala, Entebbe, Jinja, Mbarara, Gulu and Mbale.\n\nFounded by Kato Fred, Ugandan cybersecurity analyst, web dev and software dev. ScottsTechX is built Ugandan-first: UGX pricing, Mobile Money, local delivery and real people on both sides of every sale.\n\nThe app ships with an AI assistant that answers questions from the live catalog, plus enterprise messaging with read receipts between buyers and sellers.')
ON CONFLICT (slug) DO UPDATE SET
  title = EXCLUDED.title,
  body = EXCLUDED.body,
  updated_at = now();
