-- Seeded / pre-existing sellers were inserted after 0025 with the new default
-- role_chosen=false and got bounced to onboarding. A seller with a store row
-- has plainly chosen already.
UPDATE users u SET role_chosen = true
 WHERE role_chosen = false AND (u.role = 'admin' OR EXISTS (SELECT 1 FROM store_settings s WHERE s.user_id = u.id));
