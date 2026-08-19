/**
 * The six-digit fallback code, stashed for the verification page.
 *
 * Only ever populated when Firebase could not be used and the backend had no
 * mail server either — in that case the API returns the code so sign-up is
 * still completable. With a working mailer this is never set.
 */
const DEV_CODE_KEY = 'stx_dev_verify_code';

export function rememberDevCode(code?: string) {
  try {
    if (code) sessionStorage.setItem(DEV_CODE_KEY, code);
  } catch {
    /* private mode */
  }
}

export function readDevCode(): string {
  try {
    return sessionStorage.getItem(DEV_CODE_KEY) || '';
  } catch {
    return '';
  }
}

export function clearDevCode() {
  try {
    sessionStorage.removeItem(DEV_CODE_KEY);
  } catch {
    /* ignore */
  }
}
