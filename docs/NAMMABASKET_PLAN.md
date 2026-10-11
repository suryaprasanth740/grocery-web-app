# NammaBasket — Marketplace Plan

*Your Neighbourhood Shops, All in One Basket.*

This file covers Phase 1 (what FreshCart is today) and Phase 2 (the proposed design for turning it into a multi-vendor marketplace). Nothing has been changed in the code yet. Decisions confirmed by the owner on 11 Oct 2026: **A** one product row per shop, **B** one basket grouped by shop, **C** images stored in PostgreSQL, and code delivered on a `nammabasket` branch through the Claude GitHub App, so CI runs on every push.

---

## Phase 1 — FreshCart today (inspected at commit `21abd71`)

### Frontend
- **Framework: none.** Plain HTML, CSS and JavaScript, with no build step. React is not used.
- **12 pages** in `src/main/resources/static/`:
  - shopping: `index`, `products`, `cart`, `checkout`
  - account: `login`, `register`, `orders`, `order`
  - admin: `admin`
  - smart shopping: `list`, `recipes`, `budget`
- **Shared code:**
  - `css/style.css`: one stylesheet for all pages
  - `js/api.js`: a `fetch` wrapper that retries GET requests while Render wakes up and never retries POST requests
  - `js/nav.js`: the navigation bar
  - `js/shop.js`: shared shop functions
- **Size:** about 3,700 lines.
- **Hosting:**
  - Spring Boot serves the pages itself on Render.
  - Copies of the frontend can also run on Vercel or Netlify. Both forward `/api/*` to Render, so the browser only ever talks to one domain and the session cookie keeps working.

### Backend
- **Stack:** Java 17, Spring Boot 3.3.4, Spring Data JPA, Bean Validation, Maven, Docker.
- **Layers:**
  - 10 controllers, about 40 REST endpoints, under `/api/auth`, `/api/products`, `/api/categories`, `/api/cart`, `/api/orders`, `/api/coupons`, `/api/delivery`, `/api/admin`, plus the smart-shopping endpoints
  - 10 services, 8 repositories
  - `GlobalExceptionHandler`, which returns clean JSON errors: 400/401/403/404/409, and a generic 500 with no stack trace
- **Size:** about 4,800 lines of Java.

### Database
- **H2 in memory** by default, for local development.
- **PostgreSQL** in production, set through `DB_URL` (Neon, per the README). A MySQL driver is also included.
- **Schema:** created by Hibernate with `ddl-auto=update`. There are no migration files.
- **Tables:**
  - `users`, `categories`, `products` (with `CHECK stock >= 0`), `cart_items`, `coupons`
  - `orders` (with a `@Version` column and a unique `(user_id, request_id)` pair), `order_items`, `order_issues`
- **Money:** `BigDecimal(10,2)` throughout.
- **Order history:** order items keep a snapshot of the product name, price and GST, so editing a product doesn't change old orders.

### Login and security
- **Login state:** a server-side `HttpSession` cookie. Spring Security is not used.
- **Roles:** a text field, `USER` or `ADMIN`. Checked in `SessionUtil.requireLoggedInUser` and `requireAdmin`.
- **Passwords:** a custom salted SHA-256 hash, repeated 10,000 times (`PasswordUtil`). Never stored as plain text, but this isn't an established password encoder.
- **Login lockout:** after 5 wrong passwords the account is locked for 15 minutes. The lock is kept in memory, so it resets when the app restarts.
- **Login errors:** the same "Invalid email or password" message for a wrong email and a wrong password.
- **Admin account:** created once at startup from `ADMIN_EMAIL` / `ADMIN_PASSWORD`. There is no public admin sign-up.
- **Order privacy:** customers can only see their own orders (a test covers this).

