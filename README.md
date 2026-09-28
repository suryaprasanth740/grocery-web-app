# FreshCart — Grocery Web Application

A full-stack grocery shopping platform: browse a product catalog, add items to a cart,
and complete checkout — built as a portfolio/demo project.

**Live demo:** _add your deployed link here once you complete the deployment steps below_
**Tech stack:** Java (Spring Boot) · PostgreSQL / MySQL · HTML/CSS/JavaScript · JUnit

---

## What it does

- Browse products by category or search
- Create an account / log in (passwords are salted + hashed, never stored in plain text)
- Add to cart, adjust quantities, remove items
- Checkout with PIN code check, coupons, a full bill breakdown and a choice of payment
  (Cash on Delivery or a **demo UPI** screen — no real money moves)
- View order history, cancel an order, report a missing item after delivery
- Admin panel to manage orders, products, stock, expiry dates and coupons

## QA-driven features (edge cases most e-commerce apps miss)

I tested this app like a QA engineer, compared it with Zomato / Swiggy Instamart, and built
fixes for the gaps I found. Every rule below has an automated test in `src/test/java`.

| Problem found | What the app does now |
|---|---|
| Two customers buy the **last item** at the same moment | Stock is reserved with one atomic SQL `UPDATE ... WHERE stock >= qty`; only one wins. The database also refuses negative stock. |
| **Double click** on "Place order" | Each checkout has a request id; the same id twice returns the same order |
| Same cart checked out in **two tabs** | The cart is deleted in one statement; the second checkout finds nothing and is refused |
| No input limits | Address 10–300 chars, max 20 of one item, never more than stock |
| **Price changed** after adding to cart | Cart shows "price changed from ₹30 to ₹32"; an order with the old total is refused |
| Bill on the page ≠ amount charged | The bill is calculated only on the server; cart, checkout and order use the same numbers |
| **Hidden fees** | Bill shows item total, coupon, delivery fee, "other fees ₹0" and GST included |
| **PIN code** checked only at the end | PIN code checked while typing, before placing the order |
| Coupon misuse | Minimum order, expiry, per-customer limit, cap on % discounts, never below ₹0 |
| **Money deducted but order failed** | Demo UPI: success / failure / 10-minute timeout. A late payment is refunded automatically |
| Cancel after the order is packed | Only allowed while "Placed"; stock goes back; paid orders are refunded |
| **Missing item** in a grocery delivery | "Report a problem" within 48 hours, refund approved instantly (coupon share taken off) |
| Item out of stock while packing | Customer chooses at checkout: refund / replace with similar / call me |
| **Expired products** | Every product has a best-before date; items expiring today cannot be sold |
| Brute-force login | Account locked for 15 minutes after 5 wrong passwords |
| Seeing another customer's order by changing the URL | Every order API checks the owner (403) |

## Tech stack

| Layer | Choice |
|---|---|
| Backend | Java 17, Spring Boot 3 (Spring Web, Spring Data JPA) |
| Database | MySQL (falls back to in-memory H2 automatically for local dev) |
| Frontend | HTML, CSS, vanilla JavaScript (no framework/build step) |
| Auth | Session-based, salted SHA-256 password hashing |
| Hosting (suggested) | Render.com (free tier) |
| Tests | JUnit 5 + Spring Boot Test + MockMvc, run by GitHub Actions on every push |

> **Why Spring Boot instead of plain Java/servlets:** it's the current industry-standard
> way to build Java web backends and is what most job postings screen for, while keeping
> the same core language skills (Java, MySQL) that were used originally.

## Project structure

```
grocery-web-app/
├── pom.xml
├── render.yaml                  # Render deployment config (backend)
├── netlify.toml                 # Netlify: hosts the frontend, proxies /api to Render
├── vercel.json                  # Vercel: hosts the frontend, proxies /api to Render
├── src/main/java/com/suryaprasanth/grocery/
│   ├── GroceryApplication.java
│   ├── model/                   # JPA entities: Product, Category, User, CartItem, Order, OrderItem, Coupon, OrderIssue
│   ├── service/                 # Business rules: OrderService, BillingService, CouponService, DeliveryService
│   ├── repository/              # Spring Data JPA repositories
│   ├── controller/               # REST controllers (auth, products, categories, cart, orders)
│   ├── dto/                      # Request/response payloads
│   ├── util/                     # Password hashing, session helpers
│   └── config/DataInitializer.java  # Seeds the catalog on first run
└── src/main/resources/
    ├── application.properties
    └── static/                  # Frontend pages (incl. admin.html), css/, js/
src/test/java/...                # Automated API tests (JUnit 5 + MockMvc)
```

