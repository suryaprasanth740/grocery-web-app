// Every public page opens without a script error, has one main heading, and a phone-sized
// screen never scrolls sideways. Runs against the real app.
const { test, expect } = require('@playwright/test');

const PAGES = ['/', '/shops.html', '/products.html', '/recipes.html', '/list.html', '/shop.html?s=hasiru-tarkari-angadi',
  '/sell.html', '/login.html', '/register.html', '/help.html', '/privacy.html', '/terms.html', '/credits.html',
  '/no-such-page.html'];

for (const path of PAGES) {
  test(`${path} opens cleanly on desktop and phone`, async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const res = await page.goto(path);
    if (path === '/no-such-page.html') {
      expect(res.status()).toBe(404);
      return;
    }
    expect(res.status()).toBe(200);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('h1')).toHaveCount(1);

    await page.setViewportSize({ width: 375, height: 800 });
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflow, 'page scrolls sideways on a phone').toBeLessThanOrEqual(0);
    expect(errors).toEqual([]);
  });
}

test('Photo credits list every bundled photo with its licence', async ({ page }) => {
  await page.goto('/credits.html');
  const credits = await (await page.request.get('/img/products/credits.json')).json();
  await expect(page.locator('.credit')).toHaveCount(Object.keys(credits).length);
  await expect(page.locator('.credit').first()).toContainText(/CC|Public domain/);
});
