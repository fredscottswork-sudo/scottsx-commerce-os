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
 */

type MarkProps = {
  /** Pixel size of the square mark. Default 36 to match the old placeholder. */
  size?: number;
  className?: string;
};

export function BrandMark({ size = 36, className = '' }: MarkProps) {
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

export function BrandLockup({
  width = 240,
  className = '',
  alt = 'ScottsTechX Enterprises (U) Ltd — Innovate. Integrate. Elevate.',
}: LockupProps) {
  return (
    <img
      src="/brand/scottstechx-logo-transparent.png"
      alt={alt}
      decoding="async"
      className={`brand-lockup ${className}`.trim()}
      style={{ width, maxWidth: '100%', height: 'auto' }}
    />
  );
}

export default BrandMark;
