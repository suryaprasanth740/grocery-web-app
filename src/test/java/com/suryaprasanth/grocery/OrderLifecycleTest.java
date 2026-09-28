package com.suryaprasanth.grocery;

import com.fasterxml.jackson.databind.JsonNode;
import com.suryaprasanth.grocery.model.Order;
import com.suryaprasanth.grocery.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 2 + 3: UPI payment, cancel, missing-item reports, expiry dates. */
class OrderLifecycleTest extends ApiTestSupport {

    private JsonNode pay(MockHttpSession s, long orderId, String outcome) throws Exception {
        return body(call("POST", "/api/orders/" + orderId + "/pay", s, Map.of("outcome", outcome)));
    }

    private int cartSize(MockHttpSession s) throws Exception {
        return body(call("GET", "/api/cart", s, null)).size();
    }

    // ---------------- UPI ----------------

    @Test
    @DisplayName("UPI success: order becomes PLACED and PAID")
    void upiSuccess() throws Exception {
        MockHttpSession s = newCustomer();
        Product p = newProduct("100.00", 10);
        JsonNode order = placeOrder(s, p, 2, "UPI", null);
        assertEquals("PAYMENT_PENDING", order.get("status").asText());
        assertEquals(8, stockOf(p), "stock is held while waiting for payment");

        JsonNode paid = pay(s, order.get("id").asLong(), "SUCCESS");
        assertEquals("PLACED", paid.get("status").asText());
        assertEquals("PAID", paid.get("paymentStatus").asText());
    }

    @Test
    @DisplayName("UPI failure: stock is released and the items go back to the cart")
    void upiFailure() throws Exception {
        MockHttpSession s = newCustomer();
        Product p = newProduct("100.00", 10);
        JsonNode order = placeOrder(s, p, 3, "UPI", null);
        assertEquals(0, cartSize(s));

        JsonNode failed = pay(s, order.get("id").asLong(), "FAILED");
        assertEquals("PAYMENT_FAILED", failed.get("status").asText());
        assertEquals(10, stockOf(p));
        assertEquals(1, cartSize(s), "items restored to cart so the customer can retry");
    }

    @Test
    @DisplayName("UPI timeout, then money arrives late: order expires and the money is REFUNDED")
    void upiTimeoutThenLatePayment() throws Exception {
        MockHttpSession s = newCustomer();
        Product p = newProduct("100.00", 10);
        long id = placeOrder(s, p, 1, "UPI", null).get("id").asLong();

        Order order = orderRepository.findById(id).orElseThrow();
        order.setCreatedAt(LocalDateTime.now().minusMinutes(11)); // pretend 11 minutes passed
        orderRepository.save(order);
        orderService.expireUnpaidOrders();

        JsonNode expired = body(call("GET", "/api/orders/" + id, s, null));
        assertEquals("PAYMENT_FAILED", expired.get("status").asText());
        assertEquals(10, stockOf(p));

        JsonNode late = pay(s, id, "SUCCESS"); // "money deducted but order failed"
        assertEquals("REFUNDED", late.get("paymentStatus").asText());
        assertEquals("PAYMENT_FAILED", late.get("status").asText());
    }

    // ---------------- Cancel ----------------

    @Test
    @DisplayName("Cancel a placed COD order: stock comes back, nothing charged")
    void cancelPlacedOrder() throws Exception {
        MockHttpSession s = newCustomer();
        Product p = newProduct("80.00", 5);
        long id = placeOrder(s, p, 2, "COD", null).get("id").asLong();
        assertEquals(3, stockOf(p));

        JsonNode cancelled = body(call("POST", "/api/orders/" + id + "/cancel", s, null));
        assertEquals("CANCELLED", cancelled.get("status").asText());
        assertEquals("NOT_CHARGED", cancelled.get("paymentStatus").asText());
        assertEquals(5, stockOf(p));
        assertEquals(409, status(call("POST", "/api/orders/" + id + "/cancel", s, null)), "cannot cancel twice");
    }

    @Test
    @DisplayName("Cancel after packing is refused; cancelling a PAID order refunds it")
    void cancelRules() throws Exception {
        MockHttpSession admin = adminSession();
        MockHttpSession s = newCustomer();
        Product p = newProduct("80.00", 10);

        long packed = placeOrder(s, p, 1, "COD", null).get("id").asLong();
        setStatus(admin, packed, "PACKED");
        assertEquals(409, status(call("POST", "/api/orders/" + packed + "/cancel", s, null)));

        long upi = placeOrder(s, p, 1, "UPI", null).get("id").asLong();
        pay(s, upi, "SUCCESS");
        JsonNode refunded = body(call("POST", "/api/orders/" + upi + "/cancel", s, null));
        assertEquals("REFUNDED", refunded.get("paymentStatus").asText());
    }

