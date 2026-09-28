package com.suryaprasanth.grocery;

import com.fasterxml.jackson.databind.JsonNode;
import com.suryaprasanth.grocery.dto.CheckoutRequest;
import com.suryaprasanth.grocery.model.PaymentMethod;
import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 1: stock locking, duplicate orders, input limits, price safety. */
class CriticalFixesTest extends ApiTestSupport {

    private User userOf(MockHttpSession session) {
        return userRepository.findById((Long) session.getAttribute("userId")).orElseThrow();
    }

    private CheckoutRequest request(MockHttpSession s, String requestId) throws Exception {
        CheckoutRequest r = new CheckoutRequest();
        r.setRequestId(requestId);
        r.setShippingAddress(GOOD_ADDRESS);
        r.setPincode(GOOD_PINCODE);
        r.setPaymentMethod(PaymentMethod.COD);
        r.setExpectedTotal(quote(s, null).get("total").decimalValue());
        return r;
    }

    @Test
    @DisplayName("TC_01 Two customers buy the LAST item at the same moment: only one succeeds")
    void lastItemRace() throws Exception {
        Product product = newProduct("100.00", 1);
        MockHttpSession a = newCustomer();
        MockHttpSession b = newCustomer();
        addToCart(a, product, 1);
        addToCart(b, product, 1);
        CheckoutRequest reqA = request(a, UUID.randomUUID().toString());
        CheckoutRequest reqB = request(b, UUID.randomUUID().toString());
        User userA = userOf(a);
        User userB = userOf(b);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        results.add(pool.submit(() -> { start.await(); return tryPlace(userA, reqA); }));
        results.add(pool.submit(() -> { start.await(); return tryPlace(userB, reqB); }));
        start.countDown(); // both go at the same time
        int successes = 0;
        for (Future<Boolean> f : results) {
            if (f.get(30, TimeUnit.SECONDS)) successes++;
        }
        pool.shutdown();

        assertEquals(1, successes, "exactly one customer should get the last item");
        assertEquals(0, stockOf(product), "stock must be 0, never negative");
    }

