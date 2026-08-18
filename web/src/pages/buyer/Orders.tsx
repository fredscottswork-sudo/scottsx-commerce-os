import { useEffect, useState } from 'react';
import { buyerService } from '../../api/services';
import type { Order } from '../../api/types';
import { formatUgx } from '../../api/types';
import { Empty, ErrorBox, Loading, PageHeader, StatusBadge, Table } from '../../components/ui';

export default function Orders() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    buyerService.orders().then((r) => setOrders(r.orders)).catch((e: any) => setError(e.message)).finally(() => setLoading(false));
  }, []);

  return (
    <>
      <PageHeader title="My orders" sub="Every order from the shared backend — statuses sync with the mobile app." />
      {loading ? <Loading /> : error ? <ErrorBox message={error} onRetry={() => window.location.reload()} /> :
        orders.length === 0 ? <Empty emoji="📦" title="No orders yet" /> : (
        <div className="card" style={{ padding: 0 }}>
          <Table head={['Product', 'Store', 'Qty', 'Amount', 'Status', 'Date']}>
            {orders.map((o) => (
              <tr key={o.id}>
                <td>
                  <span className="row">
                    {o.imageUrl && <img src={o.imageUrl} alt="" style={{ width: 40, height: 40, borderRadius: 8, objectFit: 'cover' }} />}
                    <strong>{o.title}</strong>
                  </span>
                </td>
                <td>{o.storeName || 'ScottsTechX'}</td>
                <td>{o.quantity}</td>
                <td>{formatUgx(o.amount)}</td>
                <td><StatusBadge status={o.status} /></td>
                <td>{new Date(o.createdAt).toLocaleDateString()}</td>
              </tr>
            ))}
          </Table>
        </div>
      )}
    </>
  );
}
