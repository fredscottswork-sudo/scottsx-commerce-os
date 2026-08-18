import { useEffect, useState } from 'react';
import { adminService } from '../../api/services';
import type { AdminUserRow } from '../../api/types';
import { useToast } from '../../store/ToastContext';
import { Btn, Card, Empty, ErrorBox, Loading, PageHeader, Pagination, SearchInput, Select, Table } from '../../components/ui';

export default function AdminUsers() {
  const { toast } = useToast();
  const [items, setItems] = useState<AdminUserRow[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState('');
  const [role, setRole] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  async function load(p = page) {
    setLoading(true);
    adminService.users({ search, role: role || undefined, page: p, pageSize: 25 })
      .then((r) => { setItems(r.items); setTotal(r.total); setPage(r.page); })
      .catch((e: any) => setError(e.message))
      .finally(() => setLoading(false));
  }
  useEffect(() => { load(1); /* eslint-disable-next-line */ }, [search, role]);

  async function changeRole(id: string, newRole: string) {
    try {
      await adminService.setRole(id, newRole);
      toast(`Role → ${newRole}`, 'success');
      load();
    } catch (e: any) {
      toast(e.message, 'error');
    }
  }

  return (
    <>
      <PageHeader title="Users" sub={`${total} account(s) in the shared backend.`} />
      <div className="row wrap mb-16">
        <SearchInput value={search} onChange={setSearch} placeholder="Search name, email, phone…" />
        <Select value={role} onChange={(e) => setRole(e.target.value)} style={{ width: 160 }}>
          <option value="">All roles</option>
          <option value="buyer">Buyer</option>
          <option value="seller">Seller</option>
          <option value="admin">Admin</option>
        </Select>
      </div>

      {loading ? <Loading /> : error ? <ErrorBox message={error} onRetry={() => load()} /> :
        items.length === 0 ? <Empty emoji="👥" title="No users found" /> : (
        <Card style={{ padding: 0 }}>
          <Table head={['Name', 'Email', 'Phone', 'Role', 'Verified', 'Change role', 'Joined']}>
            {items.map((u) => (
              <tr key={u.id}>
                <td><strong>{u.displayName || '—'}</strong></td>
                <td>{u.email}</td>
                <td>{u.phone || '—'}</td>
                <td><span className="badge badge-blue" style={{ textTransform: 'capitalize' }}>{u.role}</span></td>
                <td>{u.emailVerified ? <span className="badge badge-green">Verified</span> : <span className="badge badge-gray">No</span>}</td>
                <td>
                  <Select value={u.role} onChange={(e) => changeRole(u.id, e.target.value)} style={{ width: 110 }}>
                    <option value="buyer">buyer</option>
                    <option value="seller">seller</option>
                    <option value="admin">admin</option>
                  </Select>
                </td>
                <td>{new Date(u.createdAt).toLocaleDateString()}</td>
              </tr>
            ))}
          </Table>
        </Card>
      )}
      <Pagination page={page} pageSize={25} total={total} onPage={(p) => load(p)} />
    </>
  );
}
