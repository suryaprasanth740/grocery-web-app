# NammaBasket — Final Review (Phase 10)

**Date:** 13 Oct 2026 · **Branch:** `nammabasket` · **Reviewed commit:** `b55d760`
**Verified by CI run** [#38415846620](https://github.com/suryaprasanth740/grocery-web-app/actions/runs/38415846620). All four jobs passed:

| Job | Result |
|---|---|
| API tests on H2 | 164 run, 0 failed, 0 skipped |
| API tests on PostgreSQL 16 | 164 run, 0 failed, 0 skipped |
| Browser tests (Playwright, real app on PostgreSQL) | 27 passed, including 26 accessibility scans with 0 findings |
| Docker image (what Render runs) | Builds, starts in about 10 s, answers, runs as the non-root user `app` |

This review covers five areas: functionality, security, accessibility, responsiveness and error handling.
Every finding below says how it was found, what was changed and which test now guards it. Anything not
fixed is listed under [Accepted risks and recommendations](#accepted-risks-and-recommendations).

---

## 1. Functionality

| Check | How | Result |
|---|---|---|
| Buyer journey: sign up, PIN code, shops, save for later, basket from two shops, slot, checkout | Browser test E2E-MARK-01…04 | Pass |
| Seller journey: notification, every order step, tax invoice | E2E-MARK-05 | Pass |
| Buyer gets notified, rates, sees the rating; admin hides it | E2E-MARK-06, 07 | Pass |
| All business rules (stock, coupons, fees, slots, refunds, roles…) | 164 API tests on two databases | Pass |
| Seller "New" orders filter hid an order as soon as it was accepted (DEF-07 in the test plan) | Found while writing the browser tests | **Fixed:** after Accept, the dashboard switches to "In progress" and says so |

## 2. Security

### Findings fixed in this phase

| ID | Finding | Severity | Fix | Proof |
|---|---|---|---|---|
| SR-01 | **Stored XSS through quotes.** `escapeHtml()` did not escape `"` and `'`. Product and shop names (typed by sellers) go into `alt="…"`, `aria-label="…"` and `data-…="…"` attributes, so a name like `Dal" onmouseover="…` could add its own script to every buyer's page. | Critical | `escapeHtml` now escapes `& < > " '` (one change protects every page) | Ran locally against the old and new code: the old code ran the payload and injected 4 elements; the new code ran nothing and injected 0. Regression test: browser test "Hostile shop or product text… (XSS)" |
| SR-02 | **Data inside inline event handlers.** The photo fallback did `onerror="…textContent='${emoji}'"`, so an emoji value with a quote became code. | High | The value goes into `data-fallback` and is read as text (`this.dataset.fallback`); same fix on the home and recipe pages | Same XSS test (fallback emoji payload) |
| SR-03 | Category icons and product emojis (admin input) were inserted as HTML, unescaped | Medium | Escaped | Same XSS test |
| SR-04 | **No browser security headers** when the app is served straight from Render: the admin and seller pages could be framed (click-jacking), there was no MIME-sniffing protection and no content policy | High | `SecurityHeadersFilter`: Content-Security-Policy (`frame-ancestors 'none'`, `object-src 'none'`, `connect-src 'self'`…), `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy`, `Permissions-Policy`. The same policy is in `netlify.toml` and `vercel.json` | `SecurityHeadersTest` (API), browser test "Security headers…", and every browser test fails on any CSP violation |
| SR-05 | **Session fixation at sign-up.** Login already issued a new session id, but sign-up kept the old one | Medium | `changeSessionId()` at sign-up as well | `SecurityHeadersTest.signUpRotatesSession` |
| SR-06 | **Admin password in the logs.** Without `ADMIN_PASSWORD`, a random password was printed, even for a production database, so anyone with log access could log in as admin | High | Printed only for the local in-memory database. For a real database it is never shown, and `ADMIN_PASSWORD` becomes the source of truth: set it and restart to (re)gain access | Code review (start-up behaviour; README and `.env.example` explain it) |
| SR-07 | API answers (orders, invoices, accounts) could be stored by shared caches | Low | `Cache-Control: no-store` on `/api/**` (except public images) | `SecurityHeadersTest.apiNoStore` |
| SR-08 | Buyer passwords could be 6 characters (sellers needed 8) | Low | 8–72 characters for everyone, with a hint on the sign-up form | `InputAndErrorsTest` (ERR-01) |
| SR-09 | Spring Boot 3.3.4 was behind on security patches (e.g. CVE-2025-22235) | Medium | Upgraded to 3.3.13, the last 3.3 release | All 164 tests pass on both databases |

### Checked and already sound (no change needed)

- **Passwords:** BCrypt; old FreshCart hashes are upgraded on login; 72-byte limit enforced.
- **Login:** the same answer for an unknown email and a wrong password; locked after 5 failures (AUTH-05, SEC-02, SEC-09).
- **Session cookie:** HttpOnly and SameSite=Lax; HTTPS-only in production via `COOKIE_SECURE=true` (set in `render.yaml`).
- **CSRF:** every change needs the `X-Requested-With` header (AUTH-10), on top of SameSite cookies.
- **Access control:** every seller, admin and buyer-only endpoint is tested with the wrong role and with no login.
  Other people's orders, products, invoices and notifications are refused (SEC-01, CAT-03, INV-05, NOTE-04, MV-09).
- **SQL injection:** all queries use JPQL with bound parameters. The only two native queries are fixed strings
  with no user input.
- **Uploads:** the file type is detected from the file's own bytes; files are at most 300 KB; a shop can only use
  its own uploads (CAT-09, CAT-10).
- **Errors:** generic messages, no stack traces, SQL or exception names (ERR-08, ERR-09).
- **Secrets:** none in the repository; CI uses throw-away passwords for throw-away databases.

## 3. Accessibility

- **Automated:** axe-core 4.10 scans for WCAG 2.1 A and AA rules on **26 page states**:
  - all 13 public pages;
  - every step of the buyer, seller and admin journeys, including the reviews dialog;
  - the seller and admin dashboards at phone size.

  Serious or critical findings fail the build. Every scan's full result is saved as `axe-*.json`.
- **Found and fixed:**
  - *Critical:* the admin tab strip had `role="tablist"` with no tabs inside. Both dashboards now use a labelled
    button group with `aria-pressed`.
  - *Serious:* on phones, the header's Admin link counted as a link inside a block of text. The header links are
    now separate items.
  - *Earlier phases:* 10 pages had no `h1`.
- **Result:** 0 findings of any severity on the last run.
- **Keyboard:** a browser test uses the keyboard only.
  - The first Tab reaches "Skip to content", and Enter moves focus into the page (`main` is focusable).
  - The PIN-code box opens with Enter, focus goes into its input, and Escape closes it and returns focus to its
    button. The notification bell behaves the same way.
  - The reviews dialog is a native modal `<dialog>`: focus stays inside it and Escape closes it.
  - Star ratings are real radio buttons, so arrow keys work.
- **Not covered:** no manual screen-reader pass (NVDA/VoiceOver). That is recommended before a public launch.

## 4. Responsiveness

- 375 px (small phone) checks fail the build if a page scrolls sideways:
  - all 13 public pages;
  - 6 buyer pages (cart, checkout, orders, order, saved, invoice);
  - the seller and admin dashboards.
- Screenshots of each are published to the `ci-screenshots` branch and were reviewed by eye.
- **Fixed:**
  - The header took three rows on phones. The greeting is hidden there, so it takes two.
  - In earlier phases, desktop header links wrapped and notification text was cut off.
- **By design:** on phones the invoice's item table scrolls inside its own box instead of shrinking the numbers.

## 5. Error handling

| Situation | Behaviour | Test |
|---|---|---|
| Unknown page or API path | 404 with a short JSON message | SMART-09, E2E-PAGE-14 |
| Bad input (fields, broken JSON, wrong types, wrong method) | 400 / 405 with a plain message for the form | ERR-01, ERR-02, ERR-07, ERR-08 |
| Unexpected bug | 500 "Something went wrong. Please try again." with nothing internal; details only in the server log | ERR-09 |
| Database rule broken (duplicate, race) | 409 "conflicts with other data", never a 500 | CRIT-01, CRIT-03, SLOT-07 |
| Free server waking up (502/503/504 from the proxy) | Pages retry read requests for about 90 s; changes are never retried, so no double orders | `api.js` (by design) |
| Something changed while the buyer was paying (price, stock, slot) | 409 with the reason; the checkout reloads the bill and slots | CRIT-07, SLOT-03 |
| A broken product photo | The product's emoji is shown instead | XSS test (fallback path) |

## Accepted risks and recommendations

| Item | Risk | Recommendation |
|---|---|---|
| Spring Boot 3.3 gets no more free updates | Future vulnerabilities won't be patched | Move to a supported Spring Boot line (3.5 or 4.x) as a separate, fully tested change |
| `script-src 'unsafe-inline'` in the CSP | If an escaping mistake slipped in, the CSP would not block inline script | Move each page's inline `<script>` into a `.js` file and the remaining `onerror` handlers into code, then drop `'unsafe-inline'` |
| Rate limits exist only for login | Sign-up, review and report spam is possible | Add per-IP limits (e.g. Bucket4j, or at the proxy) |
| Login lockout is kept in memory | Resets on restart; doesn't work across several servers | Store it in Redis or the database if the app is scaled out |
| Sign-up says an email is already registered | Someone can test whether an email has an account | Common trade-off for usability; could answer "check your email" once email verification exists |
| No email verification or password reset | Typos lock people out; fake emails are possible | Add an email provider and a verify/reset flow |
| Another buyer's order gives 403, another shop's gives 404 | Inconsistent; 403 confirms the order exists | Use 404 everywhere for other people's records |
| Browser tests run in Chrome only; no manual screen-reader pass | Safari/Firefox and assistive-tech issues could remain | Manual check before a public launch |
| Payments are a demo | Not usable for real money | Razorpay/PhonePe test mode, trusting only their signed webhooks |

## Verdict

NammaBasket meets the brief:
- a multi-vendor marketplace on the original Java/Spring/PostgreSQL stack;
- roles, shops, a one-basket checkout split by shop, and admin tools;
- the four extra features (reviews, slots, saved for later, notifications and invoices);
- versioned migrations and deployment configuration.

Every rule is covered by tests that run on every push, on the same database as production.

The review found a **critical stored-XSS bug and eight other security issues**, and fixed them all. Each has
a regression test except the start-up change SR-06, which was checked by code review. It also found **two accessibility problems** that now fail the build if they return. What
remains is listed above, with a concrete next step for each.
