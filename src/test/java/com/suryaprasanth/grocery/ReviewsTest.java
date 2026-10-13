package com.suryaprasanth.grocery;

import com.fasterxml.jackson.databind.JsonNode;
import com.suryaprasanth.grocery.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Ratings and reviews: who may review, one review per buyer, averages, and admin moderation. */
class ReviewsTest extends ApiTestSupport {

    private String reviewUrl(Product p) {
        return "/api/products/" + p.getId() + "/reviews";
    }

    private Map<String, Object> review(Integer stars, String comment) {
        Map<String, Object> m = new HashMap<>();
        m.put("rating", stars);
        m.put("comment", comment);
        return m;
    }

    /** A buyer who has received the product (order delivered by the admin's demo shop). */
    private MockHttpSession buyerWhoReceived(Product p) throws Exception {
        MockHttpSession buyer = newCustomer();
        long orderId = placeOrder(buyer, p, 1, "COD", null).get("id").asLong();
        deliverOrder(adminSession(), orderId);
        return buyer;
    }

    @Test
    @DisplayName("Only a buyer whose order was delivered can review: before delivery 403, after delivery 201")
    void mustHaveReceivedIt() throws Exception {
        Product p = newProduct("100.00", 20);
        MockHttpSession buyer = newCustomer();
        long orderId = placeOrder(buyer, p, 1, "COD", null).get("id").asLong();

        assertThat(status(call("POST", reviewUrl(p), buyer, review(5, "Lovely")))).isEqualTo(403);
        assertThat(status(call("POST", reviewUrl(p), newCustomer(), review(5, "Never bought it")))).isEqualTo(403);

        deliverOrder(adminSession(), orderId);
        var r = call("POST", reviewUrl(p), buyer, review(5, "  Fresh and well packed  "));
        assertThat(status(r)).isEqualTo(201);
        assertThat(body(r).get("comment").asText()).isEqualTo("Fresh and well packed");

        JsonNode list = body(call("GET", reviewUrl(p), null, null));
        assertThat(list.get("ratingCount").asInt()).isEqualTo(1);
        assertThat(list.get("ratingAverage").asDouble()).isEqualTo(5.0);
        assertThat(list.get("reviews").get(0).get("verifiedPurchase").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Reviewing again updates the same review (200): the average uses only the new stars")
    void secondReviewUpdates() throws Exception {
        Product p = newProduct("80.00", 20);
        MockHttpSession a = buyerWhoReceived(p);
        MockHttpSession b = buyerWhoReceived(p);
        call("POST", reviewUrl(p), a, review(5, null));
        call("POST", reviewUrl(p), b, review(2, "Too ripe"));
        assertThat(body(call("GET", "/api/products/" + p.getId(), null, null)).get("ratingAverage").asDouble()).isEqualTo(3.5);

        var again = call("POST", reviewUrl(p), b, review(4, "Better this time"));
        assertThat(status(again)).isEqualTo(200);
        assertThat(body(again).get("edited").asBoolean()).isTrue();
        JsonNode product = body(call("GET", "/api/products/" + p.getId(), null, null));
        assertThat(product.get("ratingCount").asInt()).isEqualTo(2);       // still two reviews, not three
        assertThat(product.get("ratingAverage").asDouble()).isEqualTo(4.5); // (5 + 4) / 2
    }

    @Test
    @DisplayName("Stars must be 1-5 and comments at most 500 characters (400); sellers 403; logged out 401")
    void validation() throws Exception {
        Product p = newProduct("60.00", 20);
        MockHttpSession buyer = buyerWhoReceived(p);
        assertThat(status(call("POST", reviewUrl(p), buyer, review(0, null)))).isEqualTo(400);
        assertThat(status(call("POST", reviewUrl(p), buyer, review(6, null)))).isEqualTo(400);
        assertThat(status(call("POST", reviewUrl(p), buyer, review(null, "No stars")))).isEqualTo(400);
        assertThat(status(call("POST", reviewUrl(p), buyer, review(4, "x".repeat(501))))).isEqualTo(400);
        assertThat(status(call("POST", reviewUrl(p), buyer, review(4, "x".repeat(500))))).isEqualTo(201);

        assertThat(status(call("POST", reviewUrl(p), newApprovedSeller("560073", "").session(), review(5, null)))).isEqualTo(403);
        assertThat(status(call("POST", reviewUrl(p), null, review(5, null)))).isEqualTo(401);
        assertThat(status(call("POST", "/api/products/999999999/reviews", buyer, review(5, null)))).isEqualTo(404);
    }

    @Test
    @DisplayName("Reviews show only the buyer's first name (never the email); the shop average combines its products")
    void privacyAndShopAverage() throws Exception {
        SellerAccount seller = newApprovedSeller("560073", "");
        Product one = productRepository.findById(sellerAddsProduct(seller, "Review Item A", "40.00", 10)).orElseThrow();
        Product two = productRepository.findById(sellerAddsProduct(seller, "Review Item B", "40.00", 10)).orElseThrow();
        MockHttpSession buyer = newCustomer();
        addToCart(buyer, one, 1);
        addToCart(buyer, two, 1);
        long orderId = body(checkoutWith(buyer, "COD", Map.of(), Map.of())).get("orders").get(0).get("id").asLong();
        for (String s : new String[]{"ACCEPTED", "PREPARING", "OUT_FOR_DELIVERY", "DELIVERED"}) {
            call("PUT", "/api/seller/orders/" + orderId + "/status", seller.session(), Map.of("status", s));
        }
        call("POST", reviewUrl(one), buyer, review(5, null));
        call("POST", reviewUrl(two), buyer, review(3, null));

        JsonNode shop = body(call("GET", "/api/shops/" + seller.shopId() + "/reviews", null, null));
        assertThat(shop.get("ratingCount").asInt()).isEqualTo(2);
        assertThat(shop.get("ratingAverage").asDouble()).isEqualTo(4.0);
        String text = shop.toString();
        assertThat(shop.get("reviews").get(0).get("buyerName").asText()).isEqualTo("Test");
        assertThat(text).doesNotContain("@test.local");
    }

    @Test
    @DisplayName("Admin hides a review (reason required): it disappears and stops counting; hiding twice 409; showing restores it")
    void moderation() throws Exception {
        Product p = newProduct("50.00", 20);
        MockHttpSession buyer = buyerWhoReceived(p);
        long id = body(call("POST", reviewUrl(p), buyer, review(1, "Rude words here"))).get("id").asLong();
        MockHttpSession admin = adminSession();

        assertThat(status(call("PUT", "/api/admin/reviews/" + id + "/hide", buyer, Map.of("reason", "Abusive language")))).isEqualTo(403);
        assertThat(status(call("PUT", "/api/admin/reviews/" + id + "/hide", admin, Map.of("reason", " ")))).isEqualTo(400);
        assertThat(status(call("PUT", "/api/admin/reviews/" + id + "/hide", admin, Map.of("reason", "Abusive language")))).isEqualTo(200);
        JsonNode list = body(call("GET", reviewUrl(p), null, null));
        assertThat(list.get("reviews").size()).isZero();
        assertThat(list.get("ratingCount").asInt()).isZero();
        assertThat(list.get("ratingAverage").isNull()).isTrue();
        assertThat(status(call("PUT", "/api/admin/reviews/" + id + "/hide", admin, Map.of("reason", "Abusive language")))).isEqualTo(409);

        assertThat(status(call("PUT", "/api/admin/reviews/" + id + "/show", admin, null))).isEqualTo(200);
        assertThat(body(call("GET", reviewUrl(p), null, null)).get("ratingCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("A buyer's own reviews list shows what they rated, for the order page")
    void myReviews() throws Exception {
        Product p = newProduct("30.00", 20);
        MockHttpSession buyer = buyerWhoReceived(p);
        call("POST", reviewUrl(p), buyer, review(4, "Good"));
        JsonNode mine = body(call("GET", "/api/reviews/mine", buyer, null));
        assertThat(mine.size()).isEqualTo(1);
        assertThat(mine.get(0).get("productId").asLong()).isEqualTo(p.getId());
        assertThat(mine.get(0).get("rating").asInt()).isEqualTo(4);
        assertThat(status(call("GET", "/api/reviews/mine", null, null))).isEqualTo(401);
    }
}
