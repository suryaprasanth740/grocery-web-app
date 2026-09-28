# FreshCart — Test Cases

Edge cases that most e-commerce apps (including big ones) miss, and how FreshCart handles them.
Every case has an **automated test** (runs on every push in GitHub Actions) and a **manual check**
you can do on the live site.

**Test data:** coupons `FRESH50`, `SAVE10`, `BIG100`, `OLD20` (expired) · PIN `560073` (served), `110001` (not served) ·
admin login `admin@freshcart.com` (password = `ADMIN_PASSWORD`).

Legend — Priority: **H** High, **M** Medium, **L** Low. Type: **N** negative, **B** boundary, **C** concurrency, **S** security, **P** positive.

## 1. Stock, cart and duplicate orders

| ID | Type | Scenario | Steps | Expected result | Pri | Automated test |
|---|---|---|---|---|---|---|
| TC_01 | C | Two customers buy the last item at the same moment | Stock = 1. Two users check out together | Only one order succeeds; stock = 0, never −1 | H | `CriticalFixesTest.lastItemRace` |
| TC_02 | C | Double click on "Place order" | Send the same checkout (same request id) twice | Same order returned; only 1 order; stock taken once | H | `CriticalFixesTest.doubleClickSameRequestId` |
| TC_02b | C | Same cart checked out in two tabs | Two checkouts, different request ids, same cart, same time | Only 1 order is created | H | `CriticalFixesTest.twoTabsSameCart` |
| TC_03 | N/B | Add more than the stock | Stock 5 → add 6, then add 5 | 6 refused ("Only 5 left"); 5 accepted | H | `CriticalFixesTest.cannotAddMoreThanStock` |
| TC_04 | B | Quantity limits | Add 21, 0, −1, then 20, then 1 more | 21/0/−1 refused; 20 accepted; 21st refused | M | `CriticalFixesTest.quantityBoundaries` |
| TC_05 | N | Wrong value for payment method | `paymentMethod: "BITCOIN"` | Clean 400 error, no crash | L | `CriticalFixesTest.badEnumValue` |

## 2. Address, price and bill

| ID | Type | Scenario | Steps | Expected result | Pri | Automated test |
|---|---|---|---|---|---|---|
| TC_06 | B | Address length | 7 chars, 301 chars, exactly 300 chars | 7 and 301 refused with 400 (not 500); 300 accepted | M | `CriticalFixesTest.addressLengthBoundary` |
| TC_07 | N | Price changed after adding to cart | Add at ₹40 → admin sets ₹45 → checkout with old total | Cart shows "price changed"; order refused (409) | H | `CriticalFixesTest.priceChangedAfterAdding` |
| TC_08 | S | Price tampering | Send `expectedTotal: 1.00` | Refused (409); nothing reserved | H | `CriticalFixesTest.priceTampering` |
| TC_09 | B | Free delivery boundary | Cart ₹499, then cart ₹500 | ₹499 → ₹30 fee, "add ₹1 more"; ₹500 → free | M | `CheckoutFeaturesTest.deliveryFeeBoundary` |
| TC_10 | P | Order total = bill shown | 2 × ₹120, COD | Order total ₹270 = cart bill | H | `CheckoutFeaturesTest.orderMatchesQuote` |
| TC_11 | P | GST shown | ₹105 item at 5% GST | "Includes GST of ₹5.00" | L | `CheckoutFeaturesTest.gstIncluded` |

## 3. PIN code and payment

| ID | Type | Scenario | Steps | Expected result | Pri | Automated test |
|---|---|---|---|---|---|---|
| TC_12 | N/B | PIN code formats | `560073`, `110001`, `012345`, `56007`, `5600731`, `56OO73` | Only 560xxx is served; others invalid or "we don't deliver" | H | `CheckoutFeaturesTest.pincodeCheck` |
| TC_13 | N | Checkout to an unserved PIN | PIN `110001` | Refused before any stock is taken | H | `CheckoutFeaturesTest.checkoutToUnservedPincode` |
| TC_14 | B | COD limit | Total ₹3000 with COD; total ₹3000.01 with COD, then UPI | ₹3000 OK; ₹3000.01 COD refused, UPI OK | M | `CheckoutFeaturesTest.codLimit` |
| TC_15 | P | UPI success | Order with UPI → "Payment succeeds" | Status Placed, payment Paid | H | `OrderLifecycleTest.upiSuccess` |
| TC_16 | N | UPI failure | Order with UPI → "Payment fails" | Payment failed; stock back; items back in cart | H | `OrderLifecycleTest.upiFailure` |
| TC_17 | N | UPI timeout, then money arrives late | Leave 10+ min → then payment succeeds | Order expires, stock back; late money **refunded** | H | `OrderLifecycleTest.upiTimeoutThenLatePayment` |

