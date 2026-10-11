package com.suryaprasanth.grocery;

import com.fasterxml.jackson.databind.JsonNode;
import com.suryaprasanth.grocery.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 6b: one basket with items from several shops. Each shop keeps its own fees and minimum,
 * and checkout creates one order per shop, all or nothing.
 *
 * In these tests: the demo shop delivers to 560xxx for Rs 30 (free from Rs 500); test sellers
 * charge Rs 20 delivery with no free-delivery amount.
 */
class MultiShopCheckoutTest extends ApiTestSupport {

    private Product product(long id) {
        return productRepository.findById(id).orElseThrow();
    }

    private JsonNode quoteWith(MockHttpSession s, Map<String, Object> req) throws Exception {
        return body(call("POST", "/api/orders/quote", s, req));
    }

    private JsonNode shopBill(JsonNode bill, long shopId) {
        for (JsonNode sb : bill.get("shops")) {
            if (sb.get("shopId").asLong() == shopId) return sb;
        }
        throw new AssertionError("shop " + shopId + " not in bill " + bill);
    }

    private JsonNode orderFor(JsonNode checkout, long shopId) {
        for (JsonNode o : checkout.get("orders")) {
            if (o.get("shopId").asLong() == shopId) return o;
        }
        throw new AssertionError("no order for shop " + shopId);
    }

    /** A checkout body with delivery/pickup choices; the expected total comes from the matching quote. */
    private Map<String, Object> checkoutWith(MockHttpSession s, String payment, String coupon,
                                             Map<String, String> fulfilment, String pincode) throws Exception {
        Map<String, Object> q = new HashMap<>();
        q.put("couponCode", coupon);
        q.put("fulfilment", fulfilment);
        q.put("pincode", pincode);
        Map<String, Object> body = checkoutBody(s, payment, coupon);
        body.put("fulfilment", fulfilment);
        body.put("pincode", pincode);
        body.put("expectedTotal", quoteWith(s, q).get("total").decimalValue());
        return body;
    }

    /** Changes one setting of the seller's shop (the edit form needs every field). */
    private void updateShop(SellerAccount seller, String field, Object value) throws Exception {
        JsonNode shop = body(call("GET", "/api/seller/shop", seller.session(), null));
        Map<String, Object> f = new HashMap<>();
        f.put("shopName", shop.get("name").asText());
        f.put("category", shop.get("category").asText());
        f.put("phone", shop.get("phone").asText());
        f.put("addressLine", shop.get("addressLine").asText());
        f.put("pincode", shop.get("pincode").asText());
        f.put("servicePincodes", "");
        f.put("openTime", shop.get("openTime").asText());
        f.put("closeTime", shop.get("closeTime").asText());
        f.put("offersDelivery", shop.get("offersDelivery").asBoolean());
        f.put("offersPickup", shop.get("offersPickup").asBoolean());
        f.put("deliveryFee", shop.get("deliveryFee").decimalValue());
        f.put("minOrderAmount", shop.get("minOrderAmount").decimalValue());
        f.put(field, value);
        MvcResult r = call("PUT", "/api/seller/shop", seller.session(), f);
        assertThat(status(r)).as(r.getResponse().getContentAsString()).isEqualTo(200);
    }

    @Test
    @DisplayName("Two shops in one basket: the bill keeps each shop's fee, and checkout creates one order per shop")
    void twoShopsTwoOrders() throws Exception {
        SellerAccount a = newApprovedSeller("560073", "");
        Product fromA = product(sellerAddsProduct(a, "A Atta", "100.00", 10));
        Product fromDemo = newProduct("200.00", 10);
        MockHttpSession buyer = newCustomer();
        addToCart(buyer, fromA, 1);
        addToCart(buyer, fromDemo, 1);

        JsonNode bill = quote(buyer, null);
        assertThat(bill.get("shops").size()).isEqualTo(2);
        assertMoney("20.00", shopBill(bill, a.shopId()).get("deliveryFee"));
        assertMoney("30.00", shopBill(bill, demoShop().getId()).get("deliveryFee"));
        assertMoney("350.00", bill.get("total")); // 100 + 20 + 200 + 30

        MvcResult r = checkout(buyer, checkoutBody(buyer, "COD", null));
        assertThat(status(r)).isEqualTo(200);
        JsonNode checkout = body(r);
        assertThat(checkout.get("orders").size()).isEqualTo(2);
        assertMoney("350.00", checkout.get("totalAmount"));
        assertMoney("120.00", orderFor(checkout, a.shopId()).get("totalAmount"));
        assertMoney("230.00", orderFor(checkout, demoShop().getId()).get("totalAmount"));
        assertThat(stockOf(fromA)).isEqualTo(9);
        assertThat(stockOf(fromDemo)).isEqualTo(9);
    }