### What already works well (keep all of it)
- Atomic stock reservation, `UPDATE … SET stock = stock - :qty WHERE stock >= :qty`, so two buyers can never both get the last item.
- Duplicate orders are blocked by a request id per checkout.
- Server-side bill calculation, detection of price changes after adding to the cart, and refusal of a tampered total.
- Coupons (minimum order, percentage cap, single use, expiry), a PIN-code check, the free-delivery threshold, and a COD limit.
- Demo UPI payment flow with timeout, late payment and refund handling. The screen is labelled "Demo UPI app".
- Cancel rules, missing-item reports with a 48-hour window, and expiry-date checks.
- Smart shopping:
  - paste a list, which understands Kannada, Tamil, Hindi and Telugu words
  - recipes and festival kits
  - budget mode with cheaper swaps
  - "running low" reminders
- 48 JUnit 5 + MockMvc tests in 5 classes.
  - The GitHub Actions workflow "Build and test" **passed** on the latest `main` commit (30 Sept 2026).
  - **They could not be run in this workspace:** Maven Central is blocked by the network policy (HTTP 403).

### Gaps compared with the NammaBasket brief
| Area | Today | Needed |
|---|---|---|
| Vendors | One store; delivery rules are global settings (`ShopRules`) | Many shops, each with its own rules |
| Roles | USER, ADMIN | BUYER, SELLER, ADMIN; seller applications with approval |
| Password hashing | Custom SHA-256 | An established encoder (BCrypt) |
| Schema changes | `ddl-auto=update` | Versioned migrations (Flyway), safe for the live Neon data |
| Test database | H2 in MySQL mode | PostgreSQL wherever database behaviour matters |
| Login lockout | In memory | Kept in the database (survives restarts) |
| Branding | FreshCart | NammaBasket |
| Order statuses | PLACED → PACKED → OUT_FOR_DELIVERY → DELIVERED | Seller accept/reject, preparing, pickup |

---

## Phase 2 — Proposed design

### Guiding choices
1. **Keep the stack.** Spring Boot + JPA + PostgreSQL and the plain HTML/CSS/JS frontend. No React, so there's nothing new to learn and no build step to break deployment. Reusable frontend pieces (toasts, skeleton loaders, dialogs, product and shop cards) go into small shared JS files.
2. **Keep session cookies.** They already work with the Vercel/Netlify proxy.
   - Add **`spring-security-crypto` only**, for `BCryptPasswordEncoder`. The full Spring Security framework isn't needed for this.
   - Old SHA-256 hashes are upgraded to BCrypt automatically the next time each user logs in, so no one is locked out.
3. **Authorization is enforced on the server**, in one place per role:
   - `AuthGuard.requireBuyer` / `requireSeller` / `requireApprovedSeller` / `requireAdmin`
   - **plus ownership checks in the services**: a seller can only load products and orders where `shop.owner = me`, otherwise 404/403.
4. **Work on a branch** (`nammabasket`). The live FreshCart site stays untouched until the marketplace passes its tests.

### Roles and registration
- **BUYER:** name, email or phone, password. The address is asked for at checkout.
- **SELLER:**
  - Registration creates a `User(role=SELLER)` and a `Shop(status=PENDING)` in one transaction.
  - Shop details: shop name, phone, address, PIN code, service PIN codes, category, opening hours.
  - The seller can log in and edit the shop, but can't list products publicly or receive orders until an admin approves.
- **ADMIN:** created from environment variables as today. Never self-assignable, because role changes only happen in admin endpoints.
- **Protections:**
  - Login lockout moves to a `login_attempts` table.
  - Login errors stay generic.
  - A CSRF guard: every state-changing request must carry the `X-Requested-With` header that `api.js` already controls, and the session cookie is set to `SameSite=Lax`.

