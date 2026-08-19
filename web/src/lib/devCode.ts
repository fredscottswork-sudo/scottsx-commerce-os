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

/**
 * The verification LINK, stashed on the same terms as the code above.
 *
 * Same rule: only ever present when the deployment has no mail server, so the
 * link cannot be delivered and has to be shown in the page instead. With a
 * working mailer this is never set and the user clicks the link in their
 * inbox, which is the intended flow.
 */
const DEV_LINK_KEY = 'stx_dev_verify_link';

export function rememberDevLink(link?: string) {
  try {
    if (link) sessionStorage.setItem(DEV_LINK_KEY, link);
  } catch {
    /* private mode */
  }
}

export function readDevLink(): string {
  try {
    return sessionStorage.getItem(DEV_LINK_KEY) || '';
  } catch {
    return '';
  }
}

export function clearDevLink() {
  try {
    sessionStorage.removeItem(DEV_LINK_KEY);
  } catch {
    /* ignore */
  }
}
