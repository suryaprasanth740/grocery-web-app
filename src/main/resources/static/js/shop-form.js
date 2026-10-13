// The shop-details form, shared by "Sell on NammaBasket" (sell.html) and the seller dashboard (seller.html).
// The server checks every rule again; these checks only give faster feedback.

const SHOP_CATEGORIES = [
  ['KIRANA', 'Kirana / general store'],
  ['SUPERMARKET', 'Supermarket'],
  ['FRUITS_VEGETABLES', 'Fruits & vegetables'],
  ['DAIRY', 'Dairy & milk booth'],
  ['BAKERY', 'Bakery'],
  ['ORGANIC', 'Organic store'],
];

/** HTML for the shop fields. `shop` is an existing shop (to edit) or null (new application). */
function shopFieldsHtml(shop) {
  const v = (key, fallback = '') => escapeHtml(shop && shop[key] != null ? shop[key] : fallback);
  const extraPins = shop ? shop.servicePincodes.filter(p => p !== shop.pincode).join(', ') : '';
  const checked = (key, fallback) => (shop ? shop[key] : fallback) ? 'checked' : '';
  return `
    <div class="field"><label for="f-shopName">Shop name</label>
      <input id="f-shopName" name="shopName" maxlength="80" required value="${v('name')}" placeholder="Sri Lakshmi Stores"></div>
    <div class="form-2">
      <div class="field"><label for="f-category">Type of shop</label>
        <select id="f-category" name="category">${SHOP_CATEGORIES.map(([val, label]) =>
          `<option value="${val}" ${shop && shop.category === val ? 'selected' : ''}>${label}</option>`).join('')}</select></div>
      <div class="field"><label for="f-phone">Shop phone</label>
        <input id="f-phone" name="phone" inputmode="numeric" maxlength="10" required value="${v('phone')}" placeholder="9876543210"></div>
    </div>
    <div class="field"><label for="f-addressLine">Shop address</label>
      <input id="f-addressLine" name="addressLine" maxlength="250" required value="${v('addressLine')}" placeholder="Door no., street, area, city"></div>
    <div class="form-2">
      <div class="field"><label for="f-pincode">Shop PIN code</label>
        <input id="f-pincode" name="pincode" inputmode="numeric" maxlength="6" required value="${v('pincode')}" placeholder="560073"></div>
      <div class="field"><label for="f-servicePincodes">Also deliver to <span class="muted">(optional)</span></label>
        <input id="f-servicePincodes" name="servicePincodes" maxlength="400" value="${escapeHtml(extraPins)}" placeholder="560076, 560068 or 560xxx"></div>
    </div>
    <p class="bill-note" style="margin-top:-8px;">Separate PIN codes with commas. <b>560xxx</b> means every PIN code starting with 560.</p>
    <div class="form-2">
      <div class="field"><label for="f-openTime">Opens at</label>
        <input id="f-openTime" name="openTime" type="time" required value="${v('openTime', '08:00')}"></div>
      <div class="field"><label for="f-closeTime">Closes at</label>
        <input id="f-closeTime" name="closeTime" type="time" required value="${v('closeTime', '21:00')}"></div>
    </div>
    <fieldset class="field fieldset">
      <legend>How buyers get their order</legend>
      <label class="choice"><input type="checkbox" name="offersDelivery" ${checked('offersDelivery', true)}> We deliver</label>
      <label class="choice"><input type="checkbox" name="offersPickup" ${checked('offersPickup', true)}> Buyers can pick up from the shop</label>
    </fieldset>
    <div class="form-3">
      <div class="field"><label for="f-deliveryFee">Delivery fee (&#8377;)</label>
        <input id="f-deliveryFee" name="deliveryFee" type="number" min="0" max="200" step="1" required value="${v('deliveryFee', '30')}"></div>
      <div class="field"><label for="f-freeDeliveryAbove">Free delivery from (&#8377;) <span class="muted">(optional)</span></label>
        <input id="f-freeDeliveryAbove" name="freeDeliveryAbove" type="number" min="1" max="10000" step="1" value="${v('freeDeliveryAbove')}"></div>
      <div class="field"><label for="f-minOrderAmount">Minimum order (&#8377;)</label>
        <input id="f-minOrderAmount" name="minOrderAmount" type="number" min="0" max="5000" step="1" required value="${v('minOrderAmount', '0')}"></div>
    </div>
    <div class="form-2">
      <div class="field"><label for="f-gstin">GSTIN <span class="muted">(optional, printed on invoices)</span></label>
        <input id="f-gstin" name="gstin" maxlength="15" autocapitalize="characters" spellcheck="false" value="${v('gstin')}" placeholder="29ABCDE1234F1Z5"></div>
      <div class="field"><label for="f-slotCapacity">Time slots</label>
        <label class="choice"><input type="checkbox" name="slotsEnabled" ${checked('slotsEnabled', false)}> Buyers can choose a 2-hour slot (today or tomorrow)</label>
        <div class="inline-row" style="align-items:center;margin-top:6px;">
          <input id="f-slotCapacity" name="slotCapacity" type="number" min="1" max="50" step="1" value="${v('slotCapacity', '10')}" style="max-width:90px;">
          <span class="muted small">orders per slot at most</span>
        </div></div>
    </div>
    ${shop ? `
    <div class="form-2">
      <div class="field"><label for="f-logo">Shop photo or logo <span class="muted">(optional)</span></label>
        <div class="photo-pick">
          <div class="shop-logo" id="logo-preview">${shop.logoUrl ? `<img src="${escapeHtml(shop.logoUrl)}" alt="">` : '&#127978;'}</div>
          <input id="f-logo" type="file" accept="image/jpeg,image/png,image/webp">
        </div>
        <input type="hidden" name="logoUrl" value="${v('logoUrl')}">
        <div class="field-msg" id="logo-msg" aria-live="polite"></div></div>
      <div class="field"><label>Map position <span class="muted">(optional, shows buyers the distance)</span></label>
        <input type="hidden" name="latitude" value="${v('latitude')}">
        <input type="hidden" name="longitude" value="${v('longitude')}">
        <div class="muted small" id="pos-text">${shop.latitude != null ? `Saved: ${shop.latitude}, ${shop.longitude}` : 'Not set'}</div>
        <div class="action-row" style="margin:6px 0 0;">
          <button type="button" class="btn btn-outline btn-sm" id="pos-here">Use my current location</button>
          <button type="button" class="btn btn-outline btn-sm" id="pos-clear">Clear</button>
        </div>
        <div class="field-msg" id="pos-msg" aria-live="polite"></div></div>
    </div>` : ''}`;
}

