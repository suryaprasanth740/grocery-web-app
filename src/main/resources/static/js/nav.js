// Shared page frame: header (logo, location, links, login state, cart badge) and footer.
// Every page has <header class="signboard" id="site-header"></header> and
// <footer class="site-footer" id="site-footer"></footer>; this file fills them in,
// so the brand, links and footer are the same everywhere and live in ONE place.

const LOGO_SVG = `
  <svg class="logo-mark" viewBox="0 0 32 32" aria-hidden="true" focusable="false">
    <path d="M9.5 13.5a6.5 6.5 0 0 1 13 0" fill="none" stroke="#F2B233" stroke-width="2.8" stroke-linecap="round"/>
    <path d="M3.5 13h25l-2.7 12.4A3.2 3.2 0 0 1 22.7 28H9.3a3.2 3.2 0 0 1-3.1-2.6z" fill="#0E6B5E"/>
    <path d="M8.2 18.2h15.6M9.2 22.8h13.6" stroke="#fff" stroke-opacity=".6" stroke-width="1.7" stroke-linecap="round"/>
    <path d="M12.6 13.6v13.6M19.4 13.6v13.6" stroke="#fff" stroke-opacity=".28" stroke-width="1.4"/>
  </svg>`;

const PIN_ICON = `<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path fill="currentColor" d="M12 2a7 7 0 0 0-7 7c0 5.2 7 13 7 13s7-7.8 7-13a7 7 0 0 0-7-7zm0 9.5A2.5 2.5 0 1 1 12 6.5a2.5 2.5 0 0 1 0 5z"/></svg>`;

/**
 * The shopper's delivery PIN code, remembered in this browser only.
 * Stored after the server confirms whether we deliver there.
 */
const NBLocation = {
  KEY: 'nb-location',
  get() {
    try { return JSON.parse(localStorage.getItem(this.KEY)) || null; } catch (e) { return null; }
  },
  save(check) {
    try { localStorage.setItem(this.KEY, JSON.stringify(check)); } catch (e) { /* private mode: just don't remember */ }
    document.dispatchEvent(new CustomEvent('nb:location', { detail: check }));
  },
  /** Asks the server about a PIN code, remembers the answer if the PIN code is valid, and returns it. */
  async check(pincode) {
    const result = await API.checkPincode(String(pincode || '').trim());
    if (result.valid) this.save(result);
    return result;
  },
  label() {
    const loc = this.get();
    return loc && loc.pincode ? loc.pincode : 'Set PIN code';
  }
};

function renderHeader() {
  const header = document.getElementById('site-header');
  if (!header) return;
  header.innerHTML = `
    <a class="skip-link" href="#main">Skip to content</a>
    <div class="wrap">
      <a href="/" class="brand" aria-label="NammaBasket home">${LOGO_SVG}<span class="brand-name">NammaBasket</span></a>
      <div class="loc-wrap">
        <button type="button" class="loc-btn" id="loc-btn" aria-haspopup="dialog" aria-expanded="false" aria-controls="loc-pop">
          ${PIN_ICON}<span id="loc-label">${escapeHtml(NBLocation.label())}</span>
        </button>
        <div class="loc-pop" id="loc-pop" role="dialog" aria-labelledby="loc-title">
          <h3 id="loc-title">Where should we deliver?</h3>
          <p>Enter your 6-digit PIN code to see which shops deliver to you.</p>
          <form id="loc-form" novalidate>
            <input id="loc-input" inputmode="numeric" maxlength="6" autocomplete="postal-code" aria-label="PIN code" placeholder="560073">
            <button class="btn btn-primary btn-sm" type="submit">Check</button>
          </form>
          <div class="field-msg" id="loc-msg" aria-live="polite"></div>
        </div>
      </div>
      <nav class="nav-links" aria-label="Main">
        <a href="/products.html">Shop</a>
        <a href="/recipes.html">Recipes &amp; Kits</a>
        <a href="/list.html">Quick List</a>
        <a href="/budget.html">Budget</a>
        <a href="/orders.html" id="nav-orders" style="display:none">My Orders</a>
        <span id="nav-auth-area"><a href="/login.html" id="nav-login">Log in</a></span>
        <a href="/cart.html" class="cart-pill" aria-label="Cart">Cart <span class="count" id="cart-count">0</span></a>
      </nav>
    </div>`;
  wireLocationPicker();
}