## Running it locally

No database setup needed — the app uses an in-memory H2 database by default.

```bash
git clone https://github.com/<your-username>/grocery-web-app.git
cd grocery-web-app
mvn spring-boot:run
```

Then open **http://localhost:8080** in your browser. The catalog is seeded automatically
on first startup.

**Admin login:** `admin@freshcart.com`. If you did not set `ADMIN_PASSWORD`, a random password
is printed in the console when the app starts (look for `ADMIN_PASSWORD is not set`).
To choose your own: `ADMIN_PASSWORD=MySecret123 mvn spring-boot:run`

**Test coupons:** `FRESH50` (₹50 off above ₹299), `SAVE10` (10% up to ₹100 above ₹199),
`BIG100` (₹100 off above ₹999), `OLD20` (already expired — always rejected).
**Test PIN codes:** any `560xxx` (Bengaluru) is delivered; others like `110001` are not.

## Running the tests

```bash
mvn test
```

The tests are also run automatically by GitHub Actions on every push
(repo → **Actions** tab → **Build and test**). They cover the last-item race, double clicks,
boundary values, coupons, UPI success / failure / timeout, cancel rules, missing-item refunds,
expiry dates, admin rules and security checks.

---

## Deploying it for free (so you can put a live link on your resume)

This gives you one public URL, no cost, no custom domain — exactly what you'd share
with an interviewer.

### Step 1 — Push this project to GitHub

```bash
cd grocery-web-app
git init
git add .
git commit -m "Initial commit: FreshCart grocery web app"
git branch -M main
git remote add origin https://github.com/<your-username>/grocery-web-app.git
git push -u origin main
```

(Create the empty repository on GitHub first — github.com → New repository — then
copy its URL into the command above.)

### Step 2 — Create a free permanent PostgreSQL database on Neon

Without this step the app uses an in-memory database, and **every restart wipes all users
and orders**. Neon's free tier keeps your data.

1. Go to **https://neon.tech** and sign up (GitHub login is easiest).
2. Create a project (any name, region: Singapore is closest to India).
3. On the dashboard click **Connect** and copy the connection details. You need:
   host (like `ep-xxxx.ap-southeast-1.aws.neon.tech`), database name (usually `neondb`),
   user, and password.
4. Build the JDBC URL:
   `jdbc:postgresql://<host>/<database>?sslmode=require`

### Step 3 — Deploy the backend on Render

1. Go to **https://render.com** and sign up (GitHub login is easiest).
2. Click **New +** → **Web Service** → connect your GitHub account → select your
   `grocery-web-app` repository.
3. Render should auto-detect the `render.yaml` file and pre-fill the settings. If not, set:
   - **Environment:** Java
   - **Build Command:** `mvn clean package -DskipTests`
   - **Start Command:** `java -Xmx400m -Xss512k -jar target/grocery-web-app.jar`
4. Under **Environment Variables**, add:
   | Key | Value |
   |---|---|
   | `DB_URL` | `jdbc:postgresql://<host>/<database>?sslmode=require` |
   | `DB_USER` | your Neon user |
   | `DB_PASSWORD` | your Neon password |
   | `ADMIN_PASSWORD` | a strong password for the admin panel |
5. Select the **Free** instance type and click **Create Web Service**.
6. Wait for the build to finish (first build can take a few minutes). Render will give you
   a URL like `https://grocery-web-app-xxxx.onrender.com` — **that's your live link.**

### Optional — Host the frontend on Netlify or Vercel

Netlify and Vercel **cannot run the Java backend**. They only host static files, and a Spring Boot server needs a long-running JVM. So the setup is split:

```
Browser ──► Netlify / Vercel  (HTML, CSS, JS from src/main/resources/static)
               │
               └── /api/*  ──proxy──►  Render  (Spring Boot API + MySQL)
```

Netlify/Vercel **forward** every `/api/...` request to Render. The browser only ever talks to one domain, so the login session cookie keeps working. No CORS setup and no Java changes are needed.

