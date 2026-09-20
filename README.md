# FreshCart — Grocery Web Application

A full-stack grocery shopping platform: browse a product catalog, add items to a cart,
and complete checkout — built as a portfolio/demo project.

**Live demo:** _add your deployed link here once you complete the deployment steps below_
**Tech stack:** Java (Spring Boot) · MySQL · HTML/CSS/JavaScript

---

## What it does

- Browse products by category or search
- Create an account / log in (passwords are salted + hashed, never stored in plain text)
- Add to cart, adjust quantities, remove items
- Checkout with a shipping address (demo project — no real payment is processed)
- View order history and order details
- Stock is tracked and decremented per order

## Tech stack

| Layer | Choice |
|---|---|
| Backend | Java 17, Spring Boot 3 (Spring Web, Spring Data JPA) |
| Database | MySQL (falls back to in-memory H2 automatically for local dev) |
| Frontend | HTML, CSS, vanilla JavaScript (no framework/build step) |
| Auth | Session-based, salted SHA-256 password hashing |
| Hosting (suggested) | Render.com (free tier) |

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
│   ├── model/                   # JPA entities: Product, Category, User, CartItem, Order, OrderItem
│   ├── repository/              # Spring Data JPA repositories
│   ├── controller/               # REST controllers (auth, products, categories, cart, orders)
│   ├── dto/                      # Request/response payloads
│   ├── util/                     # Password hashing, session helpers
│   └── config/DataInitializer.java  # Seeds the catalog on first run
└── src/main/resources/
    ├── application.properties
    └── static/                  # Frontend: index/products/cart/checkout/orders/login/register .html, css/, js/
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

### Step 2 — Create a free MySQL database on db4free.net

1. Go to **https://www.db4free.net/** and click **Sign up**.
2. Pick a database name (e.g. `groceryapp123` — db4free requires it to be somewhat unique),
   a username, and password. Confirm via the email they send you.
3. Note down: hostname `db4free.net`, port `3306`, your database name, username, and password.

> **Be upfront about this in interviews if asked:** db4free.net is explicitly a free
> *testing/development* MySQL host, not a production-grade service — it can have occasional
> outages and isn't meant for real customer data. That's fine for a portfolio demo (and it's
> honestly how most free student-project databases work), just don't describe it as
> production infrastructure.

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
   | `DB_URL` | `jdbc:mysql://db4free.net:3306/<your_db_name>` |
   | `DB_USER` | your db4free.net username |
   | `DB_PASSWORD` | your db4free.net password |
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
| POST | `/api/orders/checkout` | Place an order from the current cart |
| GET | `/api/orders` | Order history |
| GET | `/api/orders/{id}` | Single order detail |

## Honest limitations (worth knowing, not hiding)

- No real payment gateway — checkout is a demo "Cash on Delivery" flow.
- `db4free.net` is a free testing database, not production-grade (see Step 2 above).
- Render's free tier sleeps after inactivity, causing a cold-start delay on the first visit.
- Table schema is auto-generated by Hibernate (`ddl-auto=update`) rather than versioned
  migrations — fine for a project this size, but a real production app would use a tool
  like Flyway or Liquibase.

## Possible next steps (good talking points for interviews)

- Add an admin panel to manage products/stock
- Add product images instead of emoji placeholders
- Add pagination for large catalogs
- Move to a managed production database + a paid always-on host
- Add automated tests (JUnit for backend, Playwright/Cypress for frontend flows)

---

Built by [Surya Prasanth G](https://github.com/suryaprasanth740).
