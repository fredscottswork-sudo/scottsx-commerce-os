/**
 * BrandLogo — the official ScottsTechX mark, shared with the Android app.
 *
 * Assets in /public/brand are generated from the same source artwork the app
 * uses as its launcher/notification icon
 * (scottsx-android/app/src/main/res/drawable-nodpi/logo.png), so web and app
 * render an identical brand.
 *
 *  - `mark`    square STX monogram on the brand-dark backdrop (nav, avatars)
 *  - `lockup`  full logo with wordmark + tagline, transparent (auth, splash)
 *
 * Both components degrade gracefully: if the image is missing (e.g. /brand
 * was not deployed), an inline fallback is shown instead of a broken-image
 * icon, so the nav never shows a missing-asset placeholder.
 */

import { useState } from 'react';

type MarkProps = {
  /** Pixel size of the square mark. Default 36 to match the old placeholder. */
  size?: number;
  className?: string;
};

function FallbackMark({ size, className }: MarkProps) {
  return (
    <span
      aria-hidden="true"
      className={`brand-logo brand-logo-fallback ${className}`.trim()}
      style={{
        width: size,
        height: size,
        display: 'inline-grid',
        placeItems: 'center',
        background: 'var(--gradient-brand, linear-gradient(135deg,#124ca8,#1e6fff))',
        color: '#fff',
        fontWeight: 800,
        fontSize: Math.round((size || 36) * 0.52),
        fontFamily: 'var(--font-display, system-ui)',
        borderRadius: 11,
        flexShrink: 0,
      }}
    >
      S
    </span>
  );
}

export function BrandMark({ size = 36, className = '' }: MarkProps) {
  const [failed, setFailed] = useState(false);
  if (failed) return <FallbackMark size={size} className={className} />;
  return (
    <img
      src="/brand/scottstechx-mark.png"
      width={size}
      height={size}
      alt=""
      aria-hidden="true"
      decoding="async"
      className={`brand-logo ${className}`.trim()}
      style={{ width: size, height: size }}
      onError={() => setFailed(true)}
    />
  );
}

type LockupProps = {
  /** Max width in pixels. Height scales automatically. */
  width?: number;
  className?: string;
  /** Accessible name; the lockup already spells out the brand. */
  alt?: string;
};

function FallbackLockup({ width = 240, className }: LockupProps) {
  return (
    <span
      className={`brand-lockup brand-lockup-fallback row ${className}`.trim()}
      style={{ width, maxWidth: '100%', display: 'inline-flex', alignItems: 'center', gap: 12 }}
      aria-label="ScottsTechX"
    >
      <BrandMark size={44} />
      <span style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.1 }}>
        <span style={{ fontFamily: 'var(--font-display, system-ui)', fontWeight: 800, fontSize: 20, letterSpacing: '-0.03em' }}>
          ScottsTechX
        </span>
        <span style={{ fontSize: 10, letterSpacing: '0.08em', textTransform: 'uppercase', opacity: 0.7 }}>
          Innovate. Integrate. Elevate.
        </span>
      </span>
    </span>
  );
}

export function BrandLockup({
  width = 240,
  className = '',
  alt = 'ScottsTechX Enterprises (U) Ltd — Innovate. Integrate. Elevate.',
}: LockupProps) {
  const [failed, setFailed] = useState(false);
  if (failed) return <FallbackLockup width={width} className={className} />;
  return (
    <img
      src="/brand/scottstechx-logo-transparent.png"
      alt={alt}
      decoding="async"
      className={`brand-lockup ${className}`.trim()}
      style={{ width, maxWidth: '100%', height: 'auto' }}
      onError={() => setFailed(true)}
    />
  );
}

export default BrandMark;
