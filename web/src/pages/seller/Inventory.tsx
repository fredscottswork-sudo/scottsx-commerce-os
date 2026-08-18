import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Plus, Trash2, Upload } from 'lucide-react';
import { sellerService } from '../../api/services';
import type { Product } from '../../api/types';
import { formatUgx } from '../../api/types';
import { useToast } from '../../store/ToastContext';
import { Btn, Card, Confirm, Empty, ErrorBox, Loading, PageHeader, SearchInput, StatusBadge, Table } from '../../components/ui';

export default function Inventory() {
  const { toast } = useToast();
  const [items, setItems] = useState<Product[]>([]);
  const [q, setQ] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [del, setDel] = useState<Product | null>(null);

  async function load() {
    setLoading(true);
    sellerService.inventory().then((r) => setItems(r.products)).catch((e: any) => setError(e.message)).finally(() => setLoading(false));
  }
  useEffect(() => { load(); }, []);

  async function confirmDelete() {
    if (!del) return;
    await sellerService.deleteProduct(del.id);
    toast('Product deleted', 'info');
    load();
  }

  const filtered = items.filter((p) => (p.title + p.category).toLowerCase().includes(q.toLowerCase()));

  return (
    <>
      <PageHeader title="Inventory" sub="Your products — add, edit and manage stock. Shared with the mobile app."
        actions={
          <div className="row wrap">
            <Link to="/seller/bulk-import"><Btn><Upload size={16} /> Bulk import</Btn></Link>
            <Link to="/seller/add-product"><Btn variant="primary"><Plus size={16} /> Add product</Btn></Link>
          </div>
        } />
      <SearchInput value={q} onChange={setQ} placeholder="Search your products…" />
      <div className="mt-16">
      {loading ? <Loading /> : error ? <ErrorBox message={error} onRetry={load} /> :
        filtered.length === 0 ? <Empty emoji="📦" title="No products" subtitle="Add your first product to start selling." /> : (
        <Card style={{ padding: 0 }}>
          <Table head={['Product', 'Category', 'Price', 'Stock', 'Status', 'Actions']}>
            {filtered.map((p) => (
              <tr key={p.id}>
                <td>
                  <span className="row">
                    <img src={p.imageUrl} alt="" style={{ width: 42, height: 42, borderRadius: 8, objectFit: 'cover' }} />
                    <div>
                      <strong>{p.title}</strong>
                      <span className="muted" style={{ fontSize: 12, display: 'block' }}>{p.brand}</span>
                    </div>
                  </span>
                </td>
                <td>{p.category}</td>
                <td>{formatUgx(p.priceMinor)}</td>
                <td>{p.stockQuantity}</td>
                <td>{p.stockQuantity <= 5 ? <StatusBadge status="low stock" /> : p.isFlashDeal ? <StatusBadge status="flash" /> : <StatusBadge status="active" />}</td>
                <td>
                  <div className="row-actions">
                    <Link to={`/product/${p.id}`}><Btn size="sm">View</Btn></Link>
                    <Btn size="sm" onClick={() => setDel(p)} aria-label="Delete"><Trash2 size={14} /></Btn>
                  </div>
                </td>
              </tr>
            ))}
          </Table>
        </Card>
      )}
      </div>

      <Confirm open={del !== null} onClose={() => setDel(null)} onConfirm={confirmDelete} danger
        title="Delete product" message={`Delete "${del?.title}"? This is permanent.`} />
    </>
  );
}
