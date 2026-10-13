// End-to-end journeys through the real app in a real browser:
//   1. A buyer finds shops by PIN code, saves a product, fills a basket from two shops,
//      books a delivery slot and places one checkout that becomes two orders.
//   2. The shop owner gets a notification, moves the order through every step and opens the GST invoice.
//   3. The buyer is notified, rates the product, and the rating shows on the shop page.
//   4. An admin hides that review, and the rating disappears again.
// Demo shops and their products come from MarketplaceDemoData.java.
const { test, expect } = require('@playwright/test');

const ADMIN = { email: process.env.ADMIN_EMAIL, password: process.env.ADMIN_PASSWORD };
const SELLER = { email: 'lakshmi.provisions@demo.nammabasket.in', password: process.env.DEMO_SELLER_PASSWORD };
const BUYER = { name: 'Asha Rao', email: `asha.${Date.now()}@example.com`, password: 'secret123' };
const RICE_SHOP = 'sri-lakshmi-provisions';   // has a GSTIN, so its invoice is a "Tax invoice"
const BAKERY = 'gokula-dairy-bakery';
const PRODUCT = 'Ragi Flour';                  // sold by Sri Lakshmi Provisions
const SECOND = 'Nankhatai';                    // sold by Gokula Dairy & Bakery
const SHOTS = 'screenshots';
const COMMENT = `Fresh, fine flour. Good for ragi mudde (${Date.now()})`; // unique, so a re-run finds its own review

/** Uncaught script errors fail the test (failed network calls such as a 401 are expected and ignored). */
function watchErrors(page) {
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  return errors;
}

async function logIn(page, who, next) {
  await page.goto('/login.html' + (next ? '?next=' + encodeURIComponent(next) : ''));
  await page.fill('#email', who.email);
  await page.fill('#password', who.password);
  await Promise.all([page.waitForURL((u) => !u.pathname.endsWith('/login.html')), page.click('#login-btn')]);
}

function card(page, name) {
  return page.locator('.product-card', { has: page.getByRole('heading', { name, exact: true }) });
}

