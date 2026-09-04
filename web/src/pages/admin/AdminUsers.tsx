import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Users, ShieldCheck, BadgeCheck, Store, Mail, Phone, Trash2 } from 'lucide-react';
import { adminService } from '../../api/services';
import type { AdminUserRow } from '../../api/types';
import { useToast } from '../../store/ToastContext';
import {
  Btn, Empty, ErrorBox, PageHeader, Pagination, SearchInput, Select, Table,
  SkeletonRows, Badge, Avatar, ConfirmModal,
} from '../../components/ui';

const PAGE_SIZE = 25;

export default function AdminUsers() {
  const { toast } = useToast();
  const [params, setParams] = useSearchParams();

  const [items, setItems] = useState<AdminUserRow[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState('');
  const [role, setRole] = useState(params.get('role') ?? '');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState('');
  const [promoting, setPromoting] = useState<{ user: AdminUserRow; role: string } | null>(null);
  const [deleting, setDeleting] = useState<AdminUserRow | null>(null);

  const load = useCallback(async (p = 1) => {
    setLoading(true);
    setError('');
    try {
      const r = await adminService.users({
        search: search || undefined,
        role: role || undefined,
        page: p,
        pageSize: PAGE_SIZE,
      });
      setItems(r.users);
      setTotal(r.total);
      setPage(r.page);
    } catch (e: any) {
      setError(e?.message || 'Could not load users');
    } finally {
      setLoading(false);
    }
  }, [search, role]);

  useEffect(() => {
    const t = setTimeout(() => void load(1), search ? 280 : 0);
    return () => clearTimeout(t);
  }, [load, search]);

  const changeRole = async () => {
    if (!promoting) return;
    setBusyId(promoting.user.id);
    try {
      await adminService.setRole(promoting.user.id, promoting.role);
      toast(`${promoting.user.displayName || promoting.user.email} is now a ${promoting.role}`, 'success');
      setPromoting(null);
      await load(page);
    } catch (e: any) {
      // The backend blocks self-demotion and removing the last admin.
      toast(e?.message || 'Could not change the role', 'error');
      setPromoting(null);
    } finally {
      setBusyId('');
    }
  };

  const removeUser = async () => {
    if (!deleting) return;
    setBusyId(deleting.id);
    try {
      await adminService.deleteUser(deleting.id);
      toast(`${deleting.email} removed`, 'success');
      setDeleting(null);
      await load(page);
    } catch (e: any) {
      // 409 = seller still has live listings; 403 = self / last admin.
      toast(e?.message || 'Could not delete this account', 'error');
      setDeleting(null);
    } finally {
      setBusyId('');
    }
  };

  const verifySeller = async (u: AdminUserRow, verified: boolean) => {
    setBusyId(u.id);
    try {
      await adminService.verifySeller(u.id, verified);
      toast(verified ? 'Seller verified — badge is now public' : 'Verification removed', 'success');
    } catch (e: any) {
      toast(e?.message || 'Could not update verification', 'error');
    } finally {
      setBusyId('');
    }
  };

  return (
    <>
      <PageHeader
        title="Users"
        sub="Everyone on the platform. Promote sellers, grant admin access, and verify trusted stores."
      />

      <div className="row wrap mb-16" style={{ gap: 10 }}>
        <div style={{ flex: '1 1 280px', maxWidth: 380 }}>
          <SearchInput value={search} onChange={setSearch} placeholder="Search name, email or phone…" />
        </div>
        <Select
          aria-label="Filter by role"
          value={role}
          style={{ width: 'auto' }}
          onChange={(e) => {
            setRole(e.target.value);
            const p = new URLSearchParams(params);
            e.target.value ? p.set('role', e.target.value) : p.delete('role');
            setParams(p, { replace: true });
          }}
        >
          <option value="">All roles</option>
          <option value="buyer">Buyers</option>
          <option value="seller">Sellers</option>
          <option value="admin">Admins</option>
        </Select>
        <span className="tiny muted" style={{ alignSelf: 'center' }}>{total.toLocaleString()} user{total === 1 ? '' : 's'}</span>
      </div>

      {loading ? (
        <SkeletonRows rows={8} height={56} />
      ) : error ? (
        <ErrorBox message={error} onRetry={() => load(page)} />
      ) : items.length === 0 ? (
        <Empty icon={<Users size={28} />} title="No users found"
          subtitle={search ? 'Try a different search term.' : 'No users match this filter.'} />
      ) : (
        <>
          <Table<AdminUserRow>
            rows={items}
            keyOf={(u) => u.id}
            columns={[
              {
                key: 'user', header: 'User',
                render: (u) => (
                  <div className="row" style={{ gap: 10 }}>
                    <Avatar name={u.displayName || u.email} />
                    <div style={{ minWidth: 0 }}>
                      <div className="semi ellipsis">{u.displayName || '—'}</div>
                      <div className="tiny muted ellipsis"><Mail size={10} style={{ verticalAlign: -1 }} /> {u.email}</div>
                      {u.phone && <div className="tiny muted-2"><Phone size={10} style={{ verticalAlign: -1 }} /> {u.phone}</div>}
                    </div>
                  </div>
                ),
              },
              {
                key: 'role', header: 'Role',
                render: (u) => (
                  <Badge tone={u.role === 'admin' ? 'violet' : u.role === 'seller' ? 'cyan' : 'default'}>{u.role}</Badge>
                ),
              },
              {
                key: 'status', header: 'Email', hideSm: true,
                render: (u) => u.emailVerified ? <Badge tone="green">verified</Badge> : <Badge tone="amber">unverified</Badge>,
              },
              { key: 'city', header: 'City', hideSm: true, render: (u) => <span className="tiny muted">{u.city || '—'}</span> },
              {
                key: 'joined', header: 'Joined', hideSm: true,
                render: (u) => <span className="tiny muted">{new Date(u.createdAt).toLocaleDateString()}</span>,
              },
              {
                key: 'actions', header: '',
                render: (u) => (
                  <div className="row" style={{ gap: 5, justifyContent: 'flex-end' }}>
                    {u.role === 'seller' && (
                      <>
                        <Link to={`/seller/${u.id}`} className="btn btn-sm" title="View storefront"><Store size={13} /></Link>
                        <Btn size="sm" loading={busyId === u.id} icon={<BadgeCheck size={13} />}
                          onClick={() => verifySeller(u, true)} title="Verify this seller">Verify</Btn>
                      </>
                    )}
                    <Select
                      value={u.role}
                      style={{ width: 'auto', padding: '5px 8px', fontSize: 'var(--fs-sm)' }}
                      onChange={(e) => setPromoting({ user: u, role: e.target.value })}
                      aria-label={`Change role for ${u.email}`}
                    >
                      <option value="buyer">buyer</option>
                      <option value="seller">seller</option>
                      <option value="admin">admin</option>
                    </Select>
                    <Btn size="sm" variant="ghost" icon={<Trash2 size={13} />}
                      onClick={() => setDeleting(u)} aria-label={`Delete ${u.email}`} />
                  </div>
                ),
              },
            ]}
          />
          <Pagination page={page} pageSize={PAGE_SIZE} total={total} onPage={(p) => void load(p)} />
        </>
      )}

      <ConfirmModal
        open={!!promoting}
        title="Change role"
        message={`Make ${promoting?.user.displayName || promoting?.user.email} a ${promoting?.role}? ${
          promoting?.role === 'admin' ? 'Admins can approve listings, moderate content and manage every user.' : ''
        }`}
        confirmLabel={`Make ${promoting?.role}`}
        danger={promoting?.role === 'admin'}
        loading={busyId === promoting?.user.id}
        onCancel={() => setPromoting(null)}
        onConfirm={changeRole}
      />

      <ConfirmModal
        open={!!deleting}
        title="Delete account"
        message={`Permanently delete ${deleting?.email}? Their orders, messages and saved items go with it. Sellers with live listings must be cleared first.`}
        confirmLabel="Delete account"
        danger
        loading={busyId === deleting?.id}
        onCancel={() => setDeleting(null)}
        onConfirm={removeUser}
      />
    </>
  );
}