/** Shrinks a photo in the browser (longest side 900 px, JPEG) so it fits the 300 KB limit. */
async function shrinkPhoto(file) {
  if (!/^image\/(jpeg|png|webp)$/.test(file.type)) throw new Error('Choose a JPEG, PNG or WebP photo.');
  const img = await new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const i = new Image();
    i.onload = () => { URL.revokeObjectURL(url); resolve(i); };
    i.onerror = () => { URL.revokeObjectURL(url); reject(new Error('That file could not be read as a photo.')); };
    i.src = url;
  });
  const scale = Math.min(1, 900 / Math.max(img.width, img.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(img.width * scale);
  canvas.height = Math.round(img.height * scale);
  canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height);
  for (const q of [0.85, 0.75, 0.65, 0.5]) {
    const blob = await new Promise(r => canvas.toBlob(r, 'image/jpeg', q));
    if (blob && blob.size <= 300 * 1024) return blob;
  }
  throw new Error('This photo is still too big after shrinking. Try a smaller one.');
}

/** Wires the logo picker and the "use my location" buttons on the shop form. */
function wireShopExtras(form) {
  const logo = form.querySelector('#f-logo');
  if (logo) {
    logo.addEventListener('change', async () => {
      const msg = form.querySelector('#logo-msg');
      if (!logo.files[0]) return;
      msg.className = 'field-msg'; msg.textContent = 'Uploading\u2026';
      try {
        const up = await API.uploadImage(await shrinkPhoto(logo.files[0]), 'logo.jpg');
        form.elements.logoUrl.value = up.url;
        form.querySelector('#logo-preview').innerHTML = `<img src="${escapeHtml(up.url)}" alt="">`;
        msg.className = 'field-msg ok'; msg.textContent = 'Photo ready. Save changes to use it.';
      } catch (e) { msg.className = 'field-msg error'; msg.textContent = e.message; }
    });
  }
  const here = form.querySelector('#pos-here');
  if (here) {
    here.addEventListener('click', async () => {
      const msg = form.querySelector('#pos-msg');
      try {
        const c = await NBLocation.locate();
        form.elements.latitude.value = c.lat;
        form.elements.longitude.value = c.lng;
        form.querySelector('#pos-text').textContent = `New: ${c.lat}, ${c.lng}`;
        msg.className = 'field-msg ok'; msg.textContent = 'Use this only when you are at the shop. Save changes to keep it.';
      } catch (e) { msg.className = 'field-msg error'; msg.textContent = e.message; }
    });
    form.querySelector('#pos-clear').addEventListener('click', () => {
      form.elements.latitude.value = '';
      form.elements.longitude.value = '';
      form.querySelector('#pos-text').textContent = 'Not set';
    });
  }
}

