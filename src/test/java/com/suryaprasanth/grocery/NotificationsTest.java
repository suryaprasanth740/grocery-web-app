package com.suryaprasanth.grocery;

import com.fasterxml.jackson.databind.JsonNode;
import com.suryaprasanth.grocery.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The notification bell: who is told what, and that people only see and change their own messages. */
class NotificationsTest extends ApiTestSupport {

    private JsonNode bell(MockHttpSession s) throws Exception {
        return body(call("GET", "/api/notifications", s, null));
    }

    @Test
    @DisplayName("Shop accepts the order: the buyer gets one unread message that links to the order")
    void buyerToldWhenAccepted() throws Exception {
        SellerAccount seller = newApprovedSeller("560073", "");
        Product p = productRepository.findById(sellerAddsProduct(seller, "Bell Item", "50.00", 10)).orElseThrow();
        MockHttpSession buyer = newCustomer();
        long orderId = placeOrder(buyer, p, 1, "COD", null).get("id").asLong();
        assertThat(bell(buyer).get("unread").asLong()).isZero();

        call("PUT", "/api/seller/orders/" + orderId + "/status", seller.session(), Map.of("status", "ACCEPTED"));
        JsonNode b = bell(buyer);
        assertThat(b.get("unread").asLong()).isEqualTo(1);
        assertThat(b.get("items").get(0).get("title").asText()).isEqualTo("Order #" + orderId + " accepted");
        assertThat(b.get("items").get(0).get("link").asText()).isEqualTo("/order.html?id=" + orderId);
    }

    @Test
    @DisplayName("The shop owner is told about a new cash order; a rejected order tells the buyer the reason")
    void sellerToldAboutNewOrder() throws Exception {
        SellerAccount seller = newApprovedSeller("560073", "");
        Product p = productRepository.findById(sellerAddsProduct(seller, "Bell Item", "50.00", 10)).orElseThrow();
        MockHttpSession buyer = newCustomer();
        long orderId = placeOrder(buyer, p, 2, "COD", null).get("id").asLong();

        JsonNode sellerBell = bell(seller.session());
        assertThat(sellerBell.get("items").get(0).get("title").asText()).isEqualTo("New order #" + orderId);

        call("POST", "/api/seller/orders/" + orderId + "/reject", seller.session(), Map.of("reason", "Shop closed for a family function"));
        JsonNode buyerBell = bell(buyer);
        assertThat(buyerBell.get("items").get(0).get("title").asText()).isEqualTo("Order #" + orderId + " was cancelled");
        assertThat(buyerBell.get("items").get(0).get("body").asText()).contains("Shop closed for a family function");
    }

    @Test
    @DisplayName("A seller is told when the admin approves the shop")
    void sellerToldWhenApproved() throws Exception {
        SellerAccount pending = newPendingSeller("560073", "");
        assertThat(bell(pending.session()).get("unread").asLong()).isZero();
        call("POST", "/api/admin/shops/" + pending.shopId() + "/approve", adminSession(), null);
        assertThat(bell(pending.session()).get("items").get(0).get("title").asText()).isEqualTo("Your shop is approved");
    }

    @Test
    @DisplayName("Mark one read, then all read; someone else's notification can't be marked (404)")
    void markRead() throws Exception {
        Product p = newProduct("40.00", 10);
        MockHttpSession buyer = newCustomer();
        long orderId = placeOrder(buyer, p, 1, "COD", null).get("id").asLong();
        deliverOrder(adminSession(), orderId); // 4 steps = 4 messages
        JsonNode b = bell(buyer);
        assertThat(b.get("unread").asLong()).isEqualTo(4);
        long first = b.get("items").get(0).get("id").asLong();

        assertThat(status(call("POST", "/api/notifications/" + first + "/read", newCustomer(), null))).isEqualTo(404);
        assertThat(bell(buyer).get("unread").asLong()).isEqualTo(4);

        JsonNode afterOne = body(call("POST", "/api/notifications/" + first + "/read", buyer, null));
        assertThat(afterOne.get("unread").asLong()).isEqualTo(3);
        JsonNode afterAll = body(call("POST", "/api/notifications/read-all", buyer, null));
        assertThat(afterAll.get("unread").asLong()).isZero();
    }

    @Test
    @DisplayName("Logged-out visitors get 401 from the bell")
    void needsLogin() throws Exception {
        assertThat(status(call("GET", "/api/notifications", null, null))).isEqualTo(401);
        assertThat(status(call("POST", "/api/notifications/read-all", null, null))).isEqualTo(401);
    }
}
