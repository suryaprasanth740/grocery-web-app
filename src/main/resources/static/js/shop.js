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
  const canBuy = p.stock > 0 && !p.expired;
  return `
    <div class="product-card">
      <div class="product-photo">
        ${p.stock === 0 ? `<span class="stock-tag">Out of stock</span>` : lowStock ? `<span class="stock-tag">Only ${p.stock} left</span>` : ''}
        ${p.imageUrl ? `<img src="${escapeHtml(p.imageUrl)}" alt="${escapeHtml(p.name)}" loading="lazy" onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">` : ''}
        <span class="photo-emoji" style="${p.imageUrl ? 'display:none;' : ''}">${p.imageEmoji || '\u{1F6D2}'}</span>
      </div>
      <div class="body">
        <h3>${escapeHtml(p.name)}</h3>
        <div class="unit">${escapeHtml(p.unit || '')}</div>
        <div class="desc">${escapeHtml(p.description || '')}</div>
        ${expiryTagHtml(p)}
        <div class="row">
          <span class="price">${formatRupees(p.price)}</span>
          ${canBuy
            ? `<button class="add-to-cart-btn" data-id="${p.id}">Add</button>`
            : `<button class="add-to-cart-btn" disabled>Add</button>`}
        </div>
      </div>
    </div>
  `;
}

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

function formatDateTime(iso) {
  return new Date(iso).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
}

// ---------- Order status ----------

const STATUS_LABELS = {
  PAYMENT_PENDING: ['Waiting for payment', 'warn'],
  PLACED: ['Placed', 'info'],
  PACKED: ['Packed', 'info'],
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
