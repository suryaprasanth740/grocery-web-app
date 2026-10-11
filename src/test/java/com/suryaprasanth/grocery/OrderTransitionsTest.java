package com.suryaprasanth.grocery;

import com.fasterxml.jackson.databind.JsonNode;
import com.suryaprasanth.grocery.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 6b: the shop moves its orders through the steps; only valid steps are allowed;
 * rejecting and cancelling return stock exactly once.
 */
class OrderTransitionsTest extends ApiTestSupport {

    private record Placed(SellerAccount seller, MockHttpSession buyer, Product product, long orderId) { }

    /** A seller with one product (stock 10) and a buyer who ordered `qty` of it with the given fulfilment. */
    private Placed placeWithSeller(int qty, String fulfilment, String payment) throws Exception {
        SellerAccount seller = newApprovedSeller("560073", "");
        Product product = productRepository.findById(sellerAddsProduct(seller, "Shop Item", "50.00", 10)).orElseThrow();
        MockHttpSession buyer = newCustomer();
        addToCart(buyer, product, qty);
        Map<String, Object> body = checkoutBody(buyer, payment, null);
        Map<String, String> choice = Map.of(String.valueOf(seller.shopId()), fulfilment);
        body.put("fulfilment", choice);
        Map<String, Object> q = new HashMap<>();
        q.put("fulfilment", choice);
        body.put("expectedTotal", body(call("POST", "/api/orders/quote", buyer, q)).get("total").decimalValue());
        MvcResult r = checkout(buyer, body);
        assertThat(status(r)).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return new Placed(seller, buyer, product, body(r).get("orders").get(0).get("id").asLong());
    }

    private MvcResult step(Placed p, String next) throws Exception {
        return call("PUT", "/api/seller/orders/" + p.orderId() + "/status", p.seller().session(), Map.of("status", next));
    }

    private JsonNode buyerView(Placed p) throws Exception {
        return body(call("GET", "/api/orders/" + p.orderId(), p.buyer(), null));
    }

