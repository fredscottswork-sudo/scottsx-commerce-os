/**
 * The platform admin allow-list.
 *
 * Only these addresses may hold (or be granted) the admin role. Anyone else
 * who somehow has role='admin' in the database is treated as a buyer. Set
 * ADMIN_EMAILS (comma-separated) to override; the default is the company
 * address.
 */
const DEFAULT_ADMINS = ['scottstechx@gmail.com'];

export function adminEmails(): string[] {
  const raw = process.env.ADMIN_EMAILS || process.env.ADMIN_EMAIL || '';
  const list = raw.split(',').map((e) => e.trim().toLowerCase()).filter(Boolean);
  return list.length ? list : DEFAULT_ADMINS;
}

export function isAdminEmail(email: string | null | undefined): boolean {
  return !!email && adminEmails().includes(email.trim().toLowerCase());
}

/** Role a user should effectively have given their email. */
export function effectiveRole(email: string, storedRole: string): 'admin' | 'buyer' | 'seller' {
  if (isAdminEmail(email)) return 'admin';
  if (storedRole === 'admin') return 'buyer';
  return storedRole === 'seller' ? 'seller' : 'buyer';
}
