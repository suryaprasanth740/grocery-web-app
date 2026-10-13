// Thin wrapper around fetch() for the NammaBasket REST API.
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
      // X-Requested-With proves the request comes from our own pages (CSRF protection, see CsrfHeaderInterceptor.java).
      headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
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
  /** The bill for the cart. fulfilment = {shopId: 'DELIVERY'|'PICKUP'}; pincode checks each shop's delivery area. */
  quote(couponCode, fulfilment, pincode) {
    return this.post('/api/orders/quote', { couponCode: couponCode || null, fulfilment: fulfilment || {}, pincode: pincode || null });
  },
  reorder(id) { return this.post(`/api/orders/${id}/reorder`); },
  checkout(payload) { return this.post('/api/orders/checkout', payload); },
  orders() { return this.get('/api/orders'); },
  order(id) { return this.get(`/api/orders/${id}`); },
  pay(id, outcome) { return this.post(`/api/orders/${id}/pay`, { outcome }); },
  cancelOrder(id) { return this.post(`/api/orders/${id}/cancel`); },
  reportIssue(id, payload) { return this.post(`/api/orders/${id}/issues`, payload); },

  // Cart extras, delivery and offers
  acceptPrices() { return this.post('/api/cart/accept-prices'); },
  shops(params) { return this.get('/api/shops?' + new URLSearchParams(params || {}).toString()); },
  shopPage(slug, params) { return this.get(`/api/shops/${encodeURIComponent(slug)}?` + new URLSearchParams(params || {}).toString()); },
  searchProducts(params) { return this.get('/api/products/search?' + new URLSearchParams(params || {}).toString()); },
  checkPincode(pincode) { return this.get('/api/delivery/check?pincode=' + encodeURIComponent(pincode)); },
  coupons() { return this.get('/api/coupons'); },

  // Smart shopping
  parseList(text, shopId) { return this.post('/api/smart-list/parse' + (shopId ? '?shopId=' + encodeURIComponent(shopId) : ''), { text }); },
  addMany(items) { return this.post('/api/cart/add-many', { items }); },
  swapInCart(fromProductId, toProductId) { return this.post('/api/cart/swap', { fromProductId, toProductId }); },
  recipes() { return this.get('/api/recipes'); },
  recipe(id, servings) { return this.get(`/api/recipes/${encodeURIComponent(id)}?servings=${servings}`); },
  budget() { return this.get('/api/budget'); },
  setBudget(amount) { return this.put('/api/budget', { amount }); },
  reorder() { return this.get('/api/reorder'); },

  // Admin (only works for an ADMIN login)
  adminOrders() { return this.get('/api/admin/orders'); },
  adminSetStatus(id, status) { return this.put(`/api/admin/orders/${id}/status`, { status }); },
  adminProducts() { return this.get('/api/admin/products'); },
  adminCreateProduct(payload) { return this.post('/api/admin/products', payload); },
  adminUpdateProduct(id, payload) { return this.put(`/api/admin/products/${id}`, payload); },
  adminIssues() { return this.get('/api/admin/issues'); },
  adminCoupons() { return this.get('/api/admin/coupons'); },
  adminSetCouponActive(id, active) { return this.put(`/api/admin/coupons/${id}/active`, { active }); },
  adminShops(status) { return this.get('/api/admin/shops' + (status ? '?status=' + encodeURIComponent(status) : '')); },
  adminShop(id) { return this.get(`/api/admin/shops/${id}`); },
  adminShopAction(id, action, reason) { return this.post(`/api/admin/shops/${id}/${action}`, reason ? { reason } : {}); },
  adminUsers(role, page) { return this.get(`/api/admin/users?role=${encodeURIComponent(role)}&page=${page || 0}`); },
  adminUserAction(id, action, reason) { return this.post(`/api/admin/users/${id}/${action}`, reason ? { reason } : {}); },
  adminAudit() { return this.get('/api/admin/audit?limit=200'); },
  adminStats() { return this.get('/api/admin/stats'); },
  adminOrderSearch(params) { return this.get('/api/admin/orders/search?' + new URLSearchParams(params || {}).toString()); },
  adminReports(status) { return this.get('/api/admin/reports?status=' + encodeURIComponent(status || 'OPEN')); },
  adminResolveReport(id, payload) { return this.post(`/api/admin/reports/${id}/resolve`, payload); },
  adminCreateCategory(payload) { return this.post('/api/admin/categories', payload); },
  adminUpdateCategory(id, payload) { return this.put(`/api/admin/categories/${id}`, payload); },
  adminDeleteCategory(id) { return this.del(`/api/admin/categories/${id}`); },
  adminCreateCoupon(payload) { return this.post('/api/admin/coupons', payload); },
  adminUpdateCoupon(id, payload) { return this.put(`/api/admin/coupons/${id}`, payload); },
  adminSetProductActive(id, active, reason) { return this.put(`/api/admin/products/${id}/active`, { active, reason }); },
  reportReasons() { return this.get('/api/reports/reasons'); },
  report(payload) { return this.post('/api/reports', payload); },

  // Seller (only works for a SELLER login, always on the seller's own shop)
  registerSeller(payload) { return this.post('/api/auth/register-seller', payload); },
  sellerSummary() { return this.get('/api/seller/summary'); },
  sellerProducts(params) { return this.get('/api/seller/products?' + new URLSearchParams(params || {}).toString()); },
  createProduct(payload) { return this.post('/api/seller/products', payload); },
  updateProduct(id, payload) { return this.put(`/api/seller/products/${id}`, payload); },
  setProductStock(id, stock) { return this.put(`/api/seller/products/${id}/stock`, { stock }); },
  setProductActive(id, active) { return this.put(`/api/seller/products/${id}/active`, { active }); },
  sellerOrders(status, page) { return this.get(`/api/seller/orders?status=${encodeURIComponent(status || 'all')}&page=${page || 0}`); },
  sellerOrder(id) { return this.get(`/api/seller/orders/${id}`); },
  setOrderStep(id, status) { return this.put(`/api/seller/orders/${id}/status`, { status }); },
  rejectOrder(id, reason) { return this.post(`/api/seller/orders/${id}/reject`, { reason }); },
  /** Uploads one photo (already shrunk in the browser). Returns {id, url}. */
  async uploadImage(blob, filename) {
    const form = new FormData();
    form.append('file', blob, filename || 'photo.jpg');
    const res = await fetch('/api/seller/images', {
      method: 'POST', body: form, credentials: 'same-origin',
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
    });
    const data = await res.json().catch(() => null);
    if (!res.ok) {
      const err = new Error((data && data.message) || `Upload failed (${res.status})`);
      err.status = res.status;
      throw err;
    }
    return data;
  },
  myShop() { return this.get('/api/seller/shop'); },
  updateMyShop(payload) { return this.put('/api/seller/shop', payload); },
  resubmitMyShop() { return this.post('/api/seller/shop/resubmit'); },
  setAcceptingOrders(acceptingOrders) { return this.put('/api/seller/shop/accepting', { acceptingOrders }); },

  // ---------- Reviews, saved for later, notifications, slots, invoices ----------
  productReviews(id) { return this.get(`/api/products/${id}/reviews`); },
  shopReviews(id) { return this.get(`/api/shops/${id}/reviews`); },
  review(productId, rating, comment) { return this.post(`/api/products/${productId}/reviews`, { rating, comment }); },
  myReviews() { return this.get('/api/reviews/mine'); },
  adminReviews() { return this.get('/api/admin/reviews'); },
  adminHideReview(id, reason) { return this.put(`/api/admin/reviews/${id}/hide`, { reason }); },
  adminShowReview(id) { return this.put(`/api/admin/reviews/${id}/show`); },
  saved() { return this.get('/api/saved'); },
  save(productId) { return this.post(`/api/saved/${productId}`); },
  unsave(productId) { return this.del(`/api/saved/${productId}`); },
  savedToCart(productId) { return this.post(`/api/saved/${productId}/move-to-cart`); },
  cartToSaved(productId) { return this.post(`/api/cart/save-for-later/${productId}`); },
  notifications() { return this.get('/api/notifications'); },
  readNotification(id) { return this.post(`/api/notifications/${id}/read`); },
  readAllNotifications() { return this.post('/api/notifications/read-all'); },
  shopSlots(shopId) { return this.get(`/api/shops/${shopId}/slots`); },
  invoice(orderId, as) {
    const base = as === 'seller' ? '/api/seller/orders/' : as === 'admin' ? '/api/admin/orders/' : '/api/orders/';
    return this.get(base + orderId + '/invoice');
  },
};