    @Test
    @DisplayName("Delivery order: PLACED -> ACCEPTED -> PREPARING -> OUT_FOR_DELIVERY -> DELIVERED; cash marked paid at the end")
    void deliveryFlow() throws Exception {
        Placed p = placeWithSeller(1, "DELIVERY", "COD");
        for (String s : new String[]{"ACCEPTED", "PREPARING", "OUT_FOR_DELIVERY", "DELIVERED"}) {
            assertThat(status(step(p, s))).as(s).isEqualTo(200);
            assertThat(buyerView(p).get("status").asText()).isEqualTo(s);
        }
        assertThat(buyerView(p).get("paymentStatus").asText()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("Pickup order uses READY_FOR_PICKUP; OUT_FOR_DELIVERY is refused for pickup, and the other way round")
    void pickupOnlySteps() throws Exception {
        Placed pickup = placeWithSeller(1, "PICKUP", "COD");
        step(pickup, "ACCEPTED");
        step(pickup, "PREPARING");
        assertThat(status(step(pickup, "OUT_FOR_DELIVERY"))).isEqualTo(409);
        assertThat(status(step(pickup, "READY_FOR_PICKUP"))).isEqualTo(200);
        assertThat(status(step(pickup, "DELIVERED"))).isEqualTo(200);

        Placed delivery = placeWithSeller(1, "DELIVERY", "COD");
        step(delivery, "ACCEPTED");
        step(delivery, "PREPARING");
        assertThat(status(step(delivery, "READY_FOR_PICKUP"))).isEqualTo(409);
    }

    @Test
    @DisplayName("Skipping steps is refused: PLACED straight to PREPARING or DELIVERED gives 409")
    void noSkipping() throws Exception {
        Placed p = placeWithSeller(1, "DELIVERY", "COD");
        assertThat(status(step(p, "PREPARING"))).isEqualTo(409);
        assertThat(status(step(p, "DELIVERED"))).isEqualTo(409);
        assertThat(buyerView(p).get("status").asText()).isEqualTo("PLACED");
    }

    @Test
    @DisplayName("Shop rejects with a reason: order CANCELLED, buyer sees why, stock goes back; a reason is required")
    void sellerReject() throws Exception {
        Placed p = placeWithSeller(3, "DELIVERY", "COD");
        assertThat(stockOf(p.product())).isEqualTo(7);
        String url = "/api/seller/orders/" + p.orderId() + "/reject";

        assertThat(status(call("POST", url, p.seller().session(), Map.of("reason", " ")))).isEqualTo(400);
        assertThat(status(call("POST", url, p.seller().session(), Map.of("reason", "Out of fresh stock today")))).isEqualTo(200);

        JsonNode seen = buyerView(p);
        assertThat(seen.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(seen.get("rejectionReason").asText()).isEqualTo("Out of fresh stock today");
        assertThat(stockOf(p.product())).isEqualTo(10);
        // Rejecting again is refused and does NOT add stock a second time
        assertThat(status(call("POST", url, p.seller().session(), Map.of("reason", "Again, by mistake")))).isEqualTo(409);
        assertThat(stockOf(p.product())).isEqualTo(10);
    }

    @Test
    @DisplayName("A shop can't reject once it has started preparing, and can't use CANCELLED as a plain step")
    void rejectRules() throws Exception {
        Placed p = placeWithSeller(1, "DELIVERY", "COD");
        assertThat(status(step(p, "CANCELLED"))).isEqualTo(400);
        step(p, "ACCEPTED");
        step(p, "PREPARING");
        assertThat(status(call("POST", "/api/seller/orders/" + p.orderId() + "/reject", p.seller().session(),
                Map.of("reason", "Changed my mind")))).isEqualTo(409);
    }

    @Test
    @DisplayName("Buyer cancels while ACCEPTED: stock back once; a repeated cancel is refused and changes nothing")
    void repeatedCancel() throws Exception {
        Placed p = placeWithSeller(2, "DELIVERY", "COD");
        step(p, "ACCEPTED");
        String url = "/api/orders/" + p.orderId() + "/cancel";

        assertThat(status(call("POST", url, p.buyer(), null))).isEqualTo(200);
        assertThat(stockOf(p.product())).isEqualTo(10);
        assertThat(status(call("POST", url, p.buyer(), null))).isEqualTo(409);
        assertThat(stockOf(p.product())).isEqualTo(10);
    }

    @Test
    @DisplayName("Unpaid UPI orders are hidden from the shop until the payment goes through")
    void unpaidHiddenFromShop() throws Exception {
        Placed p = placeWithSeller(1, "DELIVERY", "UPI");
        assertThat(body(call("GET", "/api/seller/orders", p.seller().session(), null)).get("totalItems").asLong()).isZero();
        assertThat(status(call("GET", "/api/seller/orders/" + p.orderId(), p.seller().session(), null))).isEqualTo(404);

        call("POST", "/api/orders/" + p.orderId() + "/pay", p.buyer(), Map.of("outcome", "SUCCESS"));
        assertThat(body(call("GET", "/api/seller/orders?status=new", p.seller().session(), null)).get("totalItems").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("Seller dashboard counts come from real orders: new, completed, sales today, best seller, low stock")
    void dashboardNumbers() throws Exception {
        SellerAccount fresh = newApprovedSeller("560073", "");
        JsonNode empty = body(call("GET", "/api/seller/summary", fresh.session(), null));
        assertThat(empty.get("totalOrders").asLong()).isZero();
        assertThat(empty.get("bestSellers").size()).isZero();

        Placed p = placeWithSeller(6, "DELIVERY", "COD"); // stock 10 -> 4 = low stock
        for (String s : new String[]{"ACCEPTED", "PREPARING", "OUT_FOR_DELIVERY", "DELIVERED"}) step(p, s);
        JsonNode sum = body(call("GET", "/api/seller/summary", p.seller().session(), null));
        assertThat(sum.get("completed").asLong()).isEqualTo(1);
        assertThat(sum.get("newOrders").asLong()).isZero();
        assertThat(sum.get("salesToday").decimalValue()).isEqualByComparingTo("320.00"); // 6 x 50 + 20 delivery
        assertThat(sum.get("bestSellers").get(0).get("name").asText()).isEqualTo("Shop Item");
        assertThat(sum.get("bestSellers").get(0).get("quantity").asInt()).isEqualTo(6);
        assertThat(sum.get("lowStock").get(0).get("stock").asInt()).isEqualTo(4);
        assertThat(sum.get("daily").size()).isEqualTo(7);
    }

    @Test
    @DisplayName("Order again: available items go back in the cart; a product switched off is skipped with a reason")
    void reorder() throws Exception {
        Placed p = placeWithSeller(2, "DELIVERY", "COD");
        JsonNode again = body(call("POST", "/api/orders/" + p.orderId() + "/reorder", p.buyer(), null));
        assertThat(again.get("added").size()).isEqualTo(1);
        assertThat(again.get("cart").get(0).get("quantity").asInt()).isEqualTo(2);

        call("DELETE", "/api/cart/clear", p.buyer(), null);
        call("PUT", "/api/seller/products/" + p.product().getId() + "/active", p.seller().session(), Map.of("active", false));
        JsonNode skipped = body(call("POST", "/api/orders/" + p.orderId() + "/reorder", p.buyer(), null));
        assertThat(skipped.get("added").size()).isZero();
        assertThat(skipped.get("skipped").get(0).asText()).contains("no longer sold");
    }

    @Test
    @DisplayName("Buyers can't open the seller order screens (403)")
    void buyersCantManageOrders() throws Exception {
        assertThat(status(call("GET", "/api/seller/orders", newCustomer(), null))).isEqualTo(403);
        assertThat(status(call("GET", "/api/seller/summary", newCustomer(), null))).isEqualTo(403);
    }
}
