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
    </div>`;
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
  return '';
}