function formatRupees(amount) {
  const n = Number(amount);
  return '\u20B9' + n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/**
 * Small message at the bottom of the screen.
 * tone: 'ok' (green, something worked), 'error' (red, something failed) or leave it out.
 * Screen readers announce it because #toast has role="status".
 */
function showToast(message, tone) {
  let toast = document.getElementById('toast');
  if (!toast) {
    toast = document.createElement('div');
    toast.id = 'toast';
    document.body.appendChild(toast);
  }
  toast.setAttribute('role', 'status');
  toast.setAttribute('aria-live', 'polite');
  toast.className = tone === 'ok' || tone === 'error' ? tone : '';
  toast.textContent = message;
  void toast.offsetWidth; // restart the slide-in when two toasts come quickly
  toast.classList.add('show');
  clearTimeout(window.__toastTimer);
  window.__toastTimer = setTimeout(() => toast.classList.remove('show'), tone === 'error' ? 4200 : 2800);
}

/** A random id for one checkout attempt. Sending it twice can never create two orders. */
function newRequestId() {
  if (window.crypto && typeof window.crypto.randomUUID === 'function') {
    return window.crypto.randomUUID();
  }
  return 'req-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 12);
}

/**
 * Makes text safe to put inside HTML, both between tags and inside attribute values
 * (alt="...", aria-label="...", value="..."). Quotes are escaped too: a product called
 * 'x" onmouseover="...' must stay text and never become an attribute.
 */
const HTML_ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
function escapeHtml(str) {
  return (str == null ? '' : String(str)).replace(/[&<>"']/g, (ch) => HTML_ESCAPES[ch]);
}
