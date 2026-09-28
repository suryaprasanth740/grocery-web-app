package com.suryaprasanth.grocery;

import com.fasterxml.jackson.databind.JsonNode;
import com.suryaprasanth.grocery.model.Coupon;
import com.suryaprasanth.grocery.model.CouponType;
import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.service.CouponService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 2: PIN code check, bill breakdown, coupons, COD limit. */
class CheckoutFeaturesTest extends ApiTestSupport {

    @Autowired
    private CouponService couponService;

    private static BigDecimal money(String v) {
        return new BigDecimal(v);
    }

    private static void assertMoney(String expected, JsonNode value) {
        assertEquals(0, money(expected).compareTo(value.decimalValue()), "expected " + expected + " but was " + value);
    }

    // ---------------- PIN code ----------------

    @Test
    @DisplayName("PIN code check: serviceable, not serviceable, and invalid formats")
    void pincodeCheck() throws Exception {
        assertTrue(body(call("GET", "/api/delivery/check?pincode=560073", null, null)).get("serviceable").asBoolean());

        JsonNode delhi = body(call("GET", "/api/delivery/check?pincode=110001", null, null));
        assertTrue(delhi.get("valid").asBoolean());
        assertFalse(delhi.get("serviceable").asBoolean());

        for (String bad : new String[]{"012345", "56007", "5600731", "56OO73", ""}) {
            JsonNode r = body(call("GET", "/api/delivery/check?pincode=" + bad, null, null));
            assertFalse(r.get("valid").asBoolean(), "should be invalid: '" + bad + "'");
        }
    }

    @Test
    @DisplayName("Checkout to a PIN code we don't serve is refused")
    void checkoutToUnservedPincode() throws Exception {
        Product product = newProduct("100.00", 10);
        MockHttpSession s = newCustomer();
        addToCart(s, product, 1);
        Map<String, Object> body = checkoutBody(s, "COD", null);
        body.put("pincode", "110001");
        MvcResult r = checkout(s, body);
        assertEquals(400, status(r));
        assertTrue(body(r).get("message").asText().contains("don't deliver"));
    }

    // ---------------- Bill ----------------

    @Test
    @DisplayName("Delivery fee boundary: Rs 499 pays Rs 30 delivery, Rs 500 gets free delivery")
    void deliveryFeeBoundary() throws Exception {
        MockHttpSession s1 = newCustomer();
        addToCart(s1, newProduct("499.00", 10), 1);
        JsonNode b1 = quote(s1, null);
        assertMoney("30.00", b1.get("deliveryFee"));
        assertMoney("529.00", b1.get("total"));
        assertMoney("1.00", b1.get("addForFreeDelivery"));

        MockHttpSession s2 = newCustomer();
        addToCart(s2, newProduct("500.00", 10), 1);
        JsonNode b2 = quote(s2, null);
        assertMoney("0.00", b2.get("deliveryFee"));
        assertMoney("500.00", b2.get("total"));
    }

    @Test
    @DisplayName("Saved order has the same bill the customer saw (cart page and order match)")
    void orderMatchesQuote() throws Exception {
        MockHttpSession s = newCustomer();
        Product product = newProduct("120.00", 10);
        addToCart(s, product, 2);
        JsonNode bill = quote(s, null);
        MvcResult r = checkout(s, checkoutBody(s, "COD", null));
        JsonNode order = body(r);
        assertMoney(bill.get("total").asText(), order.get("totalAmount"));
        assertMoney("270.00", order.get("totalAmount")); // 240 + 30 delivery
        assertMoney("30.00", order.get("deliveryFee"));
    }

    @Test
    @DisplayName("GST already inside the price is shown: Rs 105 at 5% GST includes Rs 5")
    void gstIncluded() throws Exception {
        MockHttpSession s = newCustomer();
        addToCart(s, newProduct("105.00", 10, 5, LocalDate.now().plusDays(30)), 1);
        assertMoney("5.00", quote(s, null).get("gstIncluded"));
    }

