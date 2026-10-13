// Shared checks for the browser tests.
const fs = require('fs');
const path = require('path');
const { expect } = require('@playwright/test');
const AxeBuilder = require('@axe-core/playwright').default;

const REPORT_DIR = 'screenshots';

/**
 * Runs the axe-core accessibility scan (WCAG 2.1 A and AA rules) on the page as it is now.
 * Every finding is saved to screenshots/axe-<name>.json for review; "serious" and "critical"
 * findings fail the test (soft assertion, so the journey carries on and all pages get checked).
 */
async function checkA11y(page, name) {
  const result = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  const summary = result.violations.map((v) => ({
    id: v.id, impact: v.impact, help: v.help,
    nodes: v.nodes.slice(0, 5).map((n) => ({ target: n.target.join(' '), why: n.failureSummary })),
    count: v.nodes.length,
  }));
  fs.mkdirSync(REPORT_DIR, { recursive: true });
  fs.writeFileSync(path.join(REPORT_DIR, `axe-${name}.json`), JSON.stringify({ url: page.url(), violations: summary }, null, 1));
  const bad = summary.filter((v) => v.impact === 'serious' || v.impact === 'critical')
    .map((v) => `${v.impact}: ${v.id} (${v.count}x) ${v.nodes[0] ? v.nodes[0].target : ''}`);
  expect.soft(bad, `accessibility problems on ${name}`).toEqual([]);
}

/** Collects uncaught script errors and Content-Security-Policy blocks on a page. */
function watchErrors(page) {
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  page.on('console', (m) => {
    const text = m.text();
    // The axe scanner itself re-downloads stylesheets with fetch() to check colours; the policy
    // (rightly) blocks that for Google Fonts. Pages never do this, so it isn't counted.
    const fromAxe = /connect-src/.test(text) && /fonts\.googleapis\.com/.test(text);
    if (m.type() === 'error' && /Content Security Policy/i.test(text) && !fromAxe) errors.push(text);
  });
  return errors;
}

module.exports = { checkA11y, watchErrors };
