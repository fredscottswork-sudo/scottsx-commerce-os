/**
 * Soft wall for guest-only visitors on Nearby and the AI Shopper.
 *
 * Not a redirect: the visitor stays on the URL they chose, sees what the
 * feature is, and gets one primary action (sign in). Signed-in users render
 * the page untouched — including unverified / not-yet-onboarded accounts,
 * which the route guards on their dashboards handle separately.
 */
import type { ReactNode } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../store/AuthContext';
import { MapPin, Sparkles, ArrowRight, Lock } from 'lucide-react';

const COPY = {
  nearby: {
    icon: <MapPin size={26} />,
    title: 'Nearby is for members',
    body: 'See verified sellers and products around you on a live map, sorted by distance. Sign in with your email or Google — it takes ten seconds.',
    perks: ['Live map of sellers near you', 'Distance and directions to each store', 'Deals filtered by your area'],
  },
  ai: {
    icon: <Sparkles size={26} />,
    title: 'The AI Shopper is for members',
    body: 'Describe what you need and the assistant compares stores, prices and stock for you. Sign in with your email or Google to start a conversation.',
    perks: ['Personal shopping assistant', 'Compares prices across stores', 'Remembers your conversations'],
  },
} as const;

export default function SignInGate({ feature, children }: { feature: keyof typeof COPY; children: ReactNode }) {
  const { user } = useAuth();
  const location = useLocation();
  if (user) return <>{children}</>;
  const c = COPY[feature];
  return (
    <section className="gate" data-testid={`gate-${feature}`}>
      <div className="gate-card">
        <div className="gate-icon">{c.icon}<span className="gate-lock"><Lock size={11} /></span></div>
        <h1>{c.title}</h1>
        <p className="gate-body">{c.body}</p>
        <ul className="gate-perks">
          {c.perks.map((p) => <li key={p}>{p}</li>)}
        </ul>
        <Link
          to="/login"
          state={{ from: location.pathname, reason: `Sign in to use ${feature === 'nearby' ? 'Nearby' : 'the AI Shopper'}.` }}
          className="btn btn-primary btn-lg gate-cta"
          data-testid="gate-signin"
        >
          Sign in or create an account <ArrowRight size={16} />
        </Link>
        <Link to="/" className="gate-back">Keep browsing as a guest</Link>
      </div>
    </section>
  );
}