/** Reads the shop fields inside `form` into the JSON the API expects. */
function readShopFields(form) {
  const val = (name) => form.elements[name].value.trim();
  const num = (name) => val(name) === '' ? null : Number(val(name));
  return {
    shopName: val('shopName'),
    category: val('category'),
    phone: val('phone').replace(/\s+/g, ''),
    addressLine: val('addressLine'),
    pincode: val('pincode'),
    servicePincodes: val('servicePincodes'),
    openTime: val('openTime'),
    closeTime: val('closeTime'),
    offersDelivery: form.elements.offersDelivery.checked,
    offersPickup: form.elements.offersPickup.checked,
    deliveryFee: num('deliveryFee'),
    freeDeliveryAbove: num('freeDeliveryAbove'),
    minOrderAmount: num('minOrderAmount'),
    gstin: form.elements.gstin ? val('gstin').toUpperCase() : null,
    slotsEnabled: form.elements.slotsEnabled ? form.elements.slotsEnabled.checked : false,
    slotCapacity: form.elements.slotCapacity && val('slotCapacity') !== '' ? num('slotCapacity') : null,
    logoUrl: form.elements.logoUrl ? (form.elements.logoUrl.value || null) : null,
    latitude: form.elements.latitude && form.elements.latitude.value !== '' ? Number(form.elements.latitude.value) : null,
    longitude: form.elements.longitude && form.elements.longitude.value !== '' ? Number(form.elements.longitude.value) : null,
  };
}

/** Quick checks before sending. Returns an error message, or '' when it looks fine. */
function checkShopFields(d) {
  if (!d.shopName) return 'Enter the shop name.';
  if (!/^[6-9][0-9]{9}$/.test(d.phone)) return 'Enter a 10-digit mobile number for the shop, e.g. 9876543210.';
  if (d.addressLine.length < 10) return 'Enter the full shop address.';
  if (!/^[1-9][0-9]{5}$/.test(d.pincode)) return 'Enter the shop’s 6-digit PIN code.';
  if (!d.openTime || !d.closeTime || d.closeTime <= d.openTime) return 'Closing time must be after opening time.';
  if (!d.offersDelivery && !d.offersPickup) return 'Choose delivery, pickup, or both.';
  if (d.deliveryFee == null) return 'Enter a delivery fee (0 if delivery is free).';
  if (d.minOrderAmount == null) return 'Enter a minimum order (0 for no minimum).';
  if (d.gstin && !/^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/.test(d.gstin)) return 'GSTIN must look like 29ABCDE1234F1Z5 (15 characters), or be left empty.';
  if (d.slotsEnabled && !(d.slotCapacity >= 1 && d.slotCapacity <= 50)) return 'Orders per slot must be between 1 and 50.';
  return '';
}