    private boolean tryPlace(User user, CheckoutRequest req) {
        try {
            orderService.placeOrder(user, req);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Test
    @DisplayName("TC_02 Double click on Place Order (same request id) creates only ONE order")
    void doubleClickSameRequestId() throws Exception {
        Product product = newProduct("50.00", 10);
        MockHttpSession s = newCustomer();
        addToCart(s, product, 2);
        Map<String, Object> body = checkoutBody(s, "COD", null);

        MvcResult first = checkout(s, body);
        MvcResult second = checkout(s, body); // exactly the same request again

        assertEquals(200, status(first));
        assertEquals(200, status(second));
        assertEquals(body(first).get("id").asLong(), body(second).get("id").asLong(), "same order returned");
        assertEquals(1, orderRepository.findByUserIdOrderByCreatedAtDesc(userOf(s).getId()).size());
        assertEquals(8, stockOf(product), "stock taken only once");
    }

    @Test
    @DisplayName("TC_02b Two checkouts of the same cart at the same time (two tabs) create only ONE order")
    void twoTabsSameCart() throws Exception {
        Product product = newProduct("50.00", 10);
        MockHttpSession s = newCustomer();
        addToCart(s, product, 1);
        User user = userOf(s);
        CheckoutRequest tab1 = request(s, UUID.randomUUID().toString());
        CheckoutRequest tab2 = request(s, UUID.randomUUID().toString());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Boolean> f1 = pool.submit(() -> { start.await(); return tryPlace(user, tab1); });
        Future<Boolean> f2 = pool.submit(() -> { start.await(); return tryPlace(user, tab2); });
        start.countDown();
        int ok = (f1.get(30, TimeUnit.SECONDS) ? 1 : 0) + (f2.get(30, TimeUnit.SECONDS) ? 1 : 0);
        pool.shutdown();

        assertEquals(1, ok);
        assertEquals(1, orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).size());
        assertEquals(9, stockOf(product));
    }

    @Test
    @DisplayName("TC_03 Cannot add more than the stock to the cart")
    void cannotAddMoreThanStock() throws Exception {
        Product product = newProduct("10.00", 5);
        MockHttpSession s = newCustomer();
        MvcResult r = addToCart(s, product, 6);
        assertEquals(400, status(r));
        assertTrue(body(r).get("message").asText().contains("Only 5"));
        assertEquals(200, status(addToCart(s, product, 5)), "exactly the stock is fine (boundary)");
    }

    @Test
    @DisplayName("TC_04 Quantity boundary: 20 allowed, 21 rejected, 0 rejected")
    void quantityBoundaries() throws Exception {
        Product product = newProduct("10.00", 100);
        MockHttpSession s = newCustomer();
        assertEquals(400, status(addToCart(s, product, 21)));
        assertEquals(400, status(addToCart(s, product, 0)));
        assertEquals(400, status(addToCart(s, product, -1)));
        assertEquals(200, status(addToCart(s, product, 20)));
        assertEquals(400, status(addToCart(s, product, 1)), "20 already in cart, 21st must be refused");
    }

    @Test
    @DisplayName("TC_06 Address boundary: 300 characters OK, 301 rejected with 400 (not a 500 crash)")
    void addressLengthBoundary() throws Exception {
        Product product = newProduct("10.00", 100);
        MockHttpSession s = newCustomer();
        addToCart(s, product, 1);

        Map<String, Object> tooLong = checkoutBody(s, "COD", null);
        tooLong.put("shippingAddress", "A".repeat(301));
        MvcResult r = checkout(s, tooLong);
        assertEquals(400, status(r));

        Map<String, Object> tooShort = checkoutBody(s, "COD", null);
        tooShort.put("shippingAddress", "12 Road");
        assertEquals(400, status(checkout(s, tooShort)));

        Map<String, Object> exact = checkoutBody(s, "COD", null);
        exact.put("shippingAddress", "B".repeat(300));
        assertEquals(200, status(checkout(s, exact)));
    }

    @Test
    @DisplayName("TC_08 Price changed after adding to cart: customer is warned and old total is refused")
    void priceChangedAfterAdding() throws Exception {
        Product product = newProduct("40.00", 100);
        MockHttpSession s = newCustomer();
        addToCart(s, product, 1);
        Map<String, Object> oldBody = checkoutBody(s, "COD", null); // total with Rs 40

        product.setPrice(new BigDecimal("45.00"));
        productRepository.save(product);

        JsonNode bill = quote(s, null);
        assertEquals(1, bill.get("priceChangeNotes").size(), "warning shown");
        MvcResult r = checkout(s, oldBody);
        assertEquals(409, status(r), "order with the old total must not go through");
        assertEquals(0, orderRepository.findByUserIdOrderByCreatedAtDesc(userOf(s).getId()).size());
    }

    @Test
    @DisplayName("TC_09 Price tampering: a lower total sent by the browser is refused")
    void priceTampering() throws Exception {
        Product product = newProduct("200.00", 100);
        MockHttpSession s = newCustomer();
        addToCart(s, product, 1);
        Map<String, Object> body = checkoutBody(s, "COD", null);
        body.put("expectedTotal", new BigDecimal("1.00"));
        assertEquals(409, status(checkout(s, body)));
        assertEquals(100, stockOf(product), "nothing reserved");
    }

    @Test
    @DisplayName("Wrong payment method value gives a clean 400")
    void badEnumValue() throws Exception {
        Product product = newProduct("10.00", 100);
        MockHttpSession s = newCustomer();
        addToCart(s, product, 1);
        Map<String, Object> body = checkoutBody(s, "COD", null);
        body.put("paymentMethod", "BITCOIN");
        assertEquals(400, status(checkout(s, body)));
    }
}
