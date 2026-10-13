package com.suryaprasanth.grocery;

import com.fasterxml.jackson.databind.JsonNode;
import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.model.Shop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Delivery / pickup time slots through the API. Tests book TOMORROW's slots, which are always
 * open whatever time the tests run (the exact cut-off rule is tested in SlotRulesTest).
 */
class TimeSlotsTest extends ApiTestSupport {

    private static String tomorrowAt(String time) {
        return LocalDate.now(Shop.SHOP_ZONE).plusDays(1) + "T" + time;
    }

    private record Setup(SellerAccount seller, Product product) { }

    private Setup shopWithSlots(int capacity) throws Exception {
        SellerAccount seller = newApprovedSeller("560073", "");
        enableSlots(seller, capacity, null);
        Product product = productRepository.findById(sellerAddsProduct(seller, "Slot Item", "100.00", 20)).orElseThrow();
        return new Setup(seller, product);
    }

    private MvcResult buyWithSlot(Setup s, MockHttpSession buyer, String slot) throws Exception {
        addToCart(buyer, s.product(), 1);
        return checkoutWith(buyer, "COD", Map.of(), Map.of(s.seller().shopId(), slot));
    }

    @Test
    @DisplayName("Slots list: today and tomorrow, each slot with places left; a shop without slots says so")
    void availability() throws Exception {
        Setup s = shopWithSlots(3);
        JsonNode slots = body(call("GET", "/api/shops/" + s.seller().shopId() + "/slots", null, null));
        assertThat(slots.get("enabled").asBoolean()).isTrue();
        assertThat(slots.get("days").get(0).get("label").asText()).isEqualTo("Today");
        JsonNode tomorrow = slots.get("days").get(1);
        assertThat(tomorrow.get("slots").size()).isEqualTo(12); // 00:00-23:59 shop
        assertThat(tomorrow.get("slots").get(5).get("value").asText()).isEqualTo(tomorrowAt("10:00"));
        assertThat(tomorrow.get("slots").get(5).get("placesLeft").asInt()).isEqualTo(3);

        SellerAccount plain = newApprovedSeller("560073", "");
        JsonNode none = body(call("GET", "/api/shops/" + plain.shopId() + "/slots", null, null));
        assertThat(none.get("enabled").asBoolean()).isFalse();
        assertThat(none.get("days").size()).isZero();
    }

    @Test
    @DisplayName("Booking a slot saves it on the order and takes one place; no slot = 'as soon as possible'")
    void bookSlot() throws Exception {
        Setup s = shopWithSlots(3);
        MvcResult r = buyWithSlot(s, newCustomer(), tomorrowAt("10:00"));
        assertThat(status(r)).as(r.getResponse().getContentAsString()).isEqualTo(200);
        JsonNode order = body(r).get("orders").get(0);
        assertThat(order.get("slotDate").asText()).isEqualTo(LocalDate.now(Shop.SHOP_ZONE).plusDays(1).toString());
        assertThat(order.get("slotStart").asText()).startsWith("10:00");
        assertThat(order.get("slotEnd").asText()).startsWith("12:00");

        JsonNode tomorrow = body(call("GET", "/api/shops/" + s.seller().shopId() + "/slots", null, null)).get("days").get(1);
        assertThat(tomorrow.get("slots").get(5).get("placesLeft").asInt()).isEqualTo(2);

        MockHttpSession other = newCustomer();
        addToCart(other, s.product(), 1);
        JsonNode asap = body(checkoutWith(other, "COD", Map.of(), Map.of())).get("orders").get(0);
        assertThat(asap.get("slotDate").isNull()).isTrue();
    }

    @Test
    @DisplayName("A full slot is refused (409) and nothing is taken: no order, stock unchanged, cart kept; a cancel frees the place")
    void fullSlot() throws Exception {
        Setup s = shopWithSlots(1);
        MockHttpSession first = newCustomer();
        long firstOrder = body(buyWithSlot(s, first, tomorrowAt("14:00"))).get("orders").get(0).get("id").asLong();
        int stockAfterFirst = stockOf(s.product());

        MockHttpSession second = newCustomer();
        MvcResult refused = buyWithSlot(s, second, tomorrowAt("14:00"));
        assertThat(status(refused)).isEqualTo(409);
        assertThat(body(refused).get("message").asText()).contains("full");
        assertThat(stockOf(s.product())).isEqualTo(stockAfterFirst);
        assertThat(body(call("GET", "/api/cart", second, null)).size()).isEqualTo(1);

        call("POST", "/api/orders/" + firstOrder + "/cancel", first, null);
        MvcResult retry = checkoutWith(second, "COD", Map.of(), Map.of(s.seller().shopId(), tomorrowAt("14:00")));
        assertThat(status(retry)).as(retry.getResponse().getContentAsString()).isEqualTo(200);
    }