### Data model (changes)
| Entity | Change |
|---|---|
| `User` | role becomes `BUYER`/`SELLER`/`ADMIN` (existing `USER` rows are migrated to `BUYER`); add `phone`, `status` (ACTIVE/SUSPENDED) |
| **`Shop`** (new) | owner (User), name, slug, phone, address, pincode, `service_pincodes`, optional lat/lng, category, opening hours, delivery fee, free-delivery threshold, minimum order, `delivery`/`pickup` flags, `accepting_orders`, `status` (PENDING/APPROVED/REJECTED/SUSPENDED), rejection reason, logo |
| `Product` | add `shop_id` (required), `brand`, `active`, `discount_percent`; index on `(shop_id, active)`, `(category_id)`, `lower(name)` |
| `CartItem` | unchanged: the cart already holds products, and each product now knows its shop |
| **`Checkout`** (new) | one per "Place order" click: buyer, `request_id` (unique per buyer), total, payment method/status |
| `Order` | becomes **one shop's part of a checkout**: add `shop_id`, `checkout_id`, `fulfilment` (DELIVERY/PICKUP), `rejection_reason`, `stock_released` flag |
| `OrderItem` | unchanged (already a snapshot); add `unit` to the snapshot |
| `Coupon` | add optional `shop_id` (shop coupon) — null = platform coupon |
| **`AuditLog`** (new) | who, action, target, details, time: shop approvals, suspensions, refunds |
| **`Notification`** (new) | in-app notices for sellers (new order) and buyers (status change) |
| **`Report`** (new) | a buyer reports a shop or product; the admin reviews it |
| `OrderIssue` | kept as the "dispute" for missing items |

`Inventory` stays as `Product.stock`. A separate table would duplicate it with no benefit while each product belongs to one shop.

**Decision A: one product row per shop (recommended)**, or a shared master catalogue with per-shop listings.
- With per-shop products, "Amul Milk 500 ml" at two shops is two rows. It's simple, each seller fully owns their listing, and the existing stock locking works unchanged.
- A master catalogue makes price comparison nicer but adds a whole extra layer.

### Cart and checkout (multi-store)
**Decision B: one basket grouped by shop (recommended)**, or a separate basket for each shop.