    @Test
    @DisplayName("Pickup from a shop: no delivery fee, and the order is saved as PICKUP without an address")
    void pickupHasNoDeliveryFee() throws Exception {
        SellerAccount a = newApprovedSeller("560073", "");
        Product fromA = product(sellerAddsProduct(a, "A Jaggery", "80.00", 10));
        MockHttpSession buyer = newCustomer();
        addToCart(buyer, fromA, 1);
        Map<String, String> pickup = Map.of(String.valueOf(a.shopId()), "PICKUP");

        MvcResult r = checkout(buyer, checkoutWith(buyer, "COD", null, pickup, GOOD_PINCODE));
        assertThat(status(r)).as(r.getResponse().getContentAsString()).isEqualTo(200);
        JsonNode order = body(r).get("orders").get(0);
        assertThat(order.get("fulfilment").asText()).isEqualTo("PICKUP");
        assertMoney("0.00", order.get("deliveryFee"));
        assertMoney("80.00", order.get("totalAmount"));
        assertThat(order.get("shippingAddress").isNull()).isTrue();
    }

    @Test
    @DisplayName("Delivery outside a shop's PIN codes is refused; choosing pickup for that shop works")
    void deliveryAreaPerShop() throws Exception {
        SellerAccount a = newApprovedSeller("560073", ""); // delivers only to 560073
        Product fromA = product(sellerAddsProduct(a, "A Ghee", "300.00", 10));
        MockHttpSession buyer = newCustomer();
        addToCart(buyer, fromA, 1);

        Map<String, Object> body = checkoutWith(buyer, "COD", null, Map.of(), "560076");
        MvcResult refused = checkout(buyer, body);
        assertThat(status(refused)).isEqualTo(400);
        assertThat(body(refused).get("message").asText()).contains("doesn't deliver to 560076");

        MvcResult ok = checkout(buyer, checkoutWith(buyer, "COD", null,
                Map.of(String.valueOf(a.shopId()), "PICKUP"), "560076"));
        assertThat(status(ok)).isEqualTo(200);
    }

    @Test
    @DisplayName("A shop's minimum order is checked on its own items: below it, checkout is refused")
    void minimumOrderPerShop() throws Exception {
        SellerAccount a = newApprovedSeller("560073", "");
        updateShop(a, "minOrderAmount", 150);
        Product fromA = product(sellerAddsProduct(a, "A Salt", "100.00", 10));
        MockHttpSession buyer = newCustomer();
        addToCart(buyer, fromA, 1);
        addToCart(buyer, newProduct("500.00", 10), 1); // a big order from ANOTHER shop doesn't count

        JsonNode bill = quote(buyer, null);
        assertThat(shopBill(bill, a.shopId()).get("problems").toString()).contains("minimum order Rs 150.00");
        MvcResult r = checkout(buyer, checkoutBody(buyer, "COD", null));
        assertThat(status(r)).isEqualTo(400);
        assertThat(body(r).get("message").asText()).contains("more from");
    }

    @Test
    @DisplayName("All or nothing: if one shop's item can't be bought, no order is created for ANY shop")
    void allOrNothing() throws Exception {
        SellerAccount a = newApprovedSeller("560073", "");
        Product fromA = product(sellerAddsProduct(a, "A Poha", "60.00", 5));
        Product fromDemo = newProduct("90.00", 3);
        MockHttpSession buyer = newCustomer();
        addToCart(buyer, fromA, 2);
        addToCart(buyer, fromDemo, 1);
        Map<String, Object> body = checkoutBody(buyer, "COD", null);

        fromDemo.setStock(0); // someone else bought the last ones meanwhile
        productRepository.save(fromDemo);

        assertThat(status(checkout(buyer, body))).isEqualTo(400);
        assertThat(orderRepository.findByUserIdOrderByCreatedAtDesc(userOf(buyer).getId())).isEmpty();
        assertThat(stockOf(fromA)).isEqualTo(5);
        assertThat(body(call("GET", "/api/cart", buyer, null)).size()).isEqualTo(2);
    }

    @Test
    @DisplayName("A shop that pauses orders blocks checkout of its items, with a clear reason")
    void pausedShopBlocksCheckout() throws Exception {
        SellerAccount a = newApprovedSeller("560073", "");
        Product fromA = product(sellerAddsProduct(a, "A Ragi", "70.00", 10));
        MockHttpSession buyer = newCustomer();
        addToCart(buyer, fromA, 1);
        call("PUT", "/api/seller/shop/accepting", a.session(), Map.of("acceptingOrders", false));

        MvcResult r = checkout(buyer, checkoutBody(buyer, "COD", null));
        assertThat(status(r)).isEqualTo(400);
        assertThat(body(r).get("message").asText()).contains("isn't taking new orders");
        assertThat(stockOf(fromA)).isEqualTo(10);
    }

