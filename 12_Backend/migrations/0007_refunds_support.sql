-- 0007_refunds_support.sql — refunds, support tickets, FAQs, notifications

CREATE TABLE IF NOT EXISTS refund_claims (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  order_id   uuid NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
  reason     text NOT NULL DEFAULT '',
  status     text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected', 'paid')),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_refunds_user ON refund_claims (user_id);

CREATE TABLE IF NOT EXISTS support_tickets (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  subject    text NOT NULL DEFAULT '',
  message    text NOT NULL DEFAULT '',
  status     text NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'answered', 'closed')),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_support_tickets_user ON support_tickets (user_id);

CREATE TABLE IF NOT EXISTS faqs (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  question   text NOT NULL,
  answer     text NOT NULL,
  category   text NOT NULL DEFAULT 'General',
  sort_order integer NOT NULL DEFAULT 0
);

INSERT INTO faqs (question, answer, category, sort_order) VALUES
  ('How do I become a seller on ScottsTechX?', 'Open your profile, tap "Become a seller", verify your email, and set up your store profile. You can then list products and chat with buyers directly.', 'Selling', 1),
  ('How do I pay for an order?', 'ScottsTechX does not process payments itself. Agree the method with the seller in chat — most orders are cash on delivery, a bank transfer to the seller, or collection at pickup.', 'Payments', 2),
  ('How is delivery handled?', 'Delivery is arranged between buyer and seller. Each store lists its delivery fee and free-delivery threshold on its store page.', 'Delivery', 3),
  ('Can I return a product?', 'Yes — most stores offer returns within 7 days. Check the store policies or contact support to open a refund claim.', 'Returns', 4),
  ('Is ScottsTechX available outside Kampala?', 'Yes. Sellers operate across Kampala, Entebbe, Jinja, Mbarara, Gulu and Mbale. Use the Nearby tab to find stores around you.', 'General', 5)
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS notifications (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  title      text NOT NULL DEFAULT '',
  body       text NOT NULL DEFAULT '',
  type       text NOT NULL DEFAULT 'general',
  read       boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications (user_id, read);