How the recommended version works:
- The cart page shows **one section per shop**, each with its own items, delivery fee or "pickup", the minimum-order check and a subtotal. A grand total sits at the bottom.
- **"Place order" creates one `Checkout` and one `Order` per shop in a single database transaction.**
  - Stock for every item is reserved with the existing atomic update.
  - If any item fails (out of stock, price changed, shop closed, below the shop's minimum), the whole transaction rolls back and nothing is reserved or charged. The buyer sees exactly which item failed.
- **Double clicks** are blocked by the existing `request_id`, now on `Checkout`.
- **Coupons:**
  - A shop coupon applies only to that shop's order.
  - A platform coupon's discount is split across the shop orders in proportion to their subtotals and stored per order, so a later cancellation or refund on one shop's order stays correct.
- **Payment:**
  - COD is per order.
  - **Demo UPI** pays the whole checkout once. Failure or timeout releases stock for all of its orders (same logic as today).
  - It stays clearly labelled as a demo. No real gateway is added unless you ask, since that needs credentials.

### Order statuses
```
DELIVERY: PLACED → ACCEPTED → PREPARING → OUT_FOR_DELIVERY → DELIVERED
PICKUP:   PLACED → ACCEPTED → PREPARING → READY_FOR_PICKUP → DELIVERED (handed over)
Any time before PREPARING: seller REJECTS (with reason) or buyer CANCELS → CANCELLED
UPI only: PAYMENT_PENDING → PLACED, or PAYMENT_FAILED
```
- Status changes are checked against an allowed-transitions table on the server. Skipping a step is refused with 409.
- Restoring stock is **idempotent**: it only happens when `stock_released` flips from false to true, inside the same transaction and under the `@Version` lock. Cancelling twice can never add stock twice.
- Existing `PACKED` orders are migrated to `PREPARING`.

### Discovery and location
- **PIN code first.** The buyer enters a PIN code, or the browser's location is used with permission. A shop is shown when the PIN code is in its `service_pincodes`.
- **Distance:** shown only when both the shop and the buyer have coordinates. It's computed on the server with the haversine formula, so **no maps API key is needed**.
- The buyer's exact location is never stored on an order or shown to sellers. Sellers only see the delivery address of their own orders.
- Search covers products and shops with partial matching. Only `APPROVED` shops that are accepting orders are shown, and only `active` products.

### Smart shopping (kept)
- Paste a list, recipes and festival kits, budget mode and "running low" keep working.
- They now search **within the shop the buyer picks** (or the nearest shop that has the items), so they never suggest a product a shop doesn't really stock.
- Cheaper swaps only suggest products from the same shop.

### Images
Render's free disk is wiped on every deploy, so uploaded files saved to disk would disappear.

**Decision C: store small images in PostgreSQL (recommended).**
- Limit 300 KB each, resized in the browser before upload.
- Served from `/api/images/{id}` with cache headers.
- No external service or credentials needed.
- The alternative is Cloudinary (free tier), which needs an account and API keys in Render's environment settings.

### Seller dashboard
- **Overview:** new, preparing and completed orders; sales today and this week; low-stock and out-of-stock items; recent activity. **All computed with SQL from real orders.** A shop with no orders gets an empty state, never sample numbers.
- **Shop settings, products** (add, edit, deactivate, stock, discount, image, search/filter/sort) and **orders** (accept/reject with reason, status steps).
- New-order notifications: the dashboard checks every 30 seconds and shows a toast.

### Admin dashboard
- Pending shop applications, with approve or reject plus a reason.
- Buyers and sellers, with suspend and reactivate.
- Categories, coupons as campaigns, reports and disputes, marketplace statistics.
- **Every sensitive action writes an `AuditLog` row.**

### Database migrations
- Add **Flyway**.
  - `V1__baseline.sql` = today's schema, marked as the baseline for the existing Neon database.
  - `V2__marketplace.sql` adds the new tables and columns.
  - `ddl-auto` changes from `update` to `validate`.
- **Existing live data is kept.** The migration creates one approved shop, "NammaBasket Demo Kirana", owned by the admin, and moves all current products into it. Old orders keep working.
- Demo data for local runs (a few clearly labelled demo shops) is created by the existing `DataInitializer`. It can be switched off with `APP_DEMO_DATA=false`.

### Testing
- **Keep all 48 tests.** Update them where the behaviour changes on purpose (statuses, per-shop delivery fee), and explain each change.
- **New test classes:**
  - `AuthRolesTest`: buyer/seller/admin access, a buyer calling seller endpoints gets 403, BCrypt upgrade
  - `SellerOnboardingTest`: a pending shop can't sell; admin approval; audit log
  - `SellerOwnershipTest`: seller A can't edit seller B's products or orders
  - `MultiShopCheckoutTest`: grouping, per-shop fees and minimums, all-or-nothing rollback, coupon split
  - `OrderTransitionsTest`: accept/reject, pickup vs delivery paths, cancelling twice doesn't restore stock twice
  - `DiscoveryTest`: PIN code coverage, hiding unapproved or paused shops, search
- **The CI workflow** runs the suite against a **real PostgreSQL service container**, using the same Flyway migrations. Locally it still runs on H2 in PostgreSQL mode, so no Docker is needed on your computer.
- **`docs/TEST_PLAN.md`** will hold scenarios and cases with preconditions, data, steps, expected result, *actual result*, pass/fail, severity and priority. Pass/fail is filled in only from real CI runs.

### Delivery order
Phases 3–10 follow the brief.
- **Phases 4 and 6 come before the full redesign is finished.** The rebrand (Phase 3) is mostly CSS and copy, while roles, shops and checkout change the API that the new pages call.
- After each phase: files changed, what was added, commands run, real test results, and what's still missing.

### Known constraints in this workspace
1. **Java can't be built or tested here**, because the network policy blocks Maven Central (403).
   - The only place the tests can really run is GitHub Actions.
2. **Pushing to `suryaprasanth740/grocery-web-app` is refused for this session.**
   - Until the Claude GitHub App is installed for the account (or GitHub is reconnected in claude.ai settings), the code can't be pushed to a branch, and CI can't run on it.
   - Without that, each phase would be uploaded through the GitHub website and checked only after upload.