    // ---------------- Coupons ----------------

    @Test
    @DisplayName("Coupon minimum order boundary: Rs 298 rejected, Rs 299 accepted (FRESH50)")
    void couponMinimumBoundary() throws Exception {
        MockHttpSession low = newCustomer();
        addToCart(low, newProduct("298.00", 10), 1);
        JsonNode rejected = quote(low, "FRESH50");
        assertFalse(rejected.get("couponApplied").asBoolean());
        assertTrue(rejected.get("couponMessage").asText().contains("more"));
        assertEquals(400, status(checkout(low, checkoutBody(low, "COD", "FRESH50"))));

        MockHttpSession ok = newCustomer();
        addToCart(ok, newProduct("299.00", 10), 1);
        JsonNode applied = quote(ok, "fresh50"); // lower case works too
        assertTrue(applied.get("couponApplied").asBoolean());
        assertMoney("50.00", applied.get("discount"));
        assertMoney("279.00", applied.get("total")); // 299 - 50 + 30 delivery
    }

    @Test
    @DisplayName("Expired and unknown coupons are rejected")
    void expiredAndUnknownCoupons() throws Exception {
        MockHttpSession s = newCustomer();
        addToCart(s, newProduct("400.00", 10), 1);
        assertTrue(quote(s, "OLD20").get("couponMessage").asText().contains("expired"));
        assertTrue(quote(s, "NOPE123").get("couponMessage").asText().contains("does not exist"));
        assertFalse(quote(s, "OLD20").get("couponApplied").asBoolean());
    }

    @Test
    @DisplayName("Percent coupon is capped: 10% of Rs 2000 gives only Rs 100 (SAVE10 cap)")
    void percentCouponCap() throws Exception {
        MockHttpSession s = newCustomer();
        addToCart(s, newProduct("1000.00", 10), 2);
        assertMoney("100.00", quote(s, "SAVE10").get("discount"));
    }

    @Test
    @DisplayName("A flat coupon can never make the bill negative")
    void discountNeverMoreThanItems() {
        Coupon big = new Coupon("TEST", "test", CouponType.FLAT, money("100"), null, BigDecimal.ZERO, null, 1);
        assertEquals(0, money("60.00").compareTo(couponService.discountFor(big, money("60.00"))));
    }

    @Test
    @DisplayName("Coupon used once cannot be reused, but works again after that order is cancelled")
    void couponReuse() throws Exception {
        MockHttpSession s = newCustomer();
        Product product = newProduct("300.00", 20);
        JsonNode first = placeOrder(s, product, 1, "COD", "FRESH50");
        assertMoney("50.00", first.get("discount"));

        addToCart(s, product, 1);
        JsonNode again = quote(s, "FRESH50");
        assertFalse(again.get("couponApplied").asBoolean());
        assertTrue(again.get("couponMessage").asText().contains("already used"));

        call("POST", "/api/orders/" + first.get("id").asLong() + "/cancel", s, null);
        assertTrue(quote(s, "FRESH50").get("couponApplied").asBoolean());
    }

    // ---------------- COD limit ----------------

    @Test
    @DisplayName("COD limit boundary: Rs 3000 allowed, Rs 3000.01 must use UPI")
    void codLimit() throws Exception {
        MockHttpSession ok = newCustomer();
        addToCart(ok, newProduct("3000.00", 10), 1);
        assertEquals(200, status(checkout(ok, checkoutBody(ok, "COD", null))));

        MockHttpSession over = newCustomer();
        addToCart(over, newProduct("3000.01", 10), 1);
        MvcResult cod = checkout(over, checkoutBody(over, "COD", null));
        assertEquals(400, status(cod));
        assertTrue(body(cod).get("message").asText().contains("Cash on delivery"));
        assertEquals(200, status(checkout(over, checkoutBody(over, "UPI", null))));
    }
}
