/**
 * ScottsTechX web — Google Identity Services loader.
 *
 * We talk to GIS directly instead of pulling in the Firebase JS SDK: the
 * backend (POST /auth/google) verifies a Google-issued id_token against
 * Google's JWKS, which is exactly what GIS hands us. The Firebase SDK would
 * add megabytes to the bundle to produce the same credential.
 *
 * The script is loaded lazily on first use, so visitors who never touch the
 * Google button pay nothing, and a blocked/offline accounts.google.com
 * degrades to "unavailable" rather than a dead button or a crash.
 */

const GIS_SRC = 'https://accounts.google.com/gsi/client';
const SCRIPT_ID = 'stx-gis';

/** Web OAuth client for project scottstechx-52bab. Public by design. */
export const GOOGLE_CLIENT_ID =
  (import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined) ||
  '911393008938-f0an8p59rlkhimcnn9rdqbtbi1aa9hbk.apps.googleusercontent.com';

/** How long we wait for Google's script before calling it unavailable. */
const LOAD_TIMEOUT_MS = 8000;

export class GoogleUnavailableError extends Error {
  constructor(message = "Google Sign-In could not load. Check your connection, or use your email and password.") {
    super(message);
    this.name = 'GoogleUnavailableError';
  }
}

interface GisAccounts {
  id: {
    initialize(config: {
      client_id: string;
      callback: (res: { credential?: string; error?: string }) => void;
      auto_select?: boolean;
      cancel_on_tap_outside?: boolean;
      ux_mode?: 'popup' | 'redirect';
    }): void;
    prompt(listener?: (n: unknown) => void): void;
    renderButton(parent: HTMLElement, options: Record<string, unknown>): void;
    disableAutoSelect(): void;
  };
}

declare global {
  interface Window {
    google?: { accounts?: GisAccounts };
  }
}

let loader: Promise<GisAccounts> | null = null;

/** Loads the GIS script once; rejects with GoogleUnavailableError if it can't. */
export function loadGoogle(): Promise<GisAccounts> {
  if (loader) return loader;

  loader = new Promise<GisAccounts>((resolve, reject) => {
    if (typeof document === 'undefined') {
      reject(new GoogleUnavailableError('Google Sign-In needs a browser.'));
      return;
    }
    if (window.google?.accounts) {
      resolve(window.google.accounts);
      return;
    }

    const done = (fn: () => void) => {
      window.clearTimeout(timer);
      fn();
    };
    const timer = window.setTimeout(() => {
      // Let a later attempt retry from scratch.
      loader = null;
      reject(new GoogleUnavailableError());
    }, LOAD_TIMEOUT_MS);

    const onLoad = () => {
      if (window.google?.accounts) done(() => resolve(window.google!.accounts!));
      else
        done(() => {
          loader = null;
          reject(new GoogleUnavailableError());
        });
    };
    const onError = () =>
      done(() => {
        loader = null;
        reject(new GoogleUnavailableError());
      });

    const existing = document.getElementById(SCRIPT_ID) as HTMLScriptElement | null;
    if (existing) {
      existing.addEventListener('load', onLoad);
      existing.addEventListener('error', onError);
      return;
    }

    const script = document.createElement('script');
    script.id = SCRIPT_ID;
    script.src = GIS_SRC;
    script.async = true;
    script.defer = true;
    script.addEventListener('load', onLoad);
    script.addEventListener('error', onError);
    document.head.appendChild(script);
  });

  return loader;
}

/**
 * Renders Google's official button into `parent` and resolves with the
 * id_token when the user completes sign-in.
 *
 * Google requires *their* rendered button for the popup flow, so we mount it
 * and let the caller style the surrounding frame.
 */
export async function renderGoogleButton(
  parent: HTMLElement,
  onCredential: (idToken: string) => void,
  opts: { theme?: 'outline' | 'filled_black'; width?: number } = {}
): Promise<void> {
  const accounts = await loadGoogle();
  accounts.id.initialize({
    client_id: GOOGLE_CLIENT_ID,
    callback: (res) => {
      if (res.credential) onCredential(res.credential);
    },
    cancel_on_tap_outside: true,
    ux_mode: 'popup',
  });
  parent.replaceChildren();
  accounts.id.renderButton(parent, {
    type: 'standard',
    theme: opts.theme ?? 'filled_black',
    size: 'large',
    text: 'continue_with',
    shape: 'pill',
    logo_alignment: 'left',
    width: opts.width ?? 320,
  });
}

/** Clears the "one tap" auto-select so the next sign-in asks again. */
export function forgetGoogleSession(): void {
  try {
    window.google?.accounts?.id.disableAutoSelect();
  } catch {
    /* GIS never loaded — nothing to forget */
  }
}
