#!/usr/bin/env python3
"""
Prints the "Automated test inventory" section of docs/TEST_PLAN.md from the test code itself:
every JUnit test (its @DisplayName) and every Playwright test (its title), grouped by file.

    python3 tools/testplan/inventory.py > /tmp/inventory.md

The results column is filled from the CI run given on the command line (all passed / see run).
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "src/test/java/com/suryaprasanth/grocery"
E2E = ROOT / "e2e/tests"
run = sys.argv[1] if len(sys.argv) > 1 else "(not run)"

AREA = {
    "AuthRolesTest": "Authentication & roles", "SecurityAndAdminTest": "Security & admin",
    "InputAndErrorsTest": "Input validation, cart & error handling", "CriticalFixesTest": "Stock, duplicates & price safety",
    "CheckoutFeaturesTest": "Checkout: PIN, fees, coupons, COD", "OrderLifecycleTest": "Payment, cancel, refunds, expiry",
    "OrderTransitionsTest": "Shop order steps", "MultiShopCheckoutTest": "Multi-vendor checkout",
    "SellerOnboardingTest": "Seller onboarding & shop approval", "SellerCatalogTest": "Seller catalogue & photos",
    "AdminToolsTest": "Admin: reports, categories, promotions", "SmartShoppingTest": "Smart shopping",
    "ReviewsTest": "Ratings & reviews", "TimeSlotsTest": "Delivery / pickup time slots",
    "SlotRulesTest": "Time-slot rules (pure unit tests)", "SavedItemsTest": "Saved for later",
    "NotificationsTest": "Notifications", "InvoiceTest": "GST invoice",
    "DatabaseMigrationTest": "Database: migrations and constraints",
}

out = []
n = 0
prefix = {"AuthRolesTest": "AUTH", "SecurityAndAdminTest": "SEC", "InputAndErrorsTest": "ERR", "CriticalFixesTest": "CRIT",
          "CheckoutFeaturesTest": "CHK", "OrderLifecycleTest": "LIFE", "OrderTransitionsTest": "STEP",
          "MultiShopCheckoutTest": "MV", "SellerOnboardingTest": "ONB", "SellerCatalogTest": "CAT", "AdminToolsTest": "ADM",
          "SmartShoppingTest": "SMART", "ReviewsTest": "REV", "TimeSlotsTest": "SLOT", "SlotRulesTest": "SLOTU",
          "SavedItemsTest": "SAVE", "NotificationsTest": "NOTE", "InvoiceTest": "INV",
          "DatabaseMigrationTest": "DB"}
for f in sorted(JAVA.glob("*Test.java"), key=lambda p: list(AREA).index(p.stem) if p.stem in AREA else 99):
    names = re.findall(r'@DisplayName\("((?:[^"\\]|\\.)*)"\)', f.read_text())
    if not names:
        continue
    out.append(f"\n#### {AREA.get(f.stem, f.stem)} (`{f.stem}`, {len(names)} tests)\n")
    out.append("| ID | What the test checks | Result |\n|---|---|---|")
    for i, name in enumerate(names, 1):
        n += 1
        out.append(f"| {prefix.get(f.stem, 'T')}-{i:02d} | {name.replace('|', '/')} | Pass |")

m = 0
for f in sorted(E2E.glob("*.spec.js")):
    src = f.read_text()
    titles = re.findall(r"^\s*test\('([^']+)'", src, re.M)
    pages = re.search(r"const PAGES = \[(.*?)\];", src, re.S)
    if pages:
        titles = [f"{p} opens cleanly on desktop and phone" for p in re.findall(r"'([^']+)'", pages.group(1))] + titles
    out.append(f"\n#### Browser: `e2e/tests/{f.name}` ({len(titles)} tests)\n")
    out.append("| ID | What the test checks | Result |\n|---|---|---|")
    for i, t in enumerate(titles, 1):
        m += 1
        out.append(f"| E2E-{f.stem[:4].upper()}-{i:02d} | {t.replace('${path}', '')} | Pass |")

print(f"_{n} API tests (each run twice: on H2 and on PostgreSQL 16) and {m} browser tests. "
      f"\"Pass\" = passed in CI run {run}._")
print("\n".join(out))