**Netlify** (uses `netlify.toml`)
1. netlify.com → **Add new site → Import an existing project** → pick this repo.
2. Leave the settings as they are. `netlify.toml` already sets the publish folder and the `/api` proxy.
3. Deploy. Your site is at `https://<name>.netlify.app`.

**Vercel** (uses `vercel.json`)
1. vercel.com → **Add New → Project** → import this repo.
2. Framework preset: **Other**. Leave everything else as it is.
3. Deploy. Your site is at `https://<name>.vercel.app`.

Keep the Render service running. It is still the backend. If your Render URL changes, update it in **both** `netlify.toml` (`to = ...`) and `vercel.json` (`destination`).

**Cold starts:** a free Render service sleeps after 15 minutes idle and needs about 30–60 s to wake up.
- Netlify gives up on a proxied request after 26 s, and Vercel after 2 minutes.
- `js/api.js` handles this. While the server wakes up, it shows *"Waking up the server…"* and retries page-loading (GET) requests automatically. Actions like placing an order are never retried, so nothing happens twice.
- To avoid the wait completely, the optional GitHub Action `.github/workflows/keep-backend-awake.yml` pings the backend every 10 minutes. One always-on free service uses about 744 of Render's 750 free hours a month. Turn it off any time under the repo's **Actions** tab.

### Step 4 — Put the link on your resume

Use the `onrender.com` URL from Step 3. One honest heads-up worth knowing (and mentioning
if an interviewer asks why the first click is slow): Render's free tier spins the app down
after 15 minutes of inactivity, so the very first visit after a while can take 30–60 seconds
to wake up. Every visit after that is instant. This is a well-known free-tier tradeoff, not a
bug in your app.

---

## API reference (for your own understanding / interview talking points)

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Create an account |
| POST | `/api/auth/login` | Log in |
| POST | `/api/auth/logout` | Log out |
| GET | `/api/auth/me` | Current logged-in user |
| GET | `/api/categories` | List categories |
| GET | `/api/products` | List products (optional `?categoryId=` or `?search=`) |
| GET | `/api/products/{id}` | Product detail |
| GET | `/api/cart` | View cart (requires login) |
| POST | `/api/cart/add` | Add item to cart |
| PUT | `/api/cart/update` | Update item quantity |
| DELETE | `/api/cart/remove/{productId}` | Remove item |
| POST | `/api/cart/accept-prices` | Accept changed prices in the cart |
| GET | `/api/delivery/check?pincode=` | Do we deliver to this PIN code? |
| GET | `/api/coupons` | Offers available now |
| POST | `/api/orders/quote` | The bill for the current cart (optional coupon) |
| POST | `/api/orders/checkout` | Place an order from the current cart |
| GET | `/api/orders` | Order history |
| GET | `/api/orders/{id}` | Single order detail |
| POST | `/api/orders/{id}/pay` | Demo UPI result (`SUCCESS` / `FAILED`) |
| POST | `/api/orders/{id}/cancel` | Cancel (only while placed) |
| POST | `/api/orders/{id}/issues` | Report a missing / damaged / expired item |
| GET/PUT/POST | `/api/admin/...` | Admin: orders, status, products, issues, coupons |

## Honest limitations (worth knowing, not hiding)

- No real payment gateway — UPI is a demo screen. A real app would use Razorpay / PhonePe
  and trust only their signed webhook, never the browser.
- Login lockout counts are kept in memory (reset on restart); a bigger site would use Redis.
- Seeded demo products get a fresh best-before date on restart once they expire
  (`app.demo.refresh-expired-stock=true`) so the free live demo keeps working. Set it to
  `false` to test expiry by hand.
- Render's free tier sleeps after inactivity, causing a cold-start delay on the first visit.
- Table schema is auto-generated by Hibernate (`ddl-auto=update`) rather than versioned
  migrations — fine for a project this size, but a real production app would use a tool
  like Flyway or Liquibase.

## Possible next steps (good talking points for interviews)

- Real payment gateway (Razorpay test mode) with webhook signature checks
- Photo upload for missing / damaged item reports
- Live delivery tracking and delivery-time estimates per area
- Pagination for large catalogs
- UI automation tests with Playwright or Selenium
- Versioned database migrations with Flyway

---

Built by [Surya Prasanth G](https://github.com/suryaprasanth740).
