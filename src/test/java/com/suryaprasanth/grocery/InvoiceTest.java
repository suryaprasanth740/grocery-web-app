package com.suryaprasanth.grocery;

import com.fasterxml.jackson.databind.JsonNode;
import com.suryaprasanth.grocery.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The GST invoice of a delivered order: the maths, and who may see it. */
class InvoiceTest extends ApiTestSupport {

    private JsonNode line(JsonNode invoice, Product p) {
        for (JsonNode l : invoice.get("lines")) {
            if (l.get("name").asText().equals(p.getName())) return l;
        }
        throw new AssertionError("no invoice line for " + p.getName());
    }

    private static BigDecimal dec(JsonNode n) {
        return n.decimalValue();
    }

    @Test
    @DisplayName("Not delivered yet: the invoice is refused with 409")
    void onlyWhenDelivered() throws Exception {
        MockHttpSession buyer = newCustomer();
        long orderId = placeOrder(buyer, newProduct("105.00", 10, 5, LocalDate.now().plusDays(30)), 1, "COD", null)
                .get("id").asLong();
        assertThat(status(call("GET", "/api/orders/" + orderId + "/invoice", buyer, null))).isEqualTo(409);
    }

    @Test
    @DisplayName("GST is taken out of the price: 2 x Rs 105 at 5% = taxable Rs 200 + GST Rs 10 (CGST 5 + SGST 5)")
    void gstSplit() throws Exception {
        Product p = newProduct("105.00", 10, 5, LocalDate.now().plusDays(30));
        MockHttpSession buyer = newCustomer();
        JsonNode order = placeOrder(buyer, p, 2, "COD", null);
        long orderId = order.get("id").asLong();
        deliverOrder(adminSession(), orderId);

        JsonNode inv = body(call("GET", "/api/orders/" + orderId + "/invoice", buyer, null));
        JsonNode l = line(inv, p);
        assertThat(dec(l.get("amount"))).isEqualByComparingTo("210.00");
        assertThat(dec(l.get("taxableValue"))).isEqualByComparingTo("200.00");
        assertThat(dec(l.get("gst"))).isEqualByComparingTo("10.00");
        JsonNode t = inv.get("totals");
        assertThat(dec(t.get("cgst"))).isEqualByComparingTo("5.00");
        assertThat(dec(t.get("sgst"))).isEqualByComparingTo("5.00");
        assertThat(dec(t.get("grandTotal"))).isEqualByComparingTo(order.get("totalAmount").decimalValue());
        assertThat(inv.get("invoiceNumber").asText()).endsWith("-" + orderId);
    }

    @Test
    @DisplayName("A coupon is shared across lines by value: Rs 50 off Rs 300 + Rs 100 = Rs 37.50 + Rs 12.50, and GST is on what was paid")
    void couponSharedAcrossLines() throws Exception {
        Product rice = newProduct("300.00", 10, 5, LocalDate.now().plusDays(30));
        Product soap = newProduct("100.00", 10, 18, LocalDate.now().plusDays(30));
        MockHttpSession buyer = newCustomer();
        addToCart(buyer, rice, 1);
        addToCart(buyer, soap, 1);
        MvcResult r = checkout(buyer, checkoutBody(buyer, "COD", "FRESH50"));
        assertThat(status(r)).as(r.getResponse().getContentAsString()).isEqualTo(200);
        long orderId = body(r).get("orders").get(0).get("id").asLong();
        deliverOrder(adminSession(), orderId);

        JsonNode inv = body(call("GET", "/api/orders/" + orderId + "/invoice", buyer, null));
        JsonNode a = line(inv, rice);
        JsonNode b = line(inv, soap);
        assertThat(dec(a.get("discount")).add(dec(b.get("discount")))).isEqualByComparingTo("50.00");
        assertThat(dec(a.get("discount"))).isEqualByComparingTo("37.50");
        // Rs 262.50 paid at 5%: taxable 250.00 + GST 12.50
        assertThat(dec(a.get("taxableValue"))).isEqualByComparingTo("250.00");
        assertThat(dec(a.get("gst"))).isEqualByComparingTo("12.50");
        // Rs 87.50 paid at 18%: taxable 74.15 + GST 13.35
        assertThat(dec(b.get("taxableValue"))).isEqualByComparingTo("74.15");
        assertThat(dec(b.get("gst"))).isEqualByComparingTo("13.35");
        // Every rupee is accounted for: taxable + GST = items total - discount
        JsonNode t = inv.get("totals");
        BigDecimal taxable = dec(a.get("taxableValue")).add(dec(b.get("taxableValue")));
        assertThat(taxable.add(dec(t.get("gstIncluded")))).isEqualByComparingTo("350.00");
        assertThat(dec(t.get("cgst")).add(dec(t.get("sgst")))).isEqualByComparingTo(dec(t.get("gstIncluded")));
        assertThat(inv.get("gstByRate").size()).isEqualTo(2);
    }

