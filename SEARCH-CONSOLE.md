# Google Search Console verification

## Status

The verification file is in the repo and ships with the site:

- source: `web/public/google4cc19033657ba3e3.html`
- build output: `web/dist/google4cc19033657ba3e3.html`
- contents (exactly 53 bytes, no trailing newline):

  ```
  google-site-verification: google4cc19033657ba3e3.html
  ```

I tested it against a real server: `200 OK`, `Content-Type: text/html`, correct
body, and the SPA catch-all rewrite does **not** shadow it (static files are
matched first). A regression test in `web/tests/ui.mjs` section 22 fails the
build if the file stops shipping or its contents drift.

---

## ⚠️ Verification will still fail right now — and not because of the file

Your Vercel deployment is behind **Deployment Protection**. I fetched the live
production URL and both the site root and the verification file redirect to
`vercel.com/login`:

```
https://scottsx-commerce-fugasn5qr-fredscottswork-1879s-projects.vercel.app/google4cc19033657ba3e3.html
  -> 'Protected Deployment - Log in to Vercel'
```

Google's first requirement for this method is explicit:

> **The file cannot require authentication.** The directory where you upload
> your HTML file must be available to non-logged-in users.

Googlebot has no Vercel account, so it sees the login page, not your token, and
verification fails with *"Your verification file has the wrong content"*.

Vercel turns **Standard Protection** on by default for new projects, so this
was almost certainly never a deliberate choice.

### Fix it (about 30 seconds)

1. Go to your Vercel project → **Settings** → **Deployment Protection**
2. Set **Vercel Authentication** to **Disabled** for **Production**
   (you can leave it enabled for Preview deployments if you want branch builds
   to stay private — only the URL you verify has to be public)
3. Save

### Confirm before clicking Verify

Open the file in a **private/incognito window**, signed out of Vercel:

```
https://<your-domain>/google4cc19033657ba3e3.html
```

You should see the single line of text and nothing else. If you get a Vercel
login page, protection is still on.

---

## Then verify

1. Search Console → your property → **Ownership verification** → **HTML file**
2. Click **Verify**

### Which URL to verify

Verify the domain people actually visit. If you have a custom domain, use that,
not the `*.vercel.app` URL — Search Console does **not** follow redirects across
domains, so verifying the wrong host will fail.

Note also that a `*.vercel.app` deployment URL changes on every deploy. Verify
either your custom domain or the project's stable production alias.

---

## Notes

- **Don't delete the file after verifying.** Google re-checks periodically; if
  the token disappears you lose verification and, eventually, access to the
  property.
- **Don't edit it.** Google requires the file byte-for-byte as issued. Extra
  newlines at the end are tolerated; nothing else is.
- If you ever move hosting, the file must be re-deployed at the new root. It is
  committed to the repo, so any deploy from this branch carries it.
- Once verified, submit a sitemap — that is what actually gets pages indexed.
  There is no `sitemap.xml` in the build yet; say the word and I will add one
  that lists the public routes and approved product pages.

## A note on the upload

Your `google4cc19033657ba3e3.html` attachment did not reach my workspace on
either attempt — I only ever saw the filename. I reconstructed the file from
the token embedded in that filename, which is the standard and documented
format, and verified it serves correctly.

If Google's copy contains anything beyond that one line, paste the contents to
me as plain text and I will match it exactly. Otherwise the file as committed
is correct.