function wireLocationPicker() {
  const btn = document.getElementById('loc-btn');
  const pop = document.getElementById('loc-pop');
  const form = document.getElementById('loc-form');
  const input = document.getElementById('loc-input');
  const msg = document.getElementById('loc-msg');
  if (!btn) return;

  const close = () => { pop.classList.remove('open'); btn.setAttribute('aria-expanded', 'false'); };
  btn.addEventListener('click', () => {
    const open = !pop.classList.contains('open');
    pop.classList.toggle('open', open);
    btn.setAttribute('aria-expanded', String(open));
    if (open) {
      const loc = NBLocation.get();
      input.value = loc ? loc.pincode : '';
      msg.textContent = loc ? loc.message : '';
      msg.className = 'field-msg' + (loc ? (loc.serviceable ? ' ok' : ' error') : '');
      input.focus();
    }
  });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') close(); });
  document.addEventListener('click', (e) => { if (!pop.contains(e.target) && e.target !== btn && !btn.contains(e.target)) close(); });

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const submit = form.querySelector('button');
    submit.disabled = true;
    try {
      const result = await NBLocation.check(input.value);
      msg.textContent = result.message;
      msg.className = 'field-msg ' + (result.serviceable ? 'ok' : 'error');
    } catch (err) {
      msg.textContent = err.message;
      msg.className = 'field-msg error';
    } finally {
      submit.disabled = false;
    }
  });

  document.addEventListener('nb:location', () => {
    const label = document.getElementById('loc-label');
    if (label) label.textContent = NBLocation.label();
  });
}

function renderFooter() {
  const footer = document.getElementById('site-footer');
  if (!footer) return;
  footer.innerHTML = `
    <div class="wrap">
      <div class="cols">
        <div>
          <a href="/" class="brand">${LOGO_SVG}<span class="brand-name">NammaBasket</span></a>
          <p>Your Neighbourhood Shops, All in One Basket. Order from the kirana stores near you and get it delivered, or pick it up on your way home.</p>
        </div>
        <div>
          <h2>Shop</h2>
          <ul>
            <li><a href="/products.html">All groceries</a></li>
            <li><a href="/recipes.html">Recipes &amp; festival kits</a></li>
            <li><a href="/list.html">Paste a shopping list</a></li>
            <li><a href="/orders.html">My orders</a></li>
            <li><a href="/sell.html">Sell on NammaBasket</a></li>
          </ul>
        </div>
        <div>
          <h2>Help</h2>
          <ul>
            <li><a href="/help.html">Help &amp; FAQs</a></li>
            <li><a href="/help.html#contact">Contact us</a></li>
            <li><a href="/privacy.html">Privacy policy</a></li>
            <li><a href="/terms.html">Terms of use</a></li>
          </ul>
        </div>
      </div>
      <p class="fine">NammaBasket is a portfolio project: payments are a demo and no real orders are delivered.
        Built with Spring Boot, PostgreSQL and plain JavaScript by
        <a href="https://github.com/suryaprasanth740" target="_blank" rel="noopener">Surya Prasanth G</a>.</p>
    </div>`;
}

function markActiveNavLink() {
  const here = window.location.pathname === '/' ? '/index.html' : window.location.pathname;
  document.querySelectorAll('.nav-links a[href]').forEach(a => {
    if (a.getAttribute('href') === here) {
      a.classList.add('active');
      a.setAttribute('aria-current', 'page');
    }
  });
}

async function initNav() {
  markActiveNavLink();
  const authArea = document.getElementById('nav-auth-area');
  const ordersLink = document.getElementById('nav-orders');

  try {
    const user = await API.me();
    if (authArea) {
      authArea.innerHTML = `
        ${user.role === 'ADMIN' ? '<a href="/admin.html" style="margin-right:18px;">Admin</a>' : ''}
        ${user.role === 'SELLER' ? '<a href="/seller.html" style="margin-right:18px;">My shop</a>' : ''}
        <span style="margin-right:18px;">Hi, ${escapeHtml(user.name.split(' ')[0])}</span>
        <button class="linklike" id="nav-logout-btn">Log out</button>
      `;
      document.getElementById('nav-logout-btn').addEventListener('click', async () => {
        await API.logout();
        window.location.href = '/index.html';
      });
    }
    if (ordersLink) ordersLink.style.display = user.role === 'BUYER' ? '' : 'none';
    // Only buyers shop, so sellers and admins don't get a cart button.
    const cart = document.querySelector('.cart-pill');
    if (cart && user.role !== 'BUYER') cart.style.display = 'none';
  } catch (e) {
    // not logged in - leave the default "Log in" link in place
    if (ordersLink) ordersLink.style.display = 'none';
  }

  refreshCartCount();
}

async function refreshCartCount() {
  const badge = document.getElementById('cart-count');
  if (!badge) return;
  try {
    const items = await API.cart();
    const total = items.reduce((sum, item) => sum + item.quantity, 0);
    badge.textContent = total;
  } catch (e) {
    badge.textContent = '0';
  }
}

// The header and footer are drawn straight away (this script loads at the end of <body>),
// so pages never show an empty bar while data loads.
renderHeader();
renderFooter();
document.addEventListener('DOMContentLoaded', initNav);
