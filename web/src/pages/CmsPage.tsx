import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { cmsService } from '../api/services';
import type { CmsPage as CmsType } from '../api/types';
import { Card, ErrorBox, Loading } from '../components/ui';
import { useSeo } from '../hooks/useSeo';

export default function CmsPage() {
  const { slug } = useParams<{ slug: string }>();
  const [page, setPage] = useState<CmsType | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useSeo({
    title: page?.title,
    description: page?.body?.slice(0, 300),
  });

  useEffect(() => {
    setLoading(true);
    setError('');
    cmsService.page(slug!).then((r) => setPage(r.page)).catch((e: any) => setError(e.message)).finally(() => setLoading(false));
  }, [slug]);

  if (loading) return <Loading />;
  if (error || !page) return <ErrorBox message={error || 'Page not found'} />;

  return (
    <>
      <Link to="/" className="muted">← Back</Link>
      <Card className="mt-16">
        <h1 style={{ marginTop: 0 }}>{page.title}</h1>
        <span className="muted" style={{ fontSize: 12.5 }}>Updated {new Date(page.updatedAt).toLocaleDateString()}</span>
        {page.body.split('\n').map((line, i) => (
          <p key={i} style={{ whiteSpace: 'pre-wrap' }}>{line || '\u00A0'}</p>
        ))}
      </Card>
    </>
  );
}