    @Test
    @DisplayName("Shop without a GSTIN prints a bill of supply; with a GSTIN, a tax invoice showing it")
    void titleFollowsGstin() throws Exception {
        SellerAccount seller = newApprovedSeller("560073", "");
        Product p = productRepository.findById(sellerAddsProduct(seller, "Invoice Item", "50.00", 10)).orElseThrow();
        MockHttpSession buyer = newCustomer();
        long first = placeOrder(buyer, p, 1, "COD", null).get("id").asLong();
        sellerDelivers(seller, first);
        assertThat(body(call("GET", "/api/orders/" + first + "/invoice", buyer, null)).get("title").asText())
                .isEqualTo("Bill of supply");

        enableSlots(seller, 10, "29abcde1234f1z5"); // also saves the GSTIN, in capitals
        long second = placeOrder(buyer, p, 1, "COD", null).get("id").asLong();
        sellerDelivers(seller, second);
        JsonNode inv = body(call("GET", "/api/orders/" + second + "/invoice", buyer, null));
        assertThat(inv.get("title").asText()).isEqualTo("Tax invoice");
        assertThat(inv.get("seller").get("gstin").asText()).isEqualTo("29ABCDE1234F1Z5");
    }

    @Test
    @DisplayName("Who can see it: the buyer, the selling shop and admins; another buyer or shop gets 404")
    void access() throws Exception {
        SellerAccount seller = newApprovedSeller("560073", "");
        Product p = productRepository.findById(sellerAddsProduct(seller, "Invoice Item", "50.00", 10)).orElseThrow();
        MockHttpSession buyer = newCustomer();
        long orderId = placeOrder(buyer, p, 1, "COD", null).get("id").asLong();
        sellerDelivers(seller, orderId);

        assertThat(status(call("GET", "/api/orders/" + orderId + "/invoice", buyer, null))).isEqualTo(200);
        assertThat(status(call("GET", "/api/seller/orders/" + orderId + "/invoice", seller.session(), null))).isEqualTo(200);
        assertThat(status(call("GET", "/api/admin/orders/" + orderId + "/invoice", adminSession(), null))).isEqualTo(200);

        assertThat(status(call("GET", "/api/orders/" + orderId + "/invoice", newCustomer(), null))).isEqualTo(404);
        SellerAccount other = newApprovedSeller("560073", "");
        assertThat(status(call("GET", "/api/seller/orders/" + orderId + "/invoice", other.session(), null))).isEqualTo(404);
        assertThat(status(call("GET", "/api/admin/orders/" + orderId + "/invoice", buyer, null))).isEqualTo(403);
    }

    @Test
    @DisplayName("GSTIN format is checked when the shop saves it: a wrong one is refused with 400")
    void badGstin() throws Exception {
        SellerAccount seller = newApprovedSeller("560073", "");
        Map<String, Object> f = shopDetails("560073", "");
        f.put("gstin", "29ABCDE1234");
        assertThat(status(call("PUT", "/api/seller/shop", seller.session(), f))).isEqualTo(400);
    }

    private void sellerDelivers(SellerAccount seller, long orderId) throws Exception {
        for (String s : new String[]{"ACCEPTED", "PREPARING", "OUT_FOR_DELIVERY", "DELIVERED"}) {
            MvcResult r = call("PUT", "/api/seller/orders/" + orderId + "/status", seller.session(), Map.of("status", s));
            assertThat(status(r)).as(s + ": " + r.getResponse().getContentAsString()).isEqualTo(200);
        }
    }
}
