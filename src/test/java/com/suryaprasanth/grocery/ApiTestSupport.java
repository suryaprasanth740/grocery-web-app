package com.suryaprasanth.grocery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suryaprasanth.grocery.model.Category;
import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.repository.CategoryRepository;
import com.suryaprasanth.grocery.repository.OrderRepository;
import com.suryaprasanth.grocery.repository.ProductRepository;
import com.suryaprasanth.grocery.repository.UserRepository;
import com.suryaprasanth.grocery.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Shared helpers for the API tests. Each test creates its OWN customers and products,
 * so tests never disturb each other.
 */
@SpringBootTest(properties = {
        "app.admin.email=admin@test.local",
        "app.admin.password=Admin@12345",
        "app.demo.refresh-expired-stock=false"
})
@AutoConfigureMockMvc
public abstract class ApiTestSupport {

    protected static final String ADMIN_EMAIL = "admin@test.local";
    protected static final String ADMIN_PASSWORD = "Admin@12345";
    protected static final String GOOD_PINCODE = "560073";
    protected static final String GOOD_ADDRESS = "12, 3rd Cross, Nagasandra, Bengaluru";

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired protected MockMvc mvc;
    @Autowired protected ProductRepository productRepository;
    @Autowired protected CategoryRepository categoryRepository;
    @Autowired protected OrderRepository orderRepository;
    @Autowired protected UserRepository userRepository;
    @Autowired protected OrderService orderService;

    protected final ObjectMapper json = new ObjectMapper();

    // ---------- HTTP helpers ----------

    protected MvcResult call(String method, String url, MockHttpSession session, Object body) throws Exception {
        var request = switch (method) {
            case "GET" -> get(url);
            case "PUT" -> put(url);
            case "DELETE" -> delete(url);
            default -> post(url);
        };
        request.contentType(MediaType.APPLICATION_JSON);
        if (session != null) {
            request.session(session);
        }
        if (body != null) {
            request.content(json.writeValueAsString(body));
        }
        return mvc.perform(request).andReturn();
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    // ---------- Test data ----------

    /** Registers a brand-new customer and returns their logged-in session. */
    protected MockHttpSession newCustomer() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String email = "user" + COUNTER.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        MvcResult r = call("POST", "/api/auth/register", session,
                Map.of("name", "Test User", "email", email, "password", "secret123"));
        if (status(r) != 201) {
            throw new IllegalStateException("register failed: " + r.getResponse().getContentAsString());
        }
        return session;
    }

    protected MockHttpSession adminSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult r = call("POST", "/api/auth/login", session, Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD));
        if (status(r) != 200) {
            throw new IllegalStateException("admin login failed: " + r.getResponse().getContentAsString());
        }
        return session;
    }

    protected Product newProduct(String price, int stock) {
        return newProduct(price, stock, 0, LocalDate.now().plusDays(30));
    }

    protected Product newProduct(String price, int stock, int gstPercent, LocalDate expiry) {
        Category category = categoryRepository.findAll().get(0);
        Product p = new Product("Test item " + COUNTER.incrementAndGet(), "for tests", new BigDecimal(price),
                "1 pc", "T", stock, category);
        p.setGstPercent(gstPercent);
        p.setExpiryDate(expiry);
        return productRepository.save(p);
    }

    protected int stockOf(Product p) {
        return productRepository.findById(p.getId()).orElseThrow().getStock();
    }

    protected MvcResult addToCart(MockHttpSession s, Product p, int qty) throws Exception {
        return call("POST", "/api/cart/add", s, Map.of("productId", p.getId(), "quantity", qty));
    }

    protected JsonNode quote(MockHttpSession s, String coupon) throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("couponCode", coupon);
        return body(call("POST", "/api/orders/quote", s, req));
    }

    /** A valid checkout body. Tests change single fields to test one rule at a time. */
    protected Map<String, Object> checkoutBody(MockHttpSession s, String payment, String coupon) throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("requestId", UUID.randomUUID().toString());
        req.put("shippingAddress", GOOD_ADDRESS);
        req.put("pincode", GOOD_PINCODE);
        req.put("paymentMethod", payment);
        req.put("couponCode", coupon);
        req.put("substitutionPreference", "REFUND_ITEM");
        req.put("expectedTotal", quote(s, coupon).get("total").decimalValue());
        return req;
    }

    protected MvcResult checkout(MockHttpSession s, Map<String, Object> body) throws Exception {
        return call("POST", "/api/orders/checkout", s, body);
    }

    /** Cart with one product, then a successful order. Returns the order JSON. */
    protected JsonNode placeOrder(MockHttpSession s, Product p, int qty, String payment, String coupon) throws Exception {
        addToCart(s, p, qty);
        MvcResult r = checkout(s, checkoutBody(s, payment, coupon));
        if (status(r) != 200) {
            throw new IllegalStateException("checkout failed: " + r.getResponse().getContentAsString());
        }
        return body(r);
    }

    protected MvcResult setStatus(MockHttpSession admin, long orderId, String statusName) throws Exception {
        return call("PUT", "/api/admin/orders/" + orderId + "/status", admin, Map.of("status", statusName));
    }
}