test.describe.serial('Marketplace journeys', () => {
  let buyerCtx, buyer, orderId;

  test.beforeAll(async ({ browser }) => {
    expect(ADMIN.email, 'set ADMIN_EMAIL and ADMIN_PASSWORD').toBeTruthy();
    expect(SELLER.password, 'set DEMO_SELLER_PASSWORD').toBeTruthy();
    buyerCtx = await browser.newContext();
    buyer = await buyerCtx.newPage();
  });

  test.afterAll(async () => { await buyerCtx.close(); });

  test('Buyer signs up, sets a PIN code and sees the shops that deliver there', async () => {
    const errors = watchErrors(buyer);
    await buyer.goto('/register.html');
    await buyer.fill('#name', BUYER.name);
    await buyer.fill('#email', BUYER.email);
    await buyer.fill('#password', BUYER.password);
    await Promise.all([buyer.waitForURL((u) => !u.pathname.endsWith('/register.html')), buyer.click('#register-btn')]);

    await buyer.goto('/');
    await buyer.fill('#hero-pin', '560041');
    await buyer.locator('#hero-pin-form button[type="submit"]').click();
    await expect(buyer.locator('#hero-pin-msg')).toContainText('560041');

    await buyer.goto('/shops.html');
    await expect(buyer.locator('.shop-card')).toHaveCount(4);
    await expect(buyer.locator('.shop-card', { hasText: 'Sri Lakshmi Provisions' })).toBeVisible();
    await buyer.screenshot({ path: `${SHOTS}/01-shops.png`, fullPage: true });
    expect(errors).toEqual([]);
  });

  test('Product photos are real and load from our own site', async () => {
    await buyer.goto(`/shop.html?s=${RICE_SHOP}`);
    const img = card(buyer, PRODUCT).locator('.product-photo img');
    await expect(img).toHaveAttribute('src', /^\/img\/products\/ragi-flour\.jpg$/);
    await expect.poll(() => img.evaluate((el) => el.complete && el.naturalWidth)).toBeGreaterThan(100);
    await buyer.screenshot({ path: `${SHOTS}/02-shop-page.png`, fullPage: true });
  });

  test('Heart saves a product; Saved page moves it to the cart', async () => {
    await buyer.goto(`/shop.html?s=${BAKERY}`);
    const heart = card(buyer, SECOND).locator('.save-btn');
    await expect(heart).toHaveAttribute('aria-pressed', 'false');
    await heart.click();
    await expect(heart).toHaveAttribute('aria-pressed', 'true');

    await buyer.goto('/saved.html');
    const row = buyer.locator('.saved-item', { hasText: SECOND });
    await expect(row).toBeVisible();
    await buyer.screenshot({ path: `${SHOTS}/03-saved.png`, fullPage: true });
    await row.getByRole('button', { name: `Move ${SECOND} to cart` }).click();
    await expect(buyer.locator('.empty-state')).toContainText('Nothing saved yet');
    await expect(buyer.locator('#cart-count')).toHaveText('1');
  });

  test('Basket from two shops, a booked slot, one checkout = two orders', async () => {
    const errors = watchErrors(buyer);
    await buyer.goto(`/shop.html?s=${RICE_SHOP}`);
    await card(buyer, PRODUCT).getByRole('button', { name: `Add ${PRODUCT} to cart` }).click();
    await expect(buyer.locator('#cart-count')).toHaveText('2');

    await buyer.goto('/cart.html');
    await expect(buyer.getByText('Sri Lakshmi Provisions').first()).toBeVisible();
    await expect(buyer.getByText('Gokula Dairy & Bakery').first()).toBeVisible();
    await buyer.screenshot({ path: `${SHOTS}/04-cart.png`, fullPage: true });

    await buyer.goto('/checkout.html');
    await expect(buyer.locator('#pincode')).toHaveValue('560041');
    await buyer.fill('#address', '22, 9th Main, Jayanagar 4th Block, Bengaluru');
    // Every demo shop offers slots: book the first free slot tomorrow at Sri Lakshmi Provisions.
    const slot = buyer.getByLabel('Delivery time from Sri Lakshmi Provisions');
    await expect(slot).toBeVisible();
    const value = await slot.locator('optgroup[label="Tomorrow"] option:not([disabled])').first().getAttribute('value');
    await slot.selectOption(value);
    await buyer.check('input[name="pay"][value="COD"]');
    await buyer.screenshot({ path: `${SHOTS}/05-checkout.png`, fullPage: true });
    await Promise.all([buyer.waitForURL(/\/order\.html\?id=\d+/), buyer.click('#place-order-btn')]);

    await expect(buyer.locator('.confirm-banner')).toContainText('2 orders, one per shop');
    // The order page we land on is one of the two; find the Sri Lakshmi one.
    await buyer.goto('/orders.html');
    const link = buyer.locator('a.order-card', { hasText: 'Sri Lakshmi Provisions' });
    await expect(link).toContainText('slot');
    orderId = Number((await link.getAttribute('href')).match(/id=(\d+)/)[1]);
    await buyer.goto(`/order.html?id=${orderId}`);
    await expect(buyer.getByText('Delivery slot:')).toBeVisible();
    await buyer.screenshot({ path: `${SHOTS}/06-order.png`, fullPage: true });
    expect(errors).toEqual([]);
  });

  test('Shop owner is notified, moves the order through every step and opens the tax invoice', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    const errors = watchErrors(page);
    await logIn(page, SELLER, '/seller.html');

    await page.locator('#bell-btn').click();
    await expect(page.locator('#bell-list')).toContainText(`New order #${orderId}`);
    await page.keyboard.press('Escape');

    await page.getByRole('tab', { name: /Orders/ }).click();
    // "New" shows only orders waiting to be accepted; "All" keeps the order on screen through every step.
    await page.locator('[data-filter="all"]').click();
    const order = page.locator('article', { hasText: `Order #${orderId}` });
    await expect(order).toContainText('Delivery slot:');
    for (const step of ['Accept order', 'Start preparing', 'Out for delivery', 'Mark delivered']) {
      await page.locator('article', { hasText: `Order #${orderId}` }).getByRole('button', { name: step }).click();
      await expect(page.locator('#toast')).toBeVisible();
    }
    await page.screenshot({ path: `${SHOTS}/07-seller-orders.png`, fullPage: true });
    await page.locator('article', { hasText: `Order #${orderId}` }).getByRole('link', { name: 'View invoice' }).click();
    await expect(page.locator('.inv-title h1')).toHaveText(/Tax invoice/i);
    await expect(page.locator('.invoice')).toContainText('29AAPFL1234K1Z5');
    await expect(page.locator('.invoice')).toContainText(PRODUCT);
    await page.screenshot({ path: `${SHOTS}/08-invoice.png`, fullPage: true });
    expect(errors).toEqual([]);
    await ctx.close();
  });

  test('Buyer sees the update in the bell, rates the product, and the shop page shows it', async () => {
    await buyer.goto('/');
    await expect(buyer.locator('#bell-count')).toBeVisible();
    await buyer.locator('#bell-btn').click();
    await expect(buyer.locator('#bell-list')).toContainText(`Order #${orderId} delivered`);
    await buyer.screenshot({ path: `${SHOTS}/09-bell.png` });
    await buyer.locator('#bell-list a', { hasText: `Order #${orderId} delivered` }).click();
    await expect(buyer).toHaveURL(new RegExp(`/order\\.html\\?id=${orderId}`));

    const form = buyer.locator('form.review-row', { hasText: PRODUCT });
    await form.locator('label[for$="-4"]').click();
    await form.getByRole('textbox').fill(COMMENT);
    await form.getByRole('button', { name: 'Send' }).click();
    await expect(form.locator('.field-msg')).toHaveText('Thanks! Your review is saved.');
    await buyer.screenshot({ path: `${SHOTS}/10-review.png`, fullPage: true });

    await buyer.goto(`/shop.html?s=${RICE_SHOP}`);
    await expect(card(buyer, PRODUCT).locator('.rating-btn')).toContainText('4');
    await card(buyer, PRODUCT).locator('.rating-btn').click();
    await expect(buyer.locator('#reviews-dialog')).toContainText(COMMENT);
    await buyer.screenshot({ path: `${SHOTS}/11-reviews-dialog.png` });
    await buyer.keyboard.press('Escape');
    await expect(buyer.locator('#shop-reviews')).toContainText('What buyers say');
  });

  test('Admin hides the review; it leaves the product rating', async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await logIn(page, ADMIN, '/admin.html');
    await page.getByRole('button', { name: 'Reviews' }).click();
    const row = page.locator('tr', { hasText: COMMENT });
    await row.getByLabel('Reason for hiding').fill('Test of moderation');
    await row.getByRole('button', { name: 'Hide' }).click();
    await expect(page.locator('tr', { hasText: COMMENT })).toContainText('hidden');
    await page.screenshot({ path: `${SHOTS}/12-admin-reviews.png`, fullPage: true });
    await ctx.close();

    await buyer.goto(`/shop.html?s=${RICE_SHOP}`);
    await expect(card(buyer, PRODUCT).locator('.rating-btn')).toHaveCount(0);
  });

  test('Cart item can be saved for later from the cart page', async () => {
    await buyer.goto(`/shop.html?s=${BAKERY}`);
    await card(buyer, SECOND).getByRole('button', { name: `Add ${SECOND} to cart` }).click();
    await buyer.goto('/cart.html');
    await buyer.getByRole('button', { name: `Save ${SECOND} for later` }).click();
    await expect(buyer.locator('#toast')).toContainText('Saved for later');
    await buyer.goto('/saved.html');
    await expect(buyer.locator('.saved-item', { hasText: SECOND })).toBeVisible();
  });
});
