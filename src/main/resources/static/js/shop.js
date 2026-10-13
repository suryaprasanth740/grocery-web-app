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
        ${saveButtonHtml(p)}
      </div>
      <div class="body">
        ${p.brand ? `<div class="brand">${escapeHtml(p.brand)}</div>` : ''}
        <h3>${escapeHtml(p.name)}</h3>
        <div class="unit">${escapeHtml(p.unit || '')}</div>
        ${ratingButtonHtml(p)}
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
          ${s.ratingCount > 0 ? `<div class="stars-line">${starsHtml(s.ratingAverage)} ${s.ratingAverage} <span class="muted">(${s.ratingCount})</span></div>` : ''}
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

// ---------- Ratings ----------

const HEART_SVG = '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M12 20.3l-1.3-1.2C5.9 14.8 3 12.1 3 8.8 3 6.1 5.1 4 7.7 4c1.5 0 3 .7 4.3 1.9C13.3 4.7 14.8 4 16.3 4 18.9 4 21 6.1 21 8.8c0 3.3-2.9 6-7.7 10.3z"/></svg>';

/** Five stars, filled to the nearest half, for an average like 4.3. Decorative: the number is always shown next to it. */
function starsHtml(avg, size) {
  const v = Math.round(Number(avg || 0) * 2) / 2;
  let out = '';
  for (let i = 1; i <= 5; i++) {
    out += `<span class="star ${v >= i ? 'full' : v >= i - 0.5 ? 'half' : ''}">\u2605</span>`;
  }
  return `<span class="stars${size === 'lg' ? ' lg' : ''}" aria-hidden="true">${out}</span>`;
}

/** "★★★★☆ 4.3 (12)" on a product card; opens the reviews. Nothing when nobody has reviewed it yet. */
function ratingButtonHtml(p) {
  if (!p.ratingCount) return '';
  return `<button type="button" class="rating-btn" data-reviews="${p.id}" data-name="${escapeHtml(p.name)}"
      aria-label="${p.ratingAverage} out of 5 from ${p.ratingCount} review${p.ratingCount === 1 ? '' : 's'}. Read reviews of ${escapeHtml(p.name)}">
      ${starsHtml(p.ratingAverage)} <b>${p.ratingAverage}</b> <span class="muted">(${p.ratingCount})</span></button>`;
}

/** The reviews of one product in a dialog (reused by every page that shows product cards). */
async function openReviews(productId, productName) {
  let dlg = document.getElementById('reviews-dialog');
  if (!dlg) {
    dlg = document.createElement('dialog');
    dlg.id = 'reviews-dialog';
    dlg.className = 'nb-dialog';
    dlg.setAttribute('aria-labelledby', 'reviews-title');
    document.body.appendChild(dlg);
    dlg.addEventListener('click', (e) => { if (e.target === dlg) dlg.close(); });
  }
  dlg.innerHTML = `<div class="dlg-head"><h2 id="reviews-title">Reviews: ${escapeHtml(productName)}</h2>
    <button type="button" class="dlg-close" aria-label="Close">\u00D7</button></div><div class="dlg-body"><p class="muted">Loading\u2026</p></div>`;
  dlg.querySelector('.dlg-close').addEventListener('click', () => dlg.close());
  dlg.showModal();
  try {
    const r = await API.productReviews(productId);
    dlg.querySelector('.dlg-body').innerHTML = r.reviews.length === 0
      ? '<p class="muted">No reviews yet.</p>'
      : `<div class="review-summary">${starsHtml(r.ratingAverage, 'lg')} <b>${r.ratingAverage}</b> out of 5 <span class="muted">from ${r.ratingCount} buyer${r.ratingCount === 1 ? '' : 's'}</span></div>
         <ul class="review-list">${r.reviews.map(reviewItemHtml).join('')}</ul>`;
  } catch (e) {
    dlg.querySelector('.dlg-body').innerHTML = `<p class="field-msg error">${escapeHtml(e.message)}</p>`;
  }
}

