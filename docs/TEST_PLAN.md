# NammaBasket — QA Test Plan

**Product:** NammaBasket, a multi-vendor marketplace for neighbourhood kirana shops (Spring Boot 3, Java 17, PostgreSQL, plain JavaScript).
**Branch:** `nammabasket` · **Last full run:** see the inventory at the end (every result there comes from a real CI run, nothing is marked Pass by hand).
**Older manual cases:** [`TEST_CASES.md`](TEST_CASES.md) holds the FreshCart-era edge cases (TC_01–TC_12); they are still automated and still pass (the `CRIT-`, `SEC-` and `CHK-` tests below).

---

## 1. Scope

**In scope**

| Area | Users |
|---|---|
| Sign-up, login, roles (buyer / seller / admin), account suspension, CSRF header, login lockout | everyone |
| Shops: seller application, admin approval / rejection / suspension, shop details, PIN-code coverage | seller, admin |
| Catalogue: seller products, photos, MRP discount, switching products off, paused and suspended shops | seller, buyer |
| Cart and checkout: one basket from many shops, delivery or pickup per shop, fees, coupons, COD limit, UPI demo payment | buyer |
| Orders: shop order steps, rejection, cancellation, stock returns, refunds for missing items | buyer, seller, admin |
| New features: ratings & reviews, delivery/pickup time slots, saved for later, notifications, GST invoice | all |
| Admin tools: reports, review moderation, categories, promotions, order search, audit log | admin |
| Error handling: unknown pages, bad input, server errors that must not leak details | everyone |
| Pages: every public page loads without script errors, has one main heading, fits a phone screen | everyone |

**Out of scope (not tested, and why)**

| Not tested | Why |
|---|---|
| Real payments | UPI is a demo screen; no payment gateway is connected. |
| Load and performance | Free hosting; no load test environment. Concurrency is tested for correctness only (last item, last slot, double submit). |
| Browsers other than Chromium | Browser tests run in Chrome only. Firefox / Safari are checked by hand before a release. |
| Manual screen-reader testing | Automated axe scans and keyboard tests run on every push (see FINAL_REVIEW.md); a manual NVDA/VoiceOver pass is still to do. |
| Emails / SMS | The app doesn't send any; notifications are in-app only. |

## 2. Test levels and tools

| Level | What it proves | Tool | Where it runs |
|---|---|---|---|
| Unit | Pure rules with fixed inputs (time-slot windows, cut-off minutes) | JUnit 5 | `SlotRulesTest` |
| API / integration | Each endpoint: status code, response body and the database effect (stock, orders, audit rows) | JUnit 5 + Spring MockMvc + AssertJ | `src/test/java/...` (164 tests) |
| Database compatibility | The same 164 tests on a real PostgreSQL 16 server (production uses PostgreSQL on Neon). Tables come from the Flyway migration and Hibernate validates them, so a schema mistake fails every test | GitHub Actions service container | job "API tests (PostgreSQL)" |
| Container | The Docker image Render runs: it builds, starts, answers, and runs as a non-root user | Docker | job "Docker image" |
| End-to-end (browser) | Real app + real database + real Chrome: buyer, seller and admin journeys, keyboard-only use, XSS, security headers, phone widths | Playwright 1.47 | `e2e/` (27 tests) |
| Accessibility | WCAG 2.1 A/AA rules on 26 page states; serious/critical findings fail the build | axe-core 4.10 | inside the browser tests |
| Exploratory | Looking at every screenshot from the browser run; trying odd inputs by hand | Screenshots on the `ci-screenshots` branch | after each push |

**Test data rules**
- Every API test creates its own buyers, sellers, shops and products (helpers in `ApiTestSupport`), so tests never depend on each other or on the order they run in.
- Seeded demo data (4 shops, ~100 products) is switched off in API tests (`app.demo.marketplace-shops=false`) and switched on in browser tests, where it is part of what is being checked.
- Time-dependent rules are tested with fixed clock values (`SlotRulesTest`) or relative dates ("tomorrow"), never today's real time.
- CI uses throw-away passwords (`ADMIN_PASSWORD`, `DEMO_SELLER_PASSWORD`) for a throw-away database. No real credentials are in the repository.

## 3. Environments

| Environment | Database | How to run |
|---|---|---|
| Developer machine | H2 in memory (MySQL mode) | `mvn verify` |
| Developer machine, PostgreSQL | local PostgreSQL | `DB_URL=jdbc:postgresql://localhost:5432/nammabasket DB_USER=... DB_PASSWORD=... mvn verify` |
| Browser tests | the app started with any database | see [`e2e/README.md`](../e2e/README.md) |
| CI (every push and pull request) | H2 **and** PostgreSQL 16 | `.github/workflows/tests.yml`, three jobs in parallel |

## 4. Entry and exit criteria

**Start testing a change when:** it compiles, and the person changing it has added or updated tests for the rule they changed.
**A change is done when:**
1. All four CI jobs are green (no test skipped or disabled to get there).
2. Every bug fix has a regression test that failed before the fix.
3. No open defect of severity Critical or Major.
4. The browser screenshots have been looked at for layout problems.

## 5. Requirements coverage (traceability)

Each line of the QA brief is mapped to the automated tests that prove it. IDs are listed in section 9.

