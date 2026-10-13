// Every public page opens without a script error, has one main heading, and a phone-sized
// screen never scrolls sideways. Runs against the real app.
const { test, expect } = require('@playwright/test');
const { checkA11y, watchErrors } = require('./helpers');

const PAGES = ['/', '/shops.html', '/products.html', '/recipes.html', '/list.html', '/shop.html?s=hasiru-tarkari-angadi',
  '/sell.html', '/login.html', '/register.html', '/help.html', '/privacy.html', '/terms.html', '/credits.html',
  '/no-such-page.html'];

for (const path of PAGES) {
  test(`${path} opens cleanly on desktop and phone`, async ({ page }) => {
    const errors = watchErrors(page);
    const res = await page.goto(path);
    if (path === '/no-such-page.html') {
      expect(res.status()).toBe(404);
      return;
    }
    expect(res.status()).toBe(200);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('h1')).toHaveCount(1);
    await checkA11y(page, 'page' + path.replace(/[^a-z0-9]+/gi, '-'));

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

test('Security headers are sent with pages and API answers', async ({ page }) => {
  const res = await page.goto('/');
  const h = res.headers();
  expect(h['content-security-policy']).toContain("frame-ancestors 'none'");
  expect(h['x-content-type-options']).toBe('nosniff');
  expect(h['x-frame-options']).toBe('DENY');
  const api = await page.request.get('/api/categories');
  expect(api.headers()['cache-control']).toContain('no-store');
});

test('Hostile shop or product text is shown as text and never runs as code (XSS)', async ({ page }) => {
  const errors = watchErrors(page);
  await page.goto('/products.html');
  await page.waitForLoadState('networkidle');
  const result = await page.evaluate(async () => {
    window.__xss = 0;
    const evil = {
      id: 999001, name: 'Dal" onmouseover="window.__xss=1" data-x="', brand: '<img src=x onerror="window.__xss=2">',
      description: '<script>window.__xss=3</script>', unit: '1 kg', price: 10, stock: 5, imageEmoji: "';window.__xss=4;'",
      imageUrl: '/no-such-photo.png', shopName: '<b>Shop</b>', shopSlug: 'x', ratingCount: 1, ratingAverage: 4,
    };
    const box = document.createElement('div');
    box.innerHTML = productCardHtml(evil) + thumbHtml(evil);
    document.body.appendChild(box);
    box.querySelectorAll('*').forEach((el) => el.dispatchEvent(new MouseEvent('mouseover', { bubbles: true })));
    await new Promise((r) => setTimeout(r, 500)); // let the broken image fire its error handler
    return { xss: window.__xss, shownName: box.querySelector('h3').textContent, injected: box.querySelectorAll('[data-x], script, img[src="x"]').length, shownShop: box.querySelector('.sold-by').textContent };
  });
  expect(result.xss).toBe(0);
  expect(result.injected).toBe(0);
  expect(result.shownName).toContain('onmouseover');
  expect(result.shownShop).toBe('<b>Shop</b>');
  expect(errors).toEqual([]);
});
