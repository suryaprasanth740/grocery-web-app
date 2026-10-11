// Shared UI helpers used by several pages: product cards, order status labels and the bill.

// ---------- Product cards ----------

function expiryTagHtml(p) {
  if (p.expired) return `<span class="expiry-tag danger">Not available (expiry)</span>`;
  if (p.expiresSoon) return `<span class="expiry-tag warn">Best before ${formatDate(p.expiryDate)}</span>`;
  if (p.expiryDate) return `<span class="expiry-tag">Best before ${formatDate(p.expiryDate)}</span>`;
  return '';
}

function productCardHtml(p) {
  const lowStock = p.stock > 0 && p.stock <= 5;
  const paused = p.shopAcceptingOrders === false;
  const canBuy = p.stock > 0 && !p.expired && !paused;
  const off = Number(p.discountPercent || 0);
  return `
    <div class="product-card">
      <div class="product-photo">
        ${p.stock === 0 ? `<span class="stock-tag">Out of stock</span>` : lowStock ? `<span class="stock-tag">Only ${p.stock} left</span>` : ''}
        ${off > 0 ? `<span class="off-tag">${off}% off</span>` : ''}
        ${p.imageUrl ? `<img src="${escapeHtml(p.imageUrl)}" alt="${escapeHtml(p.name)}" loading="lazy" onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">` : ''}
        <span class="photo-emoji" style="${p.imageUrl ? 'display:none;' : ''}">${p.imageEmoji || '\u{1F6D2}'}</span>
      </div>
      <div class="body">
        ${p.brand ? `<div class="brand">${escapeHtml(p.brand)}</div>` : ''}
        <h3>${escapeHtml(p.name)}</h3>
        <div class="unit">${escapeHtml(p.unit || '')}</div>
        ${p.shopName ? `<a class="sold-by" href="/shop.html?s=${encodeURIComponent(p.shopSlug)}">${escapeHtml(p.shopName)}</a>` : ''}
        <div class="desc">${escapeHtml(p.description || '')}</div>
        ${expiryTagHtml(p)}
        ${paused ? `<span class="expiry-tag warn">Shop not taking orders now</span>` : ''}
        <div class="row">
          <span class="price">${formatRupees(p.price)}${off > 0 ? ` <s class="mrp">${formatRupees(p.mrp)}</s>` : ''}</span>
          ${canBuy
            ? `<button class="add-to-cart-btn" data-id="${p.id}" aria-label="Add ${escapeHtml(p.name)} to cart">Add</button>`
            : `<button class="add-to-cart-btn" disabled>Add</button>`}
        </div>
      </div>
    </div>
  `;
}

/** A shop in a list: name, type, open now, how you get your order, fees and distance. */
function shopCardHtml(s) {
  const how = [s.offersDelivery && 'Delivery', s.offersPickup && 'Pickup'].filter(Boolean).join(' & ');
  const fee = Number(s.deliveryFee) === 0 ? 'Free delivery' : `Delivery ${formatRupees(s.deliveryFee)}`;
  return `
    <a class="shop-card" href="/shop.html?s=${encodeURIComponent(s.slug)}">
      <div class="shop-card-top">
        <div class="shop-logo">${s.logoUrl ? `<img src="${escapeHtml(s.logoUrl)}" alt="" loading="lazy">` : '\u{1F3EA}'}</div>
        <div>
          <h3>${escapeHtml(s.name)}</h3>
          <div class="muted small">${escapeHtml(s.categoryLabel)}${s.distanceKm != null ? `, ${s.distanceKm} km away` : ''}</div>
        </div>
      </div>
      <div class="shop-card-facts">
        <span class="${s.acceptingOrders && s.openNow ? 'open' : 'closed'}">${!s.acceptingOrders ? 'Not taking orders' : s.openNow ? 'Open now' : 'Opens ' + s.openTime}</span>
        <span>${how}</span>
        ${s.offersDelivery ? `<span>${fee}${s.freeDeliveryAbove ? `, free from ${formatRupees(s.freeDeliveryAbove)}` : ''}</span>` : ''}
        ${Number(s.minOrderAmount) > 0 ? `<span>Min. order ${formatRupees(s.minOrderAmount)}</span>` : ''}
      </div>
    </a>`;
}

/**
 * Delivery or pickup per shop, remembered in this browser so the cart and checkout agree.
 * Stored as {shopId: 'DELIVERY' | 'PICKUP'}.
 */
const NBChoices = {
  KEY: 'nb-fulfilment',
  all() {
    try { return JSON.parse(localStorage.getItem(this.KEY)) || {}; } catch (e) { return {}; }
  },
  set(shopId, value) {
    const all = this.all();
    all[shopId] = value;
    try { localStorage.setItem(this.KEY, JSON.stringify(all)); } catch (e) { /* private mode */ }
  },
};

function attachAddToCartHandlers(container) {
  container.querySelectorAll('.add-to-cart-btn[data-id]').forEach(btn => {
    btn.addEventListener('click', async () => {
      btn.disabled = true; // stops fast double clicks adding 2
      try {
        await API.addToCart(Number(btn.dataset.id), 1);
        showToast('Added to cart');
        refreshCartCount();
      } catch (e) {
        if (e.status === 401) {
          window.location.href = '/login.html?next=' + encodeURIComponent(window.location.pathname);
        } else {
          showToast(e.message);
        }
      } finally {
        btn.disabled = false;
      }
    });
  });
}

// ---------- Dates ----------

function formatDate(isoDate) {
  if (!isoDate) return '';
  const d = new Date(isoDate + (isoDate.length === 10 ? 'T00:00:00' : ''));
  return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}

/** The server stores times in UTC without a zone ("2026-09-28T13:08:28"). Read them as UTC. */
function serverDate(iso) {
  if (!iso) return null;
  return new Date(/(Z|[+-]\d\d:?\d\d)$/.test(iso) ? iso : iso + 'Z');
}

function formatDateTime(iso) {
  return serverDate(iso).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
}

// ---------- Order status ----------

const STATUS_LABELS = {
  PAYMENT_PENDING: ['Waiting for payment', 'warn'],
  PLACED: ['Placed', 'info'],
  ACCEPTED: ['Accepted by shop', 'info'],
  PREPARING: ['Preparing', 'info'],
  PACKED: ['Packed', 'info'],
  READY_FOR_PICKUP: ['Ready for pickup', 'ok'],
  OUT_FOR_DELIVERY: ['Out for delivery', 'info'],
  DELIVERED: ['Delivered', 'ok'],
  CANCELLED: ['Cancelled', 'muted'],
  PAYMENT_FAILED: ['Payment failed', 'danger'],
};

const PAYMENT_LABELS = {
  PENDING: 'UPI payment pending',
  COD_PENDING: 'Cash on delivery',
  PAID: 'Paid',
  FAILED: 'Payment failed',
  REFUNDED: 'Refunded',
  NOT_CHARGED: 'Not charged',
};

const SUBSTITUTION_LABELS = {
  REPLACE_SIMILAR: 'Replace with a similar item',
  REFUND_ITEM: 'Refund the item',
  CALL_ME: 'Call me first',
};

/** The steps an order goes through, which depend on delivery or pickup. */
function orderSteps(order) {
  return order.fulfilment === 'PICKUP'
    ? ['PLACED', 'ACCEPTED', 'PREPARING', 'READY_FOR_PICKUP', 'DELIVERED']
    : ['PLACED', 'ACCEPTED', 'PREPARING', 'OUT_FOR_DELIVERY', 'DELIVERED'];
}

function statusBadgeHtml(status) {
  const [label, tone] = STATUS_LABELS[status] || [status, 'info'];
  return `<span class="status-badge tone-${tone}">${escapeHtml(label)}</span>`;
}

// ---------- Bill (used by cart, checkout and order pages) ----------

/** Bill rows. Works for a server "quote" and for a saved order (same field names). */
function billRowsHtml(b) {
  const subtotal = b.subtotal;
  const discount = Number(b.discount || 0);
  const delivery = Number(b.deliveryFee || 0);
  return `
    <div class="summary-row"><span>Item total</span><span>${formatRupees(subtotal)}</span></div>
    ${discount > 0 ? `<div class="summary-row saving"><span>Coupon ${escapeHtml(b.couponCode || '')}</span><span>&minus; ${formatRupees(discount)}</span></div>` : ''}
    <div class="summary-row"><span>Delivery fee</span><span>${delivery === 0 ? '<b class="free">FREE</b>' : formatRupees(delivery)}</span></div>
    <div class="summary-row"><span>Platform / packing / other fees</span><span><b class="free">&#8377;0</b></span></div>
    <div class="summary-row total"><span>To pay</span><span>${formatRupees(b.total != null ? b.total : b.totalAmount)}</span></div>
    ${Number(b.gstIncluded || 0) > 0 ? `<div class="bill-note">Includes GST of ${formatRupees(b.gstIncluded)}. No hidden charges.</div>` : `<div class="bill-note">No hidden charges.</div>`}
  `;
}

// ---------- Smart shopping helpers (Quick List, Recipes & Kits, Budget) ----------

/** Small square picture of a product: its photo, or its emoji if there is no photo. */
function thumbHtml(p) {
  if (!p) return `<div class="thumb">\u{2753}</div>`;
  const emoji = escapeHtml(p.imageEmoji || '\u{1F6D2}');
  if (p.imageUrl) {
    return `<div class="thumb"><img src="${escapeHtml(p.imageUrl)}" alt="" loading="lazy"
      onerror="this.parentNode.textContent='${emoji}'"></div>`;
  }
  return `<div class="thumb">${emoji}</div>`;
}

function goToLogin() {
  window.location.href = '/login.html?next=' + encodeURIComponent(window.location.pathname + window.location.hash);
}

/**
 * Adds many items in one request and tells the customer what happened.
 * items: [{ productId, quantity }]. Returns the server result, or null if not logged in.
 */
async function addManyToCart(items) {
  try {
    const result = await API.addMany(items);
    refreshCartCount();
    const added = result.added.length;
    const skipped = result.skipped.length;
    if (skipped === 0) {
      showToast(`Added ${added} item${added === 1 ? '' : 's'} to your cart`);
    } else {
      showToast(`Added ${added}, skipped ${skipped}: ${result.skipped[0]}`);
    }
    return result;
  } catch (e) {
    if (e.status === 401) {
      goToLogin();
      return null;
    }
    showToast(e.message);
    return null;
  }
}
