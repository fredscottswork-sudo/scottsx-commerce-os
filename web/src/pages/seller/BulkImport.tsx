import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Upload, FileSpreadsheet } from 'lucide-react';
import { sellerService } from '../../api/services';
import { useToast } from '../../store/ToastContext';
import { Btn, Card, ErrorBox, PageHeader, Table } from '../../components/ui';

interface Row { title: string; price: number; category: string; stock: number; imageUrl: string; ok?: boolean; error?: string }

/** Parse a CSV string into rows (handles quoted fields + header row). */
function parseCsv(text: string): string[][] {
  const rows: string[][] = [];
  let cur = ''; let row: string[] = []; let inQ = false;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (inQ) {
      if (ch === '"') { if (text[i + 1] === '"') { cur += '"'; i++; } else inQ = false; }
      else cur += ch;
    } else if (ch === '"') inQ = true;
    else if (ch === ',') { row.push(cur); cur = ''; }
    else if (ch === '\n' || ch === '\r') {
      if (ch === '\r' && text[i + 1] === '\n') i++;
      row.push(cur); cur = '';
      if (row.some((c) => c.trim() !== '')) rows.push(row);
      row = [];
    } else cur += ch;
  }
  row.push(cur);
  if (row.some((c) => c.trim() !== '')) rows.push(row);
  return rows;
}

export default function BulkImport() {
  const { toast } = useToast();
  const navigate = useNavigate();
  const [rows, setRows] = useState<Row[]>([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);

  function onFile(file: File | null) {
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const text = String(reader.result || '');
        const raw = parseCsv(text);
        if (raw.length < 2) { setError('CSV needs a header row + data rows'); return; }
        const header = raw[0].map((h) => h.trim().toLowerCase());
        const data = raw.slice(1).map((r) => Object.fromEntries(header.map((h, i) => [h, (r[i] ?? '').trim()])));
        const parsed: Row[] = data.map((d) => ({
          title: d.title || d.name || '',
          price: Number(d.price || d.price_minor || d.priceMinor || 0),
          category: d.category || 'Other',
          stock: Number(d.stock || d.stock_quantity || d.stockQuantity || 1),
          imageUrl: d.image_url || d.imageUrl || '',
        }));
        setRows(parsed);
        setError('');
        setDone(false);
      } catch {
        setError('Could not parse the file. Expected CSV: title, price, category, stock, imageUrl');
      }
    };
    reader.readAsText(file);
  }

  async function doImport() {
    setBusy(true);
    let okCount = 0;
    const updated = await Promise.all(rows.map(async (r) => {
      if (!r.title || !r.price) return { ...r, ok: false, error: 'Missing title or price' };
      try {
        await sellerService.createProduct({
          title: r.title, description: '', category: r.category || 'Other', brand: '', priceMinor: r.price,
          oldPriceMinor: null, stockQuantity: r.stock || 1, imageUrl: r.imageUrl, location: '', isFlashDeal: false, discountPercent: 0,
        });
        okCount++;
        return { ...r, ok: true };
      } catch (e: any) {
        return { ...r, ok: false, error: e.message };
      }
    }));
    setRows(updated);
    setDone(true);
    setBusy(false);
    toast(`Imported ${okCount}/${rows.length} products`, okCount === rows.length ? 'success' : 'warning');
    if (okCount === rows.length) setTimeout(() => navigate('/seller/inventory'), 1500);
  }

  return (
    <>
      <PageHeader title="Bulk import" sub="Upload a CSV of products — validated, previewed, then saved through the existing backend (same endpoint the mobile app uses)." />

      <Card className="mb-16">
        <h3 style={{ marginTop: 0 }} className="mb-8"><FileSpreadsheet size={18} style={{ verticalAlign: -3 }} /> CSV format</h3>
        <p className="muted" style={{ fontSize: 13 }}>Columns: <code>title, price, category, stock, imageUrl</code> — header row required.</p>
        <label className="btn btn-primary" style={{ cursor: 'pointer' }}>
          <Upload size={16} /> Choose CSV / Excel file
          <input type="file" accept=".csv,text/csv,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" hidden
            onChange={(e) => onFile(e.target.files?.[0] ?? null)} />
        </label>
      </Card>

      {error && <ErrorBox message={error} />}

      {rows.length > 0 && (
        <>
          <div className="row-between mb-16">
            <strong>{rows.length} row(s) parsed — preview before importing</strong>
            <Btn variant="primary" onClick={doImport} disabled={busy || done}>{busy ? 'Importing…' : `Import ${rows.length}`}</Btn>
          </div>
          <Card style={{ padding: 0 }}>
            <Table head={['Title', 'Category', 'Price (UGX)', 'Stock', 'Status']}>
              {rows.map((r, i) => (
                <tr key={i}>
                  <td>{r.title || <span className="muted">—</span>}</td>
                  <td>{r.category}</td>
                  <td>{r.price || '—'}</td>
                  <td>{r.stock}</td>
                  <td>
                    {r.ok === true ? <span className="badge badge-green">Imported</span>
                      : r.ok === false ? <span className="badge badge-red">{r.error || 'Failed'}</span>
                      : <span className="badge badge-gray">Pending</span>}
                  </td>
                </tr>
              ))}
            </Table>
          </Card>
        </>
      )}
    </>
  );
}