    @Test
    @DisplayName("Bad slot values: wrong format or off-boundary time 400, past or too far ahead 409, shop without slots 400")
    void badValues() throws Exception {
        Setup s = shopWithSlots(5);
        MockHttpSession buyer = newCustomer();
        addToCart(buyer, s.product(), 1);
        long shopId = s.seller().shopId();
        assertThat(status(checkoutWith(buyer, "COD", Map.of(), Map.of(shopId, "tomorrow evening")))).isEqualTo(400);
        assertThat(status(checkoutWith(buyer, "COD", Map.of(), Map.of(shopId, tomorrowAt("10:30"))))).isEqualTo(400);
        String yesterday = LocalDate.now(Shop.SHOP_ZONE).minusDays(1) + "T10:00";
        String later = LocalDate.now(Shop.SHOP_ZONE).plusDays(3) + "T10:00";
        assertThat(status(checkoutWith(buyer, "COD", Map.of(), Map.of(shopId, yesterday)))).isEqualTo(409);
        assertThat(status(checkoutWith(buyer, "COD", Map.of(), Map.of(shopId, later)))).isEqualTo(409);

        SellerAccount noSlots = newApprovedSeller("560073", "");
        Product plain = productRepository.findById(sellerAddsProduct(noSlots, "No Slot Item", "50.00", 5)).orElseThrow();
        MockHttpSession b2 = newCustomer();
        addToCart(b2, plain, 1);
        MvcResult r = checkoutWith(b2, "COD", Map.of(), Map.of(noSlots.shopId(), tomorrowAt("10:00")));
        assertThat(status(r)).isEqualTo(400);
        assertThat(body(r).get("message").asText()).contains("doesn't offer time slots");
    }

    @Test
    @DisplayName("A slot for a shop that isn't in the basket is refused (400)")
    void slotForOtherShop() throws Exception {
        Setup s = shopWithSlots(5);
        Setup other = shopWithSlots(5);
        MockHttpSession buyer = newCustomer();
        addToCart(buyer, s.product(), 1);
        MvcResult r = checkoutWith(buyer, "COD", Map.of(), Map.of(other.seller().shopId(), tomorrowAt("10:00")));
        assertThat(status(r)).isEqualTo(400);
        assertThat(body(r).get("message").asText()).contains("isn't in your basket");
    }

    @Test
    @DisplayName("Seller settings: slot capacity must be 1 to 50 (400 otherwise)")
    void capacityLimits() throws Exception {
        SellerAccount seller = newApprovedSeller("560073", "");
        for (int bad : new int[]{0, 51}) {
            Map<String, Object> f = shopDetails("560073", "");
            f.put("slotsEnabled", true);
            f.put("slotCapacity", bad);
            assertThat(status(call("PUT", "/api/seller/shop", seller.session(), f))).as("capacity " + bad).isEqualTo(400);
        }
    }

    @Test
    @DisplayName("Two buyers race for the LAST place in a slot at the same moment: exactly one gets it")
    void raceForLastPlace() throws Exception {
        Setup s = shopWithSlots(1);
        MockHttpSession a = newCustomer();
        MockHttpSession b = newCustomer();
        addToCart(a, s.product(), 1);
        addToCart(b, s.product(), 1);
        // Prepare both requests first, then fire them together.
        List<Map<String, Object>> bodies = new ArrayList<>();
        for (MockHttpSession buyer : List.of(a, b)) {
            Map<String, Object> body = checkoutBody(buyer, "COD", null);
            body.put("slots", Map.of(s.seller().shopId(), tomorrowAt("18:00")));
            bodies.add(body);
        }
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            List<MockHttpSession> buyers = List.of(a, b);
            for (int i = 0; i < 2; i++) {
                int n = i;
                Callable<Integer> task = () -> {
                    start.await();
                    return status(checkout(buyers.get(n), bodies.get(n)));
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> f : results) {
                codes.add(f.get());
            }
            assertThat(codes).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
    }
}
