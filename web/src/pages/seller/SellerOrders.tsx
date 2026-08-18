import { useEffect, useState } from 'react';
import { sellerService } from '../../api/services';
import type { Order } from '../../api/types';
import { formatUgx } from '../../api/types';
import { Empty, ErrorBox, Loading, PageHeader, StatusBadge, Table, Card } from '../../components/ui';

export default function SellerOrders() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    sellerService.orders().then((r) => setOrders(r.orders)).catch((e: any) => setError(e.message)).finally(() => setLoading(false));
  }, []);

  return (
    <>
      <PageHeader title="Orders" sub="Orders placed for your products — same data the mobile seller app sees." />
      {loading ? <Loading /> : error ? <ErrorBox message={error} onRetry={() => window.location.reload()} /> :
        orders.length === 0 ? <Empty emoji="🧾" title="No orders yet" /> : (
        <Card style={{ padding: 0 }}>
          <Table head={['Product', 'Buyer', 'Qty', 'Amount', 'Status', 'Date']}>
            {orders.map((o) => (
              <tr key={o.id}>
                <td><strong>{o.title}</strong></td>
                <td>{o.buyerName || '—'}</td>
                <td>{o.quantity}</td>
                <td>{formatUgx(o.amount)}</td>
                <td><StatusBadge status={o.status} /></td>
                <td>{new Date(o.createdAt).toLocaleDateString()}</td>
              </tr>
            ))}
          </Table>
        </Card>
      )}
    </>
  );
}
