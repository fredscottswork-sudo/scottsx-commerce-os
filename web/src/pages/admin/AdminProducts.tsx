import { useEffect, useState } from 'react';
import { Trash2 } from 'lucide-react';
import { adminService } from '../../api/services';
import type { AdminProductRow } from '../../api/types';
import { formatUgx } from '../../api/types';
import { useToast } from '../../store/ToastContext';
import { Btn, Card, Confirm, Empty, ErrorBox, Loading, PageHeader, Pagination, SearchInput, Table } from '../../components/ui';

export default function AdminProducts() {
  const { toast } = useToast();
  const [items, setItems] = useState<AdminProductRow[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [del, setDel] = useState<AdminProductRow | null>(null);

  async function load(p = page) {
    setLoading(true);
    adminService.products({ search, page: p, pageSize: 25 })
      .then((r) => { setItems(r.items); setTotal(r.total); setPage(r.page); })
      .catch((e: any) => setError(e.message))
      .finally(() => setLoading(false));
  }
  useEffect(() => { load(1); /* eslint-disable-next-line */ }, [search]);

  async function confirmDelete() {
    if (!del) return;
    try {
      await adminService.deleteProduct(del.id);
      toast('Product removed (moderation)', 'success');
      load();
    } catch (e: any) {
      toast(e.message, 'error');
    }
  }

  return (
    <>
      <PageHeader title="Products" sub={`${total} listing(s) — moderation from the admin API.`} />
      <SearchInput value={search} onChange={setSearch} placeholder="Search title, category, brand…" />
      <div className="mt-16">
      {loading ? <Loading /> : error ? <ErrorBox message={error} onRetry={() => load()} /> :
        items.length === 0 ? <Empty emoji="📦" title="No products" /> : (
        <Card style={{ padding: 0 }}>
          <Table head={['Product', 'Seller', 'Category', 'Price', 'Stock', 'Flash', 'Actions']}>
            {items.map((p) => (
              <tr key={p.id}>
                <td>
                  <span className="row">
                    <img src={p.imageUrl} alt="" style={{ width: 40, height: 40, borderRadius: 8, objectFit: 'cover' }} />
                    <strong>{p.title}</strong>
                  </span>
                </td>
                <td>{p.sellerName}<span className="muted" style={{ fontSize: 12, display: 'block' }}>{p.sellerEmail}</span></td>
                <td>{p.category}</td>
                <td>{formatUgx(p.priceMinor)}</td>
                <td>{p.stockQuantity}</td>
                <td>{p.isFlashDeal ? <span className="badge badge-red">Flash</span> : <span className="badge badge-gray">—</span>}</td>
                <td><Btn size="sm" onClick={() => setDel(p)} aria-label="Delete"><Trash2 size={14} /></Btn></td>
              </tr>
            ))}
          </Table>
        </Card>
      )}
      </div>
      <Pagination page={page} pageSize={25} total={total} onPage={(p) => load(p)} />
      <Confirm open={del !== null} onClose={() => setDel(null)} onConfirm={confirmDelete} danger
        title="Remove product" message={`Remove "${del?.title}" from the marketplace?`} />
    </>
  );
}
