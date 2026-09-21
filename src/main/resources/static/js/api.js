// Thin wrapper around fetch() for the Grocery Web App REST API.
// All calls include credentials so the session cookie (login state) is sent.

// When the frontend is hosted on Vercel/Netlify, /api/* is proxied to the
// backend on Render. A free Render service sleeps when idle and needs up to
// 30-90 s to wake up; meanwhile the proxy answers 502/503/504 (Netlify gives
// up after 26 s, Vercel after 2 min). Safe GET requests are retried for about
// 90 s while it wakes, which covers a slow cold start.
// POST/PUT/DELETE are never retried, so an order is never placed twice.
const WAKE_UP_STATUSES = [502, 503, 504];
const MAX_WAKE_UP_RETRIES = 8;
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const API = {
  async _request(method, url, body, attempt = 0) {
    const options = {
      method,
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
    };
    if (body !== undefined) {
      options.body = JSON.stringify(body);
    }
    const canRetry = method === 'GET' && attempt < MAX_WAKE_UP_RETRIES;

    let res;
    try {
      res = await fetch(url, options);
    } catch (networkError) {
      if (canRetry) return this._retryWhileWaking(method, url, body, attempt);
      throw new Error('Cannot reach the server. Please check your connection and try again.');
    }

    if (WAKE_UP_STATUSES.includes(res.status)) {
      if (canRetry) return this._retryWhileWaking(method, url, body, attempt);
      const err = new Error('The server is waking up. Please try again in a few seconds.');
      err.status = res.status;
      throw err;
    }

    if (res.status === 204) {
      return null;
    }

    let data = null;
    try {
      data = await res.json();
    } catch (e) {
      data = null;
    }

    if (!res.ok) {
      const message = (data && data.message) ? data.message : `Request failed (${res.status})`;
      const err = new Error(message);
      err.status = res.status;
      throw err;
    }
    return data;
  },

  async _retryWhileWaking(method, url, body, attempt) {
    if (attempt === 0 && typeof showToast === 'function') {
      showToast('Waking up the server… the free host sleeps, so this can take a minute.');
    }
    await sleep(Math.min(3000 * (attempt + 1), 15000)); // 3s, 6s, 9s, 12s, 15s, 15s, 15s, 15s = ~90s
    return this._request(method, url, body, attempt + 1);
  },

  get(url) { return this._request('GET', url); },
  post(url, body) { return this._request('POST', url, body === undefined ? {} : body); },
  put(url, body) { return this._request('PUT', url, body === undefined ? {} : body); },
  del(url) { return this._request('DELETE', url); },

  // Auth
  register(payload) { return this.post('/api/auth/register', payload); },
  login(payload) { return this.post('/api/auth/login', payload); },
  logout() { return this.post('/api/auth/logout'); },
  me() { return this.get('/api/auth/me'); },

  // Catalog
  categories() { return this.get('/api/categories'); },
  products(params) {
    const qs = params ? '?' + new URLSearchParams(params).toString() : '';
    return this.get('/api/products' + qs);
  },
  product(id) { return this.get(`/api/products/${id}`); },

  // Cart
  cart() { return this.get('/api/cart'); },
  addToCart(productId, quantity) { return this.post('/api/cart/add', { productId, quantity }); },
  updateCartQty(productId, quantity) { return this.put('/api/cart/update', { productId, quantity }); },
  removeFromCart(productId) { return this.del(`/api/cart/remove/${productId}`); },
  clearCart() { return this.del('/api/cart/clear'); },

  // Orders
  checkout(shippingAddress) { return this.post('/api/orders/checkout', { shippingAddress }); },
  orders() { return this.get('/api/orders'); },
  order(id) { return this.get(`/api/orders/${id}`); },
};

function formatRupees(amount) {
  const n = Number(amount);
  return '\u20B9' + n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function showToast(message) {
  let toast = document.getElementById('toast');
  if (!toast) {
    toast = document.createElement('div');
    toast.id = 'toast';
    document.body.appendChild(toast);
  }
  toast.textContent = message;
  toast.classList.add('show');
  clearTimeout(window.__toastTimer);
  window.__toastTimer = setTimeout(() => toast.classList.remove('show'), 2600);
}

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str == null ? '' : String(str);
  return div.innerHTML;
}
