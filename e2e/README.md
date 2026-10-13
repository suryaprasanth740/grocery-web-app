# Browser tests (Playwright)

These tests open the **real running app** in Chrome and click through it like people do:
a buyer, a shop owner and an admin. Nothing is mocked: the app, its database and its pages
all run for real. GitHub Actions runs them on every push (job "Browser tests").

| File | What it checks |
|------|----------------|
| `tests/marketplace.spec.js` | Buyer: sign up, PIN code, shops, real product photos, save for later, two-shop basket, delivery slot, checkout. Seller: notification, every order step, tax invoice. Buyer: notification, star rating, reviews on the shop page. Admin: hides a review. |
| `tests/pages.spec.js` | Every public page opens without a script error, has one main heading (h1), and doesn't scroll sideways on a 375 px phone screen. Unknown pages give 404. Photo credits list every photo. |

## Run them on your computer

1. Start the app with known passwords (Java 17 + Maven):

   ```bash
   ADMIN_EMAIL=admin@nammabasket.test ADMIN_PASSWORD='Admin@12345' DEMO_SELLER_PASSWORD='Demo@12345' mvn spring-boot:run
   ```

2. In another terminal:

   ```bash
   cd e2e
   npm install
   npx playwright install chromium
   ADMIN_EMAIL=admin@nammabasket.test ADMIN_PASSWORD='Admin@12345' DEMO_SELLER_PASSWORD='Demo@12345' npm test
   ```

Screenshots of each step are saved in `e2e/screenshots/`, and an HTML report in `e2e/report/`.
The tests create their own buyer account each run, so they can be run again on the same app.