    @Test
    @DisplayName("Coupon on a two-shop basket: discount split by item total, and it counts as ONE use")
    void couponSplitAndCountedOnce() throws Exception {
        SellerAccount a = newApprovedSeller("560073", "");
        Product fromA = product(sellerAddsProduct(a, "A Basmati", "200.00", 10));
        Product fromDemo = newProduct("200.00", 10);
        MockHttpSession buyer = newCustomer();
        addToCart(buyer, fromA, 1);
        addToCart(buyer, fromDemo, 1);

        MvcResult r = checkout(buyer, checkoutBody(buyer, "COD", "FRESH50"));
        assertThat(status(r)).as(r.getResponse().getContentAsString()).isEqualTo(200);
        JsonNode checkout = body(r);
        assertMoney("25.00", orderFor(checkout, a.shopId()).get("discount"));
        assertMoney("25.00", orderFor(checkout, demoShop().getId()).get("discount"));
        assertMoney("400.00", checkout.get("totalAmount")); // 400 - 50 + 20 + 30

        // Same buyer, new basket: FRESH50 (one use per buyer) is already used, once
        addToCart(buyer, newProduct("350.00", 10), 1);
        JsonNode again = quote(buyer, "FRESH50");
        assertThat(again.get("couponApplied").asBoolean()).isFalse();
        assertThat(again.get("couponMessage").asText()).contains("already used");
    }

    @Test
    @DisplayName("One UPI payment covers both shops: success marks both paid; failure frees both shops' stock")
    void upiCoversAllShops() throws Exception {
        SellerAccount a = newApprovedSeller("560073", "");
        Product fromA = product(sellerAddsProduct(a, "A Honey", "150.00", 10));
        Product fromDemo = newProduct("120.00", 10);

        MockHttpSession payer = newCustomer();
        addToCart(payer, fromA, 1);
        addToCart(payer, fromDemo, 1);
        JsonNode paid = body(checkout(payer, checkoutBody(payer, "UPI", null)));
        long firstOrder = paid.get("orders").get(0).get("id").asLong();
        call("POST", "/api/orders/" + firstOrder + "/pay", payer, Map.of("outcome", "SUCCESS"));
        for (JsonNode o : paid.get("orders")) {
            JsonNode now = body(call("GET", "/api/orders/" + o.get("id").asLong(), payer, null));
            assertThat(now.get("status").asText()).isEqualTo("PLACED");
            assertThat(now.get("paymentStatus").asText()).isEqualTo("PAID");
        }

        MockHttpSession failer = newCustomer();
        addToCart(failer, fromA, 2);
        addToCart(failer, fromDemo, 2);
        JsonNode failed = body(checkout(failer, checkoutBody(failer, "UPI", null)));
        assertThat(stockOf(fromA)).isEqualTo(7);
        call("POST", "/api/orders/" + failed.get("orders").get(1).get("id").asLong() + "/pay", failer, Map.of("outcome", "FAILED"));
        assertThat(stockOf(fromA)).isEqualTo(9);
        assertThat(stockOf(fromDemo)).isEqualTo(9);
        for (JsonNode o : failed.get("orders")) {
            assertThat(body(call("GET", "/api/orders/" + o.get("id").asLong(), failer, null)).get("status").asText())
                    .isEqualTo("PAYMENT_FAILED");
        }
    }

    @Test
    @DisplayName("Each seller sees only their own shop's order from a shared basket; the other order is 404")
    void sellersSeeOnlyOwnOrders() throws Exception {
        SellerAccount a = newApprovedSeller("560073", "");
        SellerAccount b = newApprovedSeller("560073", "");
        Product fromA = product(sellerAddsProduct(a, "A Tea", "90.00", 10));
        Product fromB = product(sellerAddsProduct(b, "B Coffee", "110.00", 10));
        MockHttpSession buyer = newCustomer();
        addToCart(buyer, fromA, 1);
        addToCart(buyer, fromB, 1);
        JsonNode checkout = body(checkout(buyer, checkoutBody(buyer, "COD", null)));
        long aOrder = orderFor(checkout, a.shopId()).get("id").asLong();
        long bOrder = orderFor(checkout, b.shopId()).get("id").asLong();

        JsonNode aList = body(call("GET", "/api/seller/orders", a.session(), null));
        assertThat(aList.get("totalItems").asLong()).isEqualTo(1);
        assertThat(aList.get("items").get(0).get("id").asLong()).isEqualTo(aOrder);
        assertThat(status(call("GET", "/api/seller/orders/" + bOrder, a.session(), null))).isEqualTo(404);
        assertThat(status(call("PUT", "/api/seller/orders/" + bOrder + "/status", a.session(), Map.of("status", "ACCEPTED")))).isEqualTo(404);
        assertThat(status(call("POST", "/api/seller/orders/" + bOrder + "/reject", a.session(), Map.of("reason", "Not mine to reject")))).isEqualTo(404);
        assertThat(orderRepository.findById(bOrder).orElseThrow().getStatus().name()).isEqualTo("PLACED");
    }

    private static void assertMoney(String expected, JsonNode actual) {
        assertThat(actual.decimalValue()).isEqualByComparingTo(new BigDecimal(expected));
    }

    private com.suryaprasanth.grocery.model.User userOf(MockHttpSession s) throws Exception {
        long id = body(call("GET", "/api/auth/me", s, null)).get("id").asLong();
        return userRepository.findById(id).orElseThrow();
    }
}