| Requirement | Tests |
|---|---|
| **Authentication** | |
| Valid registration | AUTH-01, E2E-MARK-01 |
| Duplicate registration | SEC-06, SEC-07, ONB-03 |
| Invalid inputs | ERR-01, ERR-02, AUTH-11 |
| Incorrect login credentials | AUTH-05, SEC-02, SEC-09 |
| Unauthorized access | AUTH-06, AUTH-08, ADM-02, NOTE-05, SAVE-07 |
| Buyer accessing seller endpoints | ONB-05, CAT-04, STEP-10 |
| Seller modifying another seller's products | CAT-03, ONB-10 |
| **Products** | |
| Product creation | CAT-01 |
| Product update | CAT-13, CAT-06 |
| Invalid prices / negative stock | CAT-05, SEC-05, CAT-13 |
| Missing products | ERR-06, SAVE-02 |
| Inactive products | CAT-06, ADM-07 |
| Unapproved shop products | CAT-02, CAT-08 |
| **Cart** | |
| Adding / updating / removing | ERR-03, ERR-04 |
| Invalid quantities | CRIT-05 |
| Price changes | CRIT-07, CRIT-08 |
| Out-of-stock products | ERR-05, CRIT-04, SAVE-04 |
| **Orders** | |
| Successful order creation | MV-01, CHK-04, E2E-MARK-04 |
| Insufficient stock | CRIT-04, MV-05 |
| Two buyers buying the last item | CRIT-01 (and SLOT-07 for the last slot place) |
| Duplicate checkout requests | CRIT-02, CRIT-03 |
| Failed checkout | LIFE-02, MV-05, SLOT-03, MV-08 |
| Invalid / expired coupon | CHK-07, ADM-06 |
| Unauthorized order access | SEC-01, MV-09, INV-05 |
| Invalid order status transitions | STEP-02, STEP-03, STEP-05, SEC-04 |
| Cancellation and inventory restoration | LIFE-04, STEP-04, STEP-06 |
| Repeated cancellation | STEP-06, STEP-04 |
| **Multi-vendor** | |
| Orders grouped by the correct shop | MV-01, E2E-MARK-04 |
| Sellers seeing only their own orders | MV-09 |
| Different shop delivery fees | MV-01, MV-02 |
| Inactive shops rejecting new orders | MV-06, CAT-07, CAT-08 |
| Correct totals for multiple shops | MV-01, MV-07 |
| **Error handling** | |
| Unknown endpoint returns 404 | SMART-09, E2E-PAGE-14 |
| Validation failures return client errors | ERR-01, ERR-07, ERR-08, CRIT-06, CRIT-09 |
| Server errors don't leak sensitive information | ERR-09, ERR-08, ONB-12 |
| Security headers, session handling, XSS (Phase 10 review) | HDR-01 … HDR-04, E2E-PAGE-16, E2E-PAGE-17 |
| Database-level protection (PostgreSQL-compatible test configuration) | DB-01, DB-02, DB-03 |
| **New features** | |
| Ratings & reviews | REV-01 … REV-06, E2E-MARK-06, E2E-MARK-07 |
| Time slots | SLOT-01 … SLOT-07, SLOTU-01 … SLOTU-04, E2E-MARK-04 |
| Saved for later | SAVE-01 … SAVE-07, E2E-MARK-03, E2E-MARK-08 |
| Notifications | NOTE-01 … NOTE-05, E2E-MARK-05, E2E-MARK-06 |
| GST invoice | INV-01 … INV-06, E2E-MARK-05 |

## 6. Detailed test cases (high-risk scenarios)

Written out in full so they can also be run by hand on the live site. "Actual result" is what the automated test observed in the CI run named in section 9.

Severity of a failure: **Critical** money or data wrong, or security hole · **Major** a feature can't be used · **Minor** works, with a workaround · **Trivial** looks wrong only.
Priority: **P1** fix before release · **P2** fix this phase · **P3** when convenient.

