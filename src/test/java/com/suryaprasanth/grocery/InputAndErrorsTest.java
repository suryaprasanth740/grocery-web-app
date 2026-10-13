package com.suryaprasanth.grocery;

import com.fasterxml.jackson.databind.JsonNode;
import com.suryaprasanth.grocery.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Phase 8 gaps from the QA list: bad sign-up input, the cart's update/remove flow, missing products,
 * and that errors never leak internal details (stack traces, SQL, exception names).
 */
class InputAndErrorsTest extends ApiTestSupport {

    /** A test-only endpoint that fails the way a real bug would, with secrets in the exception message. */
    @TestConfiguration
    static class BrokenEndpoint {
        @Bean
        BoomController boomController() {
            return new BoomController();
        }
    }

    @RestController
    static class BoomController {
        @GetMapping("/api/test-only/boom")
        public String boom() {
            throw new IllegalStateException("could not connect to jdbc:postgresql://db.internal:5432 password=hunter2");
        }
    }

    private Map<String, Object> signUp(String name, String email, String password) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("email", email);
        m.put("password", password);
        return m;
    }

    private void assertNoLeak(String responseBody) {
        assertThat(responseBody)
                .doesNotContain("hunter2")
                .doesNotContain("jdbc:")
                .doesNotContain("Exception")
                .doesNotContain("at com.")
                .doesNotContain("Jackson")
                .doesNotContain("trace");
    }

    @Test
    @DisplayName("Sign-up with bad input is refused with 400 and the field's message; no account is created")
    void badSignUp() throws Exception {
        String email = "bad-signup-" + System.nanoTime() + "@test.local";
        Object[][] cases = {
                {signUp("", email, "secret123"), "Name is required"},
                {signUp("Asha", "not-an-email", "secret123"), "Enter a valid email"},
                {signUp("Asha", email, "12345"), "Password must be 6 to 72 characters"},
                {signUp("Asha", "", "secret123"), "Email is required"},
                {signUp("A".repeat(101), email, "secret123"), "Name must be 100 characters or less"},
        };
        for (Object[] c : cases) {
            MvcResult r = call("POST", "/api/auth/register", new MockHttpSession(), c[0]);
            assertThat(status(r)).as(String.valueOf(c[1])).isEqualTo(400);
            assertThat(body(r).get("message").asText()).isEqualTo(c[1]);
        }
        assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    @DisplayName("Login with empty fields is a clean 400, not a server error")
    void emptyLogin() throws Exception {
        MvcResult r = call("POST", "/api/auth/login", new MockHttpSession(), Map.of("email", "", "password", ""));
        assertThat(status(r)).isEqualTo(400);
    }

    @Test
    @DisplayName("Cart: add, change the quantity, remove one item, clear - the cart matches after each step")
    void cartFlow() throws Exception {
        Product a = newProduct("40.00", 10);
        Product b = newProduct("25.00", 10);
        MockHttpSession buyer = newCustomer();
        addToCart(buyer, a, 2);
        JsonNode cart = body(addToCart(buyer, b, 1));
        assertThat(cart.size()).isEqualTo(2);

        cart = body(call("PUT", "/api/cart/update", buyer, Map.of("productId", a.getId(), "quantity", 5)));
        assertThat(quantityOf(cart, a)).isEqualTo(5);

        cart = body(call("DELETE", "/api/cart/remove/" + b.getId(), buyer, null));
        assertThat(cart.size()).isEqualTo(1);
        assertThat(quantityOf(cart, a)).isEqualTo(5);

        assertThat(status(call("DELETE", "/api/cart/remove/" + b.getId(), buyer, null))).isEqualTo(404);
        assertThat(body(call("DELETE", "/api/cart/clear", buyer, null)).size()).isZero();
        assertThat(body(call("GET", "/api/cart", buyer, null)).size()).isZero();
    }

    @Test
    @DisplayName("Cart: raising a quantity above the stock is refused (400); lowering it is always allowed")
    void cartUpdateLimits() throws Exception {
        Product a = newProduct("40.00", 3);
        MockHttpSession buyer = newCustomer();
        addToCart(buyer, a, 3);
        assertThat(status(call("PUT", "/api/cart/update", buyer, Map.of("productId", a.getId(), "quantity", 4)))).isEqualTo(400);
        a.setStock(1);
        productRepository.save(a);
        assertThat(status(call("PUT", "/api/cart/update", buyer, Map.of("productId", a.getId(), "quantity", 1)))).isEqualTo(200);
        // Updating a product that isn't in the cart
        assertThat(status(call("PUT", "/api/cart/update", buyer, Map.of("productId", newProduct("9.00", 5).getId(), "quantity", 1))))
                .isEqualTo(404);
    }

    @Test
    @DisplayName("Out of stock (0 left): adding to the cart is refused with 400")
    void outOfStock() throws Exception {
        Product a = newProduct("40.00", 0);
        assertThat(status(addToCart(newCustomer(), a, 1))).isEqualTo(400);
    }

    @Test
    @DisplayName("Missing product: 404 for the page and for adding it to a cart")
    void missingProduct() throws Exception {
        assertThat(status(call("GET", "/api/products/99999999", null, null))).isEqualTo(404);
        assertThat(status(call("POST", "/api/cart/add", newCustomer(), Map.of("productId", 99999999, "quantity", 1)))).isEqualTo(404);
    }

    @Test
    @DisplayName("A wrong value in the address (/api/products/abc) is 400, and a wrong HTTP method is 405")
    void wrongPathValueAndMethod() throws Exception {
        assertThat(status(call("GET", "/api/products/abc", null, null))).isEqualTo(400);
        assertThat(status(call("DELETE", "/api/products", null, null))).isEqualTo(405);
    }

    @Test
    @DisplayName("Broken JSON is a 400 with a plain message that doesn't mention the JSON library")
    void brokenJson() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/register")
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Asha\", \"email\": ")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(r).get("message").asText()).isEqualTo("Some of the data sent is in the wrong format.");
        assertNoLeak(r.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("An unexpected server error is a 500 with a generic message: no exception name, SQL address or password")
    void serverErrorDoesNotLeak() throws Exception {
        MvcResult r = call("GET", "/api/test-only/boom", null, null);
        assertThat(status(r)).isEqualTo(500);
        assertThat(body(r).get("message").asText()).isEqualTo("Something went wrong. Please try again.");
        assertNoLeak(r.getResponse().getContentAsString());
    }

    private static int quantityOf(JsonNode cart, Product p) {
        for (JsonNode item : cart) {
            if (item.get("product").get("id").asLong() == p.getId()) return item.get("quantity").asInt();
        }
        throw new AssertionError("product " + p.getId() + " not in cart");
    }
}
