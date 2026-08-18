import { useEffect, useState, type FormEvent } from 'react';
import { Inbox, LifeBuoy, Plus } from 'lucide-react';
import { buyerService } from '../../api/services';
import type { Faq, SupportTicket } from '../../api/types';
import { useToast } from '../../store/ToastContext';
import { Btn, Card, Empty, ErrorBox, Field, Input, Loading, Modal, PageHeader, StatusBadge, TextArea } from '../../components/ui';

export default function Support() {
  const { toast } = useToast();
  const [faqs, setFaqs] = useState<Faq[]>([]);
  const [tickets, setTickets] = useState<SupportTicket[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [open, setOpen] = useState(false);
  const [subject, setSubject] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  async function load() {
    setLoading(true);
    Promise.all([buyerService.faqs(), buyerService.tickets()])
      .then(([f, t]) => { setFaqs(f.faqs); setTickets(t.tickets); })
      .catch((e: any) => setError(e.message))
      .finally(() => setLoading(false));
  }
  useEffect(() => { load(); }, []);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await buyerService.createTicket(subject, message);
      toast('Ticket opened', 'success');
      setOpen(false);
      setSubject(''); setMessage('');
      load();
    } catch (err: any) {
      toast(err.message, 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader title="Support" sub="FAQs and your support tickets — same backend the mobile app uses."
        actions={<Btn variant="primary" onClick={() => setOpen(true)}><Plus size={16} /> Open ticket</Btn>} />
      {loading ? <Loading /> : error ? <ErrorBox message={error} onRetry={load} /> : (
        <>
          <h2 className="mb-16">Frequently asked questions</h2>
          <div className="grid grid-2 mb-24" style={{ marginBottom: 30 }}>
            {faqs.map((f) => (
              <Card key={f.id}>
                <strong>{f.question}</strong>
                <p className="muted" style={{ margin: '6px 0 0' }}>{f.answer}</p>
              </Card>
            ))}
          </div>

          <h2 className="mb-16">Your tickets</h2>
          {tickets.length === 0 ? <Empty icon={<Inbox size={28} />} title="No tickets yet" /> : (
            <div className="grid grid-2">
              {tickets.map((t) => (
                <Card key={t.id}>
                  <div className="row-between mb-8">
                    <strong className="row"><LifeBuoy size={16} style={{ color: 'var(--primary)' }} /> {t.subject}</strong>
                    <StatusBadge status={t.status} />
                  </div>
                  <p className="muted" style={{ margin: 0 }}>{t.message}</p>
                </Card>
              ))}
            </div>
          )}
        </>
      )}

      <Modal open={open} onClose={() => setOpen(false)} title="Open a support ticket" footer={
        <Btn variant="primary" onClick={submit} disabled={busy || !subject || !message}>{busy ? 'Opening…' : 'Open ticket'}</Btn>
      }>
        <form onSubmit={submit}>
          <Field label="Subject"><Input required value={subject} onChange={(e) => setSubject(e.target.value)} placeholder="What's the issue?" /></Field>
          <Field label="Message"><TextArea required rows={4} value={message} onChange={(e) => setMessage(e.target.value)} placeholder="Describe the problem…" /></Field>
        </form>
      </Modal>
    </>
  );
}