## 4. Coupons

| ID | Type | Scenario | Steps | Expected result | Pri | Automated test |
|---|---|---|---|---|---|---|
| TC_18 | B | Minimum order | `FRESH50` on ₹298, then on ₹299 | ₹298: "add ₹1 more"; ₹299: ₹50 off | M | `CheckoutFeaturesTest.couponMinimumBoundary` |
| TC_19 | N | Expired / unknown coupon | `OLD20`, `NOPE123` | "has expired" / "does not exist" | M | `CheckoutFeaturesTest.expiredAndUnknownCoupons` |
| TC_20 | B | Percent cap | `SAVE10` on ₹2000 | Discount ₹100 (cap), not ₹200 | M | `CheckoutFeaturesTest.percentCouponCap` |
| TC_21 | B | Discount bigger than cart | ₹100 flat coupon on ₹60 | Discount ₹60; total never negative | M | `CheckoutFeaturesTest.discountNeverMoreThanItems` |
| TC_22 | N | Coupon reuse | Use `FRESH50`, try again, cancel first order, try again | 2nd refused ("already used"); works after cancel | H | `CheckoutFeaturesTest.couponReuse` |

## 5. Cancel, missing items, expiry

| ID | Type | Scenario | Steps | Expected result | Pri | Automated test |
|---|---|---|---|---|---|---|
| TC_23 | P/N | Cancel a placed order | Cancel, then cancel again | Cancelled, stock back, not charged; 2nd cancel refused | H | `OrderLifecycleTest.cancelPlacedOrder` |
| TC_24 | N | Cancel after packing / paid order | Admin marks Packed → cancel; paid UPI → cancel | Packed: refused; paid: **refunded** | H | `OrderLifecycleTest.cancelRules` |
| TC_25 | N/B | Report missing item | Before delivery; after delivery 1 of 2, then 2, then 1, then 1, wrong item | Only after delivery; never more than ordered | H | `OrderLifecycleTest.reportMissingItem` |
| TC_26 | P | Refund with coupon | ₹300 item, `FRESH50`, report missing | Refund ₹250 (what was actually paid) | M | `OrderLifecycleTest.refundWithCoupon` |
| TC_27 | B | 48-hour report window | Report 49 hours after delivery | Refused | M | `OrderLifecycleTest.reportWindowClosed` |
| TC_28 | B | Expiry boundary | Item expiring today; item expiring tomorrow | Today: cannot add; tomorrow: can add | H | `OrderLifecycleTest.expiryBoundary` |
| TC_29 | N | Item expires while in cart | Add milk → admin sets old date → checkout | Cart shows the problem; checkout refused | H | `OrderLifecycleTest.expiresWhileInCart` |

## 6. Security and admin

| ID | Type | Scenario | Steps | Expected result | Pri | Automated test |
|---|---|---|---|---|---|---|
| TC_30 | S | Another customer's order (IDOR) | Open / cancel / pay order of another user | 403 every time; 401 when logged out | H | `SecurityAndAdminTest.cannotTouchOtherCustomersOrder` |
| TC_31 | S | Brute-force login | 5 wrong passwords, then the right one | 6th attempt refused (429) for 15 min | H | `SecurityAndAdminTest.loginLockout` |
| TC_32 | S | Admin pages | Customer / logged out / admin open `/api/admin/orders` | 403 / 401 / 200 | H | `SecurityAndAdminTest.adminOnly` |
| TC_33 | N | Skipping order steps | Admin: Placed → Delivered | Refused; Placed → Packed OK | M | `SecurityAndAdminTest.noSkippingStatus` |
| TC_34 | N/B | Admin product form | Price 0, stock −1, GST 7% | All refused with a clear message | M | `SecurityAndAdminTest.adminProductValidation` |
| TC_35 | N | Same email twice | Register `a@x.com`, then `A@X.COM` | 2nd refused (409) | M | `SecurityAndAdminTest.duplicateEmail` |

## Manual UI checks (on the live site)

1. **Cart:** change a product price in Admin → the customer's cart shows "Prices changed" with the old price crossed out.
2. **Checkout:** type `110001` → "we don't deliver", button stays disabled. Type `560073` → delivery time shown, button enabled.
3. **Coupon:** `FRESH50` on a small cart → "Add ₹X more". On a bigger cart → discount line appears in the bill.
4. **Double click:** double-click "Place order" fast → only one order in My Orders.
5. **UPI demo:** choose UPI → try "Payment fails" → items come back to the cart.
6. **Admin:** mark an order Packed → Out for delivery → Delivered → on the customer's order page, "Something missing?" appears.
7. **Mobile:** open every page at 390 px width → nothing goes off-screen.