| ID | Scenario | Preconditions | Test data | Steps | Expected result | Actual result | Status | Severity if it fails | Priority |
|---|---|---|---|---|---|---|---|---|---|
| CRIT-01 | Two buyers buy the last item at the same moment | Product with stock 1 | 2 new buyers, qty 1 each | Both put it in the cart; both press Place order at the same instant | Exactly one order; stock 0, never −1; the other buyer's checkout is refused | As expected | Pass | Critical | P1 |
| CRIT-02 | Double click on Place order | Cart with 1 item | Same `requestId` sent twice | Submit checkout twice with the same id | One order; the second call returns the same order; stock taken once | As expected | Pass | Critical | P1 |
| CRIT-08 | Price tampering | Cart total Rs 100 | `expectedTotal` = 1.00 | Send checkout with a lower total | 409, no order, nothing reserved (stock still 100) | As expected | Pass | Critical | P1 |
| SEC-01 | Another buyer's order | Buyer A has order #N | Buyer B's session | B opens, cancels and pays order #N | 403 for open, cancel and pay; 401 when logged out; A's order unchanged | As expected | Pass | Critical | P1 |
| CAT-03 | Seller edits another shop's product | Sellers A and B, B has product P | A's session | A reads, edits, restocks and switches off P | 404 every time; P unchanged | As expected | Pass | Critical | P1 |
| MV-01 | Basket from two shops | Shops X (fee 20) and Y (fee 30), both deliver to the PIN | 1 item from each | Quote, then check out | Bill shows each shop's fee; one checkout creates 2 orders, one per shop, totals add up | As expected | Pass | Critical | P1 |
| MV-05 | One shop's item fails at checkout | Basket from X and Y; Y's item goes out of stock | — | Check out | 400; no order for any shop; stock unchanged; cart kept | As expected | Pass | Critical | P1 |
| MV-09 | Sellers see only their own orders | Shared basket made 2 orders | Seller X session | X lists orders, opens Y's order id | X sees only X's order; Y's order gives 404 | As expected | Pass | Critical | P1 |
| STEP-06 | Repeated cancellation | Order ACCEPTED, qty 2, stock 8 | — | Buyer cancels twice | First 200, stock back to 10; second 409, stock still 10 | As expected | Pass | Critical | P1 |
| SLOT-07 | Two buyers take the last slot place | Shop with slots, capacity 1 | 2 buyers, tomorrow 18:00 | Both check out together with that slot | Exactly one 200 and one 409 | As expected | Pass | Major | P1 |
| SLOT-03 | Booking a full slot | Slot already full | — | Check out with that slot | 409; no order, stock unchanged, cart kept; after a cancel the place is free again | As expected | Pass | Major | P1 |
| SLOTU-03 | Slot cut-off boundary | 7:00–9:00 slot today | Clock at 5:59, 6:00, 6:01 | Ask if the slot can be booked | 5:59 yes; 6:00 and 6:01 no | As expected | Pass | Minor | P2 |
| REV-01 | Review only after delivery | Buyer with an undelivered order | 5 stars, "Lovely" | Review before delivery; a buyer who never bought it reviews; review after delivery | 403, 403, then 201 with the trimmed comment; average 5.0 from 1 | As expected | Pass | Major | P1 |
| REV-05 | Admin hides a review | Visible 1-star review | Reason "Abusive language" | Buyer tries to hide it; admin hides with a blank reason; hides; hides again; shows it | 403; 400; 200 and it stops counting; 409; 200 and it counts again | As expected | Pass | Major | P2 |
| INV-03 | Coupon spread across invoice lines | Items Rs 300 (5% GST) + Rs 100 (18% GST), FRESH50 | — | Deliver, open invoice | Discounts 37.50 + 12.50; taxable 250.00 + GST 12.50; taxable 74.15 + GST 13.35; taxable + GST = Rs 350 | As expected | Pass | Critical | P1 |
| INV-05 | Who can open an invoice | Delivered order | Buyer, its shop, admin, other buyer, other shop | Open the invoice as each | 200, 200, 200, 403, 404 | As expected | Pass | Critical | P1 |
| SAVE-04 | Saved item goes out of stock | Saved product, then stock 0 | — | List saved; move to cart | List says "Out of stock"; move refused 400; item stays saved; cart empty | As expected | Pass | Minor | P2 |
| NOTE-04 | Marking notifications read | Buyer with 4 unread | Other buyer's session | Other buyer marks #1; buyer marks #1; buyer marks all | 404 and still 4 unread; 3 unread; 0 unread | As expected | Pass | Major | P2 |
| ERR-09 | Server error must not leak | Test-only endpoint throws with a DB address and password in the message | — | Call it | 500 with "Something went wrong. Please try again."; no exception name, `jdbc:` or password in the body | As expected | Pass | Critical | P1 |
| E2E-MARK-04 | Real browser checkout across two shops with a slot | App running on PostgreSQL with demo shops | New buyer, PIN 560041 | Add from 2 shops, pick tomorrow's first slot, pay cash, place order | Lands on the order page; "2 orders, one per shop"; order shows the slot | As expected | Pass | Critical | P1 |
| E2E-MARK-05 | Shop owner handles the order in the browser | Order from E2E-MARK-04 | Demo seller login | Bell shows "New order"; Accept → Prepare → Out for delivery → Delivered; open invoice | Each step accepted; invoice titled "Tax invoice" with the shop's GSTIN | As expected | Pass | Critical | P1 |

## 7. Defect log

Real problems found while building and testing NammaBasket. Each fixed bug has a regression test.