    // ---------------- Report missing item ----------------

    private void deliver(MockHttpSession admin, long id) throws Exception {
        setStatus(admin, id, "PACKED");
        setStatus(admin, id, "OUT_FOR_DELIVERY");
        assertEquals(200, status(setStatus(admin, id, "DELIVERED")));
    }

    @Test
    @DisplayName("Report missing items: only after delivery, never more than ordered")
    void reportMissingItem() throws Exception {
        MockHttpSession admin = adminSession();
        MockHttpSession s = newCustomer();
        Product p = newProduct("60.00", 10);
        JsonNode order = placeOrder(s, p, 2, "COD", null);
        long id = order.get("id").asLong();
        long itemId = order.get("items").get(0).get("id").asLong();
        String url = "/api/orders/" + id + "/issues";

        assertEquals(409, status(call("POST", url, s, Map.of("orderItemId", itemId, "quantity", 1, "reason", "MISSING"))),
                "not delivered yet");

        deliver(admin, id);
        MvcResult first = call("POST", url, s, Map.of("orderItemId", itemId, "quantity", 1, "reason", "MISSING"));
        assertEquals(200, status(first));
        assertEquals(0, new java.math.BigDecimal("60.00").compareTo(body(first).get("issues").get(0).get("refundAmount").decimalValue()));

        assertEquals(400, status(call("POST", url, s, Map.of("orderItemId", itemId, "quantity", 2, "reason", "DAMAGED"))),
                "only 1 left to report");
        assertEquals(200, status(call("POST", url, s, Map.of("orderItemId", itemId, "quantity", 1, "reason", "DAMAGED"))));
        assertEquals(400, status(call("POST", url, s, Map.of("orderItemId", itemId, "quantity", 1, "reason", "MISSING"))),
                "everything already reported");
        assertEquals(400, status(call("POST", url, s, Map.of("orderItemId", 999999, "quantity", 1, "reason", "MISSING"))),
                "item not in this order");
    }

    @Test
    @DisplayName("Refund for a missing item takes the coupon discount into account")
    void refundWithCoupon() throws Exception {
        MockHttpSession admin = adminSession();
        MockHttpSession s = newCustomer();
        JsonNode order = placeOrder(s, newProduct("300.00", 10), 1, "COD", "FRESH50");
        long id = order.get("id").asLong();
        deliver(admin, id);
        JsonNode updated = body(call("POST", "/api/orders/" + id + "/issues", s,
                Map.of("orderItemId", order.get("items").get(0).get("id").asLong(), "quantity", 1, "reason", "MISSING")));
        // Paid 250 for a 300 item (50 coupon), so the refund is 250, not 300
        assertEquals(0, new java.math.BigDecimal("250.00").compareTo(updated.get("issues").get(0).get("refundAmount").decimalValue()));
    }

    @Test
    @DisplayName("Reports after the 48-hour window are refused")
    void reportWindowClosed() throws Exception {
        MockHttpSession admin = adminSession();
        MockHttpSession s = newCustomer();
        JsonNode order = placeOrder(s, newProduct("60.00", 10), 1, "COD", null);
        long id = order.get("id").asLong();
        deliver(admin, id);

        Order o = orderRepository.findById(id).orElseThrow();
        o.setDeliveredAt(LocalDateTime.now().minusHours(49));
        orderRepository.save(o);

        assertEquals(409, status(call("POST", "/api/orders/" + id + "/issues", s,
                Map.of("orderItemId", order.get("items").get(0).get("id").asLong(), "quantity", 1, "reason", "MISSING"))));
    }

    // ---------------- Expiry ----------------

    @Test
    @DisplayName("Expiry boundary: expires today = cannot buy, expires tomorrow = can buy")
    void expiryBoundary() throws Exception {
        MockHttpSession s = newCustomer();
        Product today = newProduct("30.00", 10, 0, LocalDate.now());
        Product tomorrow = newProduct("30.00", 10, 0, LocalDate.now().plusDays(1));
        assertEquals(400, status(addToCart(s, today, 1)));
        assertEquals(200, status(addToCart(s, tomorrow, 1)));
    }

    @Test
    @DisplayName("Item that expires while sitting in the cart blocks checkout")
    void expiresWhileInCart() throws Exception {
        MockHttpSession s = newCustomer();
        Product milk = newProduct("32.00", 10, 0, LocalDate.now().plusDays(5));
        addToCart(s, milk, 1);
        Map<String, Object> body = checkoutBody(s, "COD", null);

        milk.setExpiryDate(LocalDate.now().minusDays(1));
        productRepository.save(milk);

        assertFalse(quote(s, null).get("problems").isEmpty());
        MvcResult r = checkout(s, body);
        assertEquals(400, status(r));
        assertTrue(body(r).get("message").asText().contains("expired"));
    }
}