function reviewItemHtml(rv) {
  return `<li class="review-item">
    <div>${starsHtml(rv.rating)} <span class="sr-only">${rv.rating} out of 5</span>
      <b>${escapeHtml(rv.buyerName)}</b> <span class="verified">Verified purchase</span></div>
    ${rv.comment ? `<p>${escapeHtml(rv.comment)}</p>` : ''}
    <div class="muted small">${formatDateTime(rv.createdAt)}${rv.edited ? ' (edited)' : ''}${rv.productName && rv.showProduct ? ' \u00B7 ' + escapeHtml(rv.productName) : ''}</div>
  </li>`;
}

// ---------- Saved for later (the heart on product cards) ----------

function saveButtonHtml(p) {
  return `<button type="button" class="save-btn" data-save="${p.id}" aria-pressed="false"
      aria-label="Save ${escapeHtml(p.name)} for later">${HEART_SVG}</button>`;
}

/**
 * Which products this buyer saved. Loaded once per page. Sellers and admins don't shop,
 * so their hearts are removed; logged-out visitors are sent to log in when they tap one.
 */
const NBSaved = {
  ids: new Set(),
  state: null, // null = not loaded, 'buyer', 'guest', 'other'
  loading: null,
  load() {
    if (!this.loading) {
      this.loading = API.saved().then((list) => {
        this.ids = new Set(list.map((s) => s.product.id));
        this.state = 'buyer';
      }).catch((e) => {
        this.state = e.status === 401 ? 'guest' : 'other';
      });
    }
    return this.loading;
  },
  paint(container) {
    container.querySelectorAll('.save-btn[data-save]').forEach((btn) => {
      if (this.state === 'other') { btn.remove(); return; }
      const on = this.ids.has(Number(btn.dataset.save));
      btn.setAttribute('aria-pressed', String(on));
    });
  },
};

function attachAddToCartHandlers(container) {
  NBSaved.load().then(() => NBSaved.paint(container));
  container.querySelectorAll('.save-btn[data-save]').forEach((btn) => {
    btn.addEventListener('click', async () => {
      if (NBSaved.state === 'guest') { goToLogin(); return; }
      const id = Number(btn.dataset.save);
      const on = btn.getAttribute('aria-pressed') === 'true';
      btn.disabled = true;
      try {
        if (on) {
          await API.unsave(id);
          NBSaved.ids.delete(id);
          showToast('Removed from Saved');
        } else {
          await API.save(id);
          NBSaved.ids.add(id);
          showToast('Saved for later');
        }
        NBSaved.paint(document);
      } catch (e) {
        if (e.status === 401) goToLogin(); else showToast(e.message);
      } finally {
        btn.disabled = false;
      }
    });
  });
  container.querySelectorAll('.rating-btn[data-reviews]').forEach((btn) => {
    btn.addEventListener('click', () => openReviews(Number(btn.dataset.reviews), btn.dataset.name));
  });

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

/** "Tue, 14 Oct, 6:00 pm - 8:00 pm" for an order with a booked slot, or '' (as soon as possible). */
function slotText(o) {
  if (!o || !o.slotDate || !o.slotStart) return '';
  const t = (hhmm) => {
    const [h, m] = hhmm.split(':').map(Number);
    return new Date(2000, 0, 1, h, m).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' });
  };
  const day = new Date(o.slotDate + 'T00:00:00').toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' });
  return `${day}, ${t(o.slotStart)} \u2013 ${t(o.slotEnd)}`;
}

/** Five radio buttons shown as stars (keyboard: arrow keys). name must be unique on the page. */
function starInputHtml(name, value, label) {
  return `<fieldset class="star-input">
    <legend class="sr-only">${escapeHtml(label)}</legend>
    ${[5, 4, 3, 2, 1].map(n => `<input type="radio" id="${name}-${n}" name="${name}" value="${n}" ${Number(value) === n ? 'checked' : ''}>
      <label for="${name}-${n}" title="${n} star${n === 1 ? '' : 's'}"><span class="sr-only">${n} star${n === 1 ? '' : 's'}</span>\u2605</label>`).join('')}
  </fieldset>`;
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
  window.location.href = '/login.html?next=' + encodeURIComponent(window.location.pathname + window.location.search + window.location.hash);
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