| ID | Found by | Summary | Severity | Priority | Status | Regression test |
|---|---|---|---|---|---|---|
| DEF-01 | CI (all tests failed at start-up) | App would not start: `findByIdAndShopId` was read by Spring Data as the JSON getter `getShopId()` instead of `shop.id` | Critical | P1 | Fixed (explicit query) | every API test starts the app |
| DEF-02 | Review of invoice code | Invoice CGST/SGST totals were worked out from the grand total, so they could differ by Rs 0.01 from the rows above them | Minor | P2 | Fixed (totals are the sum of the rows) | INV-03 |
| DEF-03 | Demo check | Demo product photos were hot-linked from Wikimedia and could stop loading at any time | Major | P2 | Fixed (99 licensed photos bundled in the app, credits page added) | E2E-MARK-02, E2E-PAGE-15 |
| DEF-04 | Writing the page checks | 10 pages had no `h1` main heading, so screen-reader users couldn't jump to the page title | Minor | P2 | Fixed | E2E-PAGE-01 … 14 |
| DEF-05 | Browser test screenshots | After adding Saved and the bell, header links wrapped onto two lines at 1280 px | Trivial | P3 | Fixed (Budget moved to the footer, links don't wrap) | screenshot review |
| DEF-06 | Browser test screenshots | Notification text was cut off in the bell panel (no-wrap rule from the header leaked into it) | Minor | P3 | Fixed | screenshot review |
| DEF-07 | Browser test design | Seller "New" filter hides an order as soon as it is accepted, so the seller can't see it move along | Minor | P3 | Fixed (Phase 10: the dashboard follows the order to "In progress") | — |
| DEF-08 | Admin page check | The "reason" box for rejecting a shop showed before Reject was pressed | Trivial | P3 | Fixed | — |
| DEF-09 | Test review | Invoice test expected 404 for another buyer, but the app answers 403 there, like the order page itself | — (test defect) | P3 | Test corrected; kept consistent with SEC-01 | INV-05 |
| DEF-10 | Test isolation | A new test made a shop serving PIN 110001, which older tests use as "not served", so they failed depending on order | — (test defect) | P2 | Fixed (new shops use 403001) | CHK-01, CHK-02 |
| DEF-11 | FreshCart (earlier) | Unknown pages returned 500 instead of 404 | Major | P1 | Fixed | SMART-09, E2E-PAGE-14 |
| DEF-12 | Phase 10 security review | Stored XSS: page templates did not escape quotes, so a product or shop name containing `"` could add a script attribute on buyers' pages | Critical | P1 | Fixed (escapeHtml escapes quotes) | E2E-PAGE-17 |
| DEF-13 | Phase 10 security review | Photo fallback built JavaScript from data (`onerror="…'${emoji}'"`) | Major | P1 | Fixed (value read from a data attribute) | E2E-PAGE-17 |
| DEF-14 | Phase 10 security review | No browser security headers (CSP, frame protection, nosniff) when served from Render | Major | P1 | Fixed (SecurityHeadersFilter + Netlify/Vercel headers) | HDR-01, HDR-02, E2E-PAGE-16 |
| DEF-15 | Phase 10 security review | Session id not renewed at sign-up (session fixation) | Major | P2 | Fixed | HDR-03 |
| DEF-16 | Phase 10 security review | Random admin password printed in the logs for production databases | Major | P1 | Fixed (only for the local in-memory database) | code review |
| DEF-17 | axe scan (browser tests) | Admin tab strip used role="tablist" without tabs (critical accessibility error) | Minor | P2 | Fixed (labelled button group with aria-pressed) | axe scan in E2E-MARK-07 |
| DEF-18 | axe scan (browser tests) | Header "Admin" link counted as a link inside text on phones (serious accessibility error) | Minor | P3 | Fixed | axe scan in E2E-MARK-09 |
| DEF-19 | Keyboard test | Escape closed the PIN-code box and bell but left keyboard focus inside the hidden box; "Skip to content" did not move focus | Minor | P3 | Fixed | E2E-PAGE-18 |

## 8. Risks and what is still missing

| Item | Risk | Plan |
|---|---|---|
| No manual screen-reader pass | Some assistive-technology problems may remain | Manual NVDA/VoiceOver check before a public launch |
| Spring Boot 3.3 gets no more free updates | Future vulnerabilities won't be patched | Upgrade to a supported line as a separate change (see FINAL_REVIEW.md) |
| Browser tests run in Chrome only | Safari/Firefox layout differences | Manual check before release |
| Slot times use India time (Asia/Kolkata) on the server | Shops outside India aren't supported | Out of scope for this project |

## 9. Automated test inventory

Generated from the test code by `python3 tools/testplan/inventory.py "<run>"`, so the list always matches what CI runs.

_164 API tests (each run twice: on H2 and on PostgreSQL 16) and 27 browser tests. "Pass" = passed in CI run [#38415846620](https://github.com/suryaprasanth740/grocery-web-app/actions/runs/38415846620) (commit b55d760, 13 Oct 2026)._

#### Authentication & roles (`AuthRolesTest`, 11 tests)

| ID | What the test checks | Result |
|---|---|---|
| AUTH-01 | Sign-up creates a BUYER account and stores a BCrypt hash, never the password | Pass |
| AUTH-02 | Sign-up ignores a 'role' field in the request: nobody can make themselves admin or seller | Pass |
| AUTH-03 | Old FreshCart password hash still logs in, and is replaced by BCrypt on that login | Pass |
| AUTH-04 | Old role text 'USER' is read as BUYER, and unknown role text never gives extra rights | Pass |
| AUTH-05 | Unknown email and wrong password get the same 401 and the same message | Pass |
| AUTH-06 | Not logged in: shopping endpoints return 401 | Pass |
| AUTH-07 | Seller and admin accounts get 403 on buyer-only endpoints (cart, orders) | Pass |
| AUTH-08 | Buyer and seller accounts get 403 on admin endpoints | Pass |
| AUTH-09 | Suspended account: cannot log in, and an open session stops working (403) | Pass |
| AUTH-10 | CSRF: a POST without the X-Requested-With header is refused (403) and changes nothing | Pass |
| AUTH-11 | Password longer than 72 bytes is refused with 400, not a server error | Pass |

#### Security & admin (`SecurityAndAdminTest`, 9 tests)

| ID | What the test checks | Result |
|---|---|---|
| SEC-01 | TC_10 A customer cannot see or cancel someone else's order (IDOR) | Pass |
| SEC-02 | TC_12 Five wrong passwords lock the account, even the right password is then refused | Pass |
| SEC-03 | Admin pages are for admins only | Pass |
| SEC-04 | Admin cannot skip steps: PLACED straight to DELIVERED is refused | Pass |
| SEC-05 | Admin product form rejects bad values (price 0, negative stock, GST 7%) | Pass |
| SEC-06 | Registering the same email twice (any capitals) is refused | Pass |
| SEC-07 | One Gmail inbox = one account (dots, +alias and googlemail.com are the same inbox) | Pass |
| SEC-08 | Other email providers: dots are NOT ignored (they can be different people) | Pass |
| SEC-09 | Wrong-password lock can't be dodged by adding dots to a Gmail address | Pass |

#### Input validation, cart & error handling (`InputAndErrorsTest`, 9 tests)

| ID | What the test checks | Result |
|---|---|---|
| ERR-01 | Sign-up with bad input is refused with 400 and the field's message; no account is created | Pass |
| ERR-02 | Login with empty fields is a clean 400, not a server error | Pass |
| ERR-03 | Cart: add, change the quantity, remove one item, clear - the cart matches after each step | Pass |
| ERR-04 | Cart: raising a quantity above the stock is refused (400); lowering it is always allowed | Pass |
| ERR-05 | Out of stock (0 left): adding to the cart is refused with 400 | Pass |
| ERR-06 | Missing product: 404 for the page and for adding it to a cart | Pass |
| ERR-07 | A wrong value in the address (/api/products/abc) is 400, and a wrong HTTP method is 405 | Pass |
| ERR-08 | Broken JSON is a 400 with a plain message that doesn't mention the JSON library | Pass |
| ERR-09 | An unexpected server error is a 500 with a generic message: no exception name, SQL address or password | Pass |

#### Stock, duplicates & price safety (`CriticalFixesTest`, 9 tests)

| ID | What the test checks | Result |
|---|---|---|
| CRIT-01 | TC_01 Two customers buy the LAST item at the same moment: only one succeeds | Pass |
| CRIT-02 | TC_02 Double click on Place Order (same request id) creates only ONE order | Pass |
| CRIT-03 | TC_02b Two checkouts of the same cart at the same time (two tabs) create only ONE order | Pass |
| CRIT-04 | TC_03 Cannot add more than the stock to the cart | Pass |
| CRIT-05 | TC_04 Quantity boundary: 20 allowed, 21 rejected, 0 rejected | Pass |
| CRIT-06 | TC_06 Address boundary: 300 characters OK, 301 rejected with 400 (not a 500 crash) | Pass |
| CRIT-07 | TC_08 Price changed after adding to cart: customer is warned and old total is refused | Pass |
| CRIT-08 | TC_09 Price tampering: a lower total sent by the browser is refused | Pass |
| CRIT-09 | Wrong payment method value gives a clean 400 | Pass |

#### Checkout: PIN, fees, coupons, COD (`CheckoutFeaturesTest`, 11 tests)

| ID | What the test checks | Result |
|---|---|---|
| CHK-01 | PIN code check: serviceable, not serviceable, and invalid formats | Pass |
| CHK-02 | Checkout to a PIN code we don't serve is refused | Pass |
| CHK-03 | Delivery fee boundary: Rs 499 pays Rs 30 delivery, Rs 500 gets free delivery | Pass |
| CHK-04 | Saved order has the same bill the customer saw (cart page and order match) | Pass |
| CHK-05 | GST already inside the price is shown: Rs 105 at 5% GST includes Rs 5 | Pass |
| CHK-06 | Coupon minimum order boundary: Rs 298 rejected, Rs 299 accepted (FRESH50) | Pass |
| CHK-07 | Expired and unknown coupons are rejected | Pass |
| CHK-08 | Percent coupon is capped: 10% of Rs 2000 gives only Rs 100 (SAVE10 cap) | Pass |
| CHK-09 | A flat coupon can never make the bill negative | Pass |
| CHK-10 | Coupon used once cannot be reused, but works again after that order is cancelled | Pass |
| CHK-11 | COD limit boundary: Rs 3000 allowed, Rs 3000.01 must use UPI | Pass |

#### Payment, cancel, refunds, expiry (`OrderLifecycleTest`, 10 tests)

| ID | What the test checks | Result |
|---|---|---|
| LIFE-01 | UPI success: order becomes PLACED and PAID | Pass |
| LIFE-02 | UPI failure: stock is released and the items go back to the cart | Pass |
| LIFE-03 | UPI timeout, then money arrives late: order expires and the money is REFUNDED | Pass |
| LIFE-04 | Cancel a placed COD order: stock comes back, nothing charged | Pass |
| LIFE-05 | Cancel once the shop starts preparing is refused; cancelling a PAID order refunds it | Pass |
| LIFE-06 | Report missing items: only after delivery, never more than ordered | Pass |
| LIFE-07 | Refund for a missing item takes the coupon discount into account | Pass |
| LIFE-08 | Reports after the 48-hour window are refused | Pass |
| LIFE-09 | Expiry boundary: expires today = cannot buy, expires tomorrow = can buy | Pass |
| LIFE-10 | Item that expires while sitting in the cart blocks checkout | Pass |

#### Shop order steps (`OrderTransitionsTest`, 10 tests)

| ID | What the test checks | Result |
|---|---|---|
| STEP-01 | Delivery order: PLACED -> ACCEPTED -> PREPARING -> OUT_FOR_DELIVERY -> DELIVERED; cash marked paid at the end | Pass |
| STEP-02 | Pickup order uses READY_FOR_PICKUP; OUT_FOR_DELIVERY is refused for pickup, and the other way round | Pass |
| STEP-03 | Skipping steps is refused: PLACED straight to PREPARING or DELIVERED gives 409 | Pass |
| STEP-04 | Shop rejects with a reason: order CANCELLED, buyer sees why, stock goes back; a reason is required | Pass |
| STEP-05 | A shop can't reject once it has started preparing, and can't use CANCELLED as a plain step | Pass |
| STEP-06 | Buyer cancels while ACCEPTED: stock back once; a repeated cancel is refused and changes nothing | Pass |
| STEP-07 | Unpaid UPI orders are hidden from the shop until the payment goes through | Pass |
| STEP-08 | Seller dashboard counts come from real orders: new, completed, sales today, best seller, low stock | Pass |
| STEP-09 | Order again: available items go back in the cart; a product switched off is skipped with a reason | Pass |
| STEP-10 | Buyers can't open the seller order screens (403) | Pass |

#### Multi-vendor checkout (`MultiShopCheckoutTest`, 9 tests)

| ID | What the test checks | Result |
|---|---|---|
| MV-01 | Two shops in one basket: the bill keeps each shop's fee, and checkout creates one order per shop | Pass |
| MV-02 | Pickup from a shop: no delivery fee, and the order is saved as PICKUP without an address | Pass |
| MV-03 | Delivery outside a shop's PIN codes is refused; choosing pickup for that shop works | Pass |
| MV-04 | A shop's minimum order is checked on its own items: below it, checkout is refused | Pass |
| MV-05 | All or nothing: if one shop's item can't be bought, no order is created for ANY shop | Pass |
| MV-06 | A shop that pauses orders blocks checkout of its items, with a clear reason | Pass |
| MV-07 | Coupon on a two-shop basket: discount split by item total, and it counts as ONE use | Pass |
| MV-08 | One UPI payment covers both shops: success marks both paid; failure frees both shops' stock | Pass |
| MV-09 | Each seller sees only their own shop's order from a shared basket; the other order is 404 | Pass |

#### Seller onboarding & shop approval (`SellerOnboardingTest`, 13 tests)

| ID | What the test checks | Result |
|---|---|---|
| ONB-01 | Seller application creates a SELLER account and a PENDING shop with the owner's details | Pass |
| ONB-02 | Bad shop details are refused with 400 and create no account (all-or-nothing) | Pass |
| ONB-03 | Applying with an email that already has an account is refused (409) | Pass |
| ONB-04 | A pending shop can't take orders yet: pausing/resuming is refused (409) | Pass |
| ONB-05 | Buyers and logged-out visitors can't use seller endpoints (403 / 401) | Pass |
| ONB-06 | Sellers and buyers can't approve shops or read the admin shop list (403) | Pass |
| ONB-07 | Admin approves: shop becomes APPROVED, can pause orders, and an audit row is written | Pass |
| ONB-08 | Admin rejects with a reason; the seller sees it, fixes the details and resubmits | Pass |
| ONB-09 | Suspend and reactivate a shop: only APPROVED can be suspended, only SUSPENDED reactivated | Pass |
| ONB-10 | A seller's edits only ever change their OWN shop | Pass |
| ONB-11 | Admin suspends a seller account: login refused; admins can't be suspended; audit written | Pass |
| ONB-12 | Admin account list and stats show real counts, and never a password hash | Pass |
| ONB-13 | Shop PIN-code rules: exact codes, 560xxx areas, and invalid input | Pass |

#### Seller catalogue & photos (`SellerCatalogTest`, 13 tests)

| ID | What the test checks | Result |
|---|---|---|
| CAT-01 | Approved seller adds a product: it appears in the catalogue with its shop's name and the MRP discount | Pass |
| CAT-02 | A pending shop's products are hidden from buyers (404) and can't be added to a cart | Pass |
| CAT-03 | Seller A can't read, edit, restock or switch off seller B's product (404), and B's product is unchanged | Pass |
| CAT-04 | Buyers and logged-out visitors can't use seller product endpoints (403 / 401) | Pass |
| CAT-05 | Product validation: price 0, negative stock, MRP below price, GST 7% and no category are refused (400) | Pass |
| CAT-06 | Switching a product off hides it and blocks adding it; switching it back on shows it again | Pass |
| CAT-07 | Paused shop: products stay visible but marked, and adding to cart is refused with a clear reason | Pass |
| CAT-08 | Suspended shop: all its products disappear from the catalogue | Pass |
| CAT-09 | Photo upload: PNG accepted and served back; a text file (415) and a 301 KB file (413) refused | Pass |
| CAT-10 | A product may only use photos its own shop uploaded: another shop's photo or an outside link is refused | Pass |
| CAT-11 | Shop search by PIN code: only approved shops that deliver there, nearest first when location is shared | Pass |
| CAT-12 | Paged product search returns one page with totals | Pass |
| CAT-13 | Seller edits their own product: new name, price and stock are saved and buyers see the new price; restock sets the stock | Pass |

#### Admin: reports, categories, promotions (`AdminToolsTest`, 9 tests)

| ID | What the test checks | Result |
|---|---|---|
| ADM-01 | Buyer reports a product: admin sees it with shop and product names; a second open report is refused (409) | Pass |
| ADM-02 | Reports: only buyers can send them (seller 403); a product from another shop is refused (404) | Pass |
| ADM-03 | Resolving a report with HIDE_PRODUCT hides it from buyers and writes the audit trail; closing twice is refused | Pass |
| ADM-04 | Resolving a shop report with SUSPEND_SHOP suspends the shop, so its products disappear | Pass |
| ADM-05 | Categories: add, rename, duplicate names refused (409), and a category with products can't be deleted | Pass |
| ADM-06 | Promotions: a new coupon works at checkout; bad percent coupons and duplicate codes are refused | Pass |
| ADM-07 | Admin hides any shop's product with a reason (required) and can show it again | Pass |
| ADM-08 | Admin order search filters by shop and status; stats include last-7-days value and open reports | Pass |
| ADM-09 | Report reasons list is public and the dispute list shows the order and shop of each problem | Pass |

#### Smart shopping (`SmartShoppingTest`, 9 tests)

| ID | What the test checks | Result |
|---|---|---|
| SMART-01 | Shopping list: quantities, units and Indian-language words are understood | Pass |
| SMART-02 | Shopping list: the most specific product wins, typos are forgiven, unknown items are flagged | Pass |
| SMART-03 | Shopping list: empty text is refused | Pass |
| SMART-04 | Add all to cart: good items are added, out-of-stock items are skipped with a reason | Pass |
| SMART-05 | Recipes: ingredients scale with the number of people and become whole packs | Pass |
| SMART-06 | Budget: spent this month, remaining, and a warning when the cart goes over | Pass |
| SMART-07 | Budget: cheaper swap is suggested and can be applied in one click | Pass |
| SMART-08 | Running low: learns how often you buy something and says when it's due | Pass |
| SMART-09 | A page or API path that doesn't exist returns 404, not 500 | Pass |

#### Ratings & reviews (`ReviewsTest`, 6 tests)

| ID | What the test checks | Result |
|---|---|---|
| REV-01 | Only a buyer whose order was delivered can review: before delivery 403, after delivery 201 | Pass |
| REV-02 | Reviewing again updates the same review (200): the average uses only the new stars | Pass |
| REV-03 | Stars must be 1-5 and comments at most 500 characters (400); sellers 403; logged out 401 | Pass |
| REV-04 | Reviews show only the buyer's first name (never the email); the shop average combines its products | Pass |
| REV-05 | Admin hides a review (reason required): it disappears and stops counting; hiding twice 409; showing restores it | Pass |
| REV-06 | A buyer's own reviews list shows what they rated, for the order page | Pass |

#### Delivery / pickup time slots (`TimeSlotsTest`, 7 tests)

| ID | What the test checks | Result |
|---|---|---|
| SLOT-01 | Slots list: today and tomorrow, each slot with places left; a shop without slots says so | Pass |
| SLOT-02 | Booking a slot saves it on the order and takes one place; no slot = 'as soon as possible' | Pass |
| SLOT-03 | A full slot is refused (409) and nothing is taken: no order, stock unchanged, cart kept; a cancel frees the place | Pass |
| SLOT-04 | Bad slot values: wrong format or off-boundary time 400, past or too far ahead 409, shop without slots 400 | Pass |
| SLOT-05 | A slot for a shop that isn't in the basket is refused (400) | Pass |
| SLOT-06 | Seller settings: slot capacity must be 1 to 50 (400 otherwise) | Pass |
| SLOT-07 | Two buyers race for the LAST place in a slot at the same moment: exactly one gets it | Pass |

#### Time-slot rules (pure unit tests) (`SlotRulesTest`, 4 tests)

| ID | What the test checks | Result |
|---|---|---|
| SLOTU-01 | A 7:00-22:00 shop gets 2-hour slots, and a last 1-hour slot 21:00-22:00 | Pass |
| SLOTU-02 | A leftover shorter than 1 hour is not offered; a day ending at 23:59 never wraps past midnight | Pass |
| SLOTU-03 | Cut-off boundary: 61 minutes before the start can book, 60 and 59 minutes before cannot | Pass |
| SLOTU-04 | Only today and tomorrow: yesterday and the day after tomorrow are refused | Pass |

#### Saved for later (`SavedItemsTest`, 7 tests)

| ID | What the test checks | Result |
|---|---|---|
| SAVE-01 | Saving is idempotent: first save 201, saving again 200, and the product is listed once | Pass |
| SAVE-02 | A product that doesn't exist or is switched off can't be saved (404) | Pass |
| SAVE-03 | Move to cart: the item leaves the saved list and is in the cart with quantity 1 | Pass |
| SAVE-04 | Out of stock: the list says why, moving to cart is refused (400) and the item STAYS saved | Pass |
| SAVE-05 | Save for later from the cart: removed from the cart, added to the saved list | Pass |
| SAVE-06 | Remove: 200 and gone; removing something not saved is 404 | Pass |
| SAVE-07 | Only buyers have a saved list: logged out 401, seller and admin 403 | Pass |

#### Notifications (`NotificationsTest`, 5 tests)

| ID | What the test checks | Result |
|---|---|---|
| NOTE-01 | Shop accepts the order: the buyer gets one unread message that links to the order | Pass |
| NOTE-02 | The shop owner is told about a new cash order; a rejected order tells the buyer the reason | Pass |
| NOTE-03 | A seller is told when the admin approves the shop | Pass |
| NOTE-04 | Mark one read, then all read; someone else's notification can't be marked (404) | Pass |
| NOTE-05 | Logged-out visitors get 401 from the bell | Pass |

#### GST invoice (`InvoiceTest`, 6 tests)

| ID | What the test checks | Result |
|---|---|---|
| INV-01 | Not delivered yet: the invoice is refused with 409 | Pass |
| INV-02 | GST is taken out of the price: 2 x Rs 105 at 5% = taxable Rs 200 + GST Rs 10 (CGST 5 + SGST 5) | Pass |
| INV-03 | A coupon is shared across lines by value: Rs 50 off Rs 300 + Rs 100 = Rs 37.50 + Rs 12.50, and GST is on what was paid | Pass |
| INV-04 | Shop without a GSTIN prints a bill of supply; with a GSTIN, a tax invoice showing it | Pass |
| INV-05 | Who can see it: the buyer, the selling shop and admins; another buyer 403, another shop 404 | Pass |
| INV-06 | GSTIN format is checked when the shop saves it: a wrong one is refused with 400 | Pass |

#### Database: migrations and constraints (`DatabaseMigrationTest`, 3 tests)

| ID | What the test checks | Result |
|---|---|---|
| DB-01 | Flyway applied every migration successfully, starting with V1 | Pass |
| DB-02 | The database itself refuses negative stock and unknown order statuses, even if the Java checks were skipped | Pass |
| DB-03 | Two accounts can't share one email: the database has a unique key, not only the Java check | Pass |

#### Security headers and sessions (`SecurityHeadersTest`, 4 tests)

| ID | What the test checks | Result |
|---|---|---|
| HDR-01 | Pages get a Content-Security-Policy that forbids framing, plus nosniff, DENY and a referrer policy | Pass |
| HDR-02 | API answers (orders, invoices...) are never stored in caches; error answers get the headers too | Pass |
| HDR-03 | Signing up gives the browser a NEW session id (an id planted before sign-up is useless: no session fixation) | Pass |
| HDR-04 | Logging in also gives a new session id, and logging out ends the session | Pass |

#### Browser: `e2e/tests/marketplace.spec.js` (9 tests)

| ID | What the test checks | Result |
|---|---|---|
| E2E-MARK-01 | Buyer signs up, sets a PIN code and sees the shops that deliver there | Pass |
| E2E-MARK-02 | Product photos are real and load from our own site | Pass |
| E2E-MARK-03 | Heart saves a product; Saved page moves it to the cart | Pass |
| E2E-MARK-04 | Basket from two shops, a booked slot, one checkout = two orders | Pass |
| E2E-MARK-05 | Shop owner is notified, moves the order through every step and opens the tax invoice | Pass |
| E2E-MARK-06 | Buyer sees the update in the bell, rates the product, and the shop page shows it | Pass |
| E2E-MARK-07 | Admin hides the review; it leaves the product rating | Pass |
| E2E-MARK-08 | Cart item can be saved for later from the cart page | Pass |
| E2E-MARK-09 | Logged-in pages fit a phone screen (375 px) and pass the accessibility scan | Pass |

#### Browser: `e2e/tests/pages.spec.js` (18 tests)

| ID | What the test checks | Result |
|---|---|---|
| E2E-PAGE-01 | / opens cleanly on desktop and phone | Pass |
| E2E-PAGE-02 | /shops.html opens cleanly on desktop and phone | Pass |
| E2E-PAGE-03 | /products.html opens cleanly on desktop and phone | Pass |
| E2E-PAGE-04 | /recipes.html opens cleanly on desktop and phone | Pass |
| E2E-PAGE-05 | /list.html opens cleanly on desktop and phone | Pass |
| E2E-PAGE-06 | /shop.html?s=hasiru-tarkari-angadi opens cleanly on desktop and phone | Pass |
| E2E-PAGE-07 | /sell.html opens cleanly on desktop and phone | Pass |
| E2E-PAGE-08 | /login.html opens cleanly on desktop and phone | Pass |
| E2E-PAGE-09 | /register.html opens cleanly on desktop and phone | Pass |
| E2E-PAGE-10 | /help.html opens cleanly on desktop and phone | Pass |
| E2E-PAGE-11 | /privacy.html opens cleanly on desktop and phone | Pass |
| E2E-PAGE-12 | /terms.html opens cleanly on desktop and phone | Pass |
| E2E-PAGE-13 | /credits.html opens cleanly on desktop and phone | Pass |
| E2E-PAGE-14 | /no-such-page.html opens cleanly on desktop and phone | Pass |
| E2E-PAGE-15 | Photo credits list every bundled photo with its licence | Pass |
| E2E-PAGE-16 | Security headers are sent with pages and API answers | Pass |
| E2E-PAGE-17 | Hostile shop or product text is shown as text and never runs as code (XSS) | Pass |
| E2E-PAGE-18 | Keyboard only: skip link, PIN code box and Escape all work without a mouse | Pass |
