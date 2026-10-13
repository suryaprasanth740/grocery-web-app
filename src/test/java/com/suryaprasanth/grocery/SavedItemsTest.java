package com.suryaprasanth.grocery;

import com.fasterxml.jackson.databind.JsonNode;
import com.suryaprasanth.grocery.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

/** Saved for later: saving, the list, and moving products between the saved list and the cart. */
class SavedItemsTest extends ApiTestSupport {

    private String url(Product p) {
        return "/api/saved/" + p.getId();
    }

    private JsonNode savedList(MockHttpSession buyer) throws Exception {
        return body(call("GET", "/api/saved", buyer, null));
    }

    private JsonNode cart(MockHttpSession buyer) throws Exception {
        return body(call("GET", "/api/cart", buyer, null));
    }

    @Test
    @DisplayName("Saving is idempotent: first save 201, saving again 200, and the product is listed once")
    void saveTwice() throws Exception {
        Product p = newProduct("40.00", 10);
        MockHttpSession buyer = newCustomer();
        assertThat(status(call("POST", url(p), buyer, null))).isEqualTo(201);
        assertThat(status(call("POST", url(p), buyer, null))).isEqualTo(200);

        JsonNode list = savedList(buyer);
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).get("product").get("id").asLong()).isEqualTo(p.getId());
        assertThat(list.get(0).get("available").asBoolean()).isTrue();
        // Another buyer's list is separate
        assertThat(savedList(newCustomer()).size()).isZero();
    }

    @Test
    @DisplayName("A product that doesn't exist or is switched off can't be saved (404)")
    void cannotSaveMissingProducts() throws Exception {
        MockHttpSession buyer = newCustomer();
        assertThat(status(call("POST", "/api/saved/99999999", buyer, null))).isEqualTo(404);

        Product off = newProduct("40.00", 10);
        off.setActive(false);
        productRepository.save(off);
        assertThat(status(call("POST", url(off), buyer, null))).isEqualTo(404);
    }

    @Test
    @DisplayName("Move to cart: the item leaves the saved list and is in the cart with quantity 1")
    void moveToCart() throws Exception {
        Product p = newProduct("40.00", 10);
        MockHttpSession buyer = newCustomer();
        call("POST", url(p), buyer, null);

        JsonNode both = body(call("POST", url(p) + "/move-to-cart", buyer, null));
        assertThat(both.get("saved").size()).isZero();
        assertThat(both.get("cart").size()).isEqualTo(1);
        assertThat(both.get("cart").get(0).get("quantity").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("Out of stock: the list says why, moving to cart is refused (400) and the item STAYS saved")
    void outOfStockStaysSaved() throws Exception {
        Product p = newProduct("40.00", 10);
        MockHttpSession buyer = newCustomer();
        call("POST", url(p), buyer, null);
        p.setStock(0);
        productRepository.save(p);

        JsonNode list = savedList(buyer);
        assertThat(list.get(0).get("available").asBoolean()).isFalse();
        assertThat(list.get(0).get("reason").asText()).isEqualTo("Out of stock");

        assertThat(status(call("POST", url(p) + "/move-to-cart", buyer, null))).isEqualTo(400);
        assertThat(savedList(buyer).size()).isEqualTo(1);
        assertThat(cart(buyer).size()).isZero();
    }

    @Test
    @DisplayName("Save for later from the cart: removed from the cart, added to the saved list")
    void saveFromCart() throws Exception {
        Product p = newProduct("40.00", 10);
        MockHttpSession buyer = newCustomer();
        addToCart(buyer, p, 3);

        JsonNode both = body(call("POST", "/api/cart/save-for-later/" + p.getId(), buyer, null));
        assertThat(both.get("cart").size()).isZero();
        assertThat(both.get("saved").get(0).get("product").get("id").asLong()).isEqualTo(p.getId());
        // A product that isn't in the cart can't be moved from it
        assertThat(status(call("POST", "/api/cart/save-for-later/" + p.getId(), buyer, null))).isEqualTo(404);
    }

    @Test
    @DisplayName("Remove: 200 and gone; removing something not saved is 404")
    void remove() throws Exception {
        Product p = newProduct("40.00", 10);
        MockHttpSession buyer = newCustomer();
        call("POST", url(p), buyer, null);
        assertThat(status(call("DELETE", url(p), buyer, null))).isEqualTo(200);
        assertThat(savedList(buyer).size()).isZero();
        assertThat(status(call("DELETE", url(p), buyer, null))).isEqualTo(404);
    }

    @Test
    @DisplayName("Only buyers have a saved list: logged out 401, seller and admin 403")
    void buyersOnly() throws Exception {
        Product p = newProduct("40.00", 10);
        assertThat(status(call("GET", "/api/saved", null, null))).isEqualTo(401);
        assertThat(status(call("POST", url(p), null, null))).isEqualTo(401);
        assertThat(status(call("POST", url(p), newApprovedSeller("560073", "").session(), null))).isEqualTo(403);
        assertThat(status(call("GET", "/api/saved", adminSession(), null))).isEqualTo(403);
    }
}
