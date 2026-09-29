package com.suryaprasanth.grocery;

import com.suryaprasanth.grocery.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SecurityAndAdminTest extends ApiTestSupport {

    @Test
    @DisplayName("TC_10 A customer cannot see or cancel someone else's order (IDOR)")
    void cannotTouchOtherCustomersOrder() throws Exception {
        MockHttpSession owner = newCustomer();
        MockHttpSession other = newCustomer();
        long id = placeOrder(owner, newProduct("50.00", 10), 1, "COD", null).get("id").asLong();

        assertEquals(403, status(call("GET", "/api/orders/" + id, other, null)));
        assertEquals(403, status(call("POST", "/api/orders/" + id + "/cancel", other, null)));
        assertEquals(403, status(call("POST", "/api/orders/" + id + "/pay", other, Map.of("outcome", "SUCCESS"))));
        assertEquals(401, status(call("GET", "/api/orders/" + id, null, null)), "not logged in");
    }

    @Test
    @DisplayName("TC_12 Five wrong passwords lock the account, even the right password is then refused")
    void loginLockout() throws Exception {
        String email = "lock-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        call("POST", "/api/auth/register", new MockHttpSession(),
                Map.of("name", "Lock Test", "email", email, "password", "right-pass"));

        for (int i = 1; i <= 5; i++) {
            assertEquals(401, status(call("POST", "/api/auth/login", new MockHttpSession(),
                    Map.of("email", email, "password", "wrong-" + i))));
        }
        assertEquals(429, status(call("POST", "/api/auth/login", new MockHttpSession(),
                Map.of("email", email, "password", "right-pass"))));
    }

    @Test
    @DisplayName("Admin pages are for admins only")
    void adminOnly() throws Exception {
        MockHttpSession customer = newCustomer();
        assertEquals(403, status(call("GET", "/api/admin/orders", customer, null)));
        assertEquals(401, status(call("GET", "/api/admin/orders", null, null)));
        assertEquals(200, status(call("GET", "/api/admin/orders", adminSession(), null)));
    }

    @Test
    @DisplayName("Admin cannot skip steps: PLACED straight to DELIVERED is refused")
    void noSkippingStatus() throws Exception {
        MockHttpSession admin = adminSession();
        MockHttpSession s = newCustomer();
        long id = placeOrder(s, newProduct("50.00", 10), 1, "COD", null).get("id").asLong();
        assertEquals(409, status(setStatus(admin, id, "DELIVERED")));
        assertEquals(200, status(setStatus(admin, id, "PACKED")));
    }

    @Test
    @DisplayName("Admin product form rejects bad values (price 0, negative stock, GST 7%)")
    void adminProductValidation() throws Exception {
        MockHttpSession admin = adminSession();
        Product existing = newProduct("50.00", 10);
        Map<String, Object> good = new HashMap<>();
        good.put("name", "Admin test product");
        good.put("price", 25.5);
        good.put("stock", 10);
        good.put("gstPercent", 5);
        good.put("categoryId", existing.getCategory().getId());
        assertEquals(200, status(call("POST", "/api/admin/products", admin, good)));

        Map<String, Object> zeroPrice = new HashMap<>(good);
        zeroPrice.put("price", 0);
        assertEquals(400, status(call("POST", "/api/admin/products", admin, zeroPrice)));

        Map<String, Object> negativeStock = new HashMap<>(good);
        negativeStock.put("stock", -1);
        assertEquals(400, status(call("PUT", "/api/admin/products/" + existing.getId(), admin, negativeStock)));

        Map<String, Object> badGst = new HashMap<>(good);
        badGst.put("gstPercent", 7);
        assertEquals(400, status(call("POST", "/api/admin/products", admin, badGst)));
    }

    @Test
    @DisplayName("Registering the same email twice (any capitals) is refused")
    void duplicateEmail() throws Exception {
        String email = "dup-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        assertEquals(201, status(call("POST", "/api/auth/register", new MockHttpSession(),
                Map.of("name", "A", "email", email, "password", "secret123"))));
        assertEquals(409, status(call("POST", "/api/auth/register", new MockHttpSession(),
                Map.of("name", "B", "email", email.toUpperCase(), "password", "secret123"))));
    }

    @Test
    @DisplayName("One Gmail inbox = one account (dots, +alias and googlemail.com are the same inbox)")
    void oneAccountPerGmail() throws Exception {
        String name = "fc" + UUID.randomUUID().toString().substring(0, 8).replaceAll("[^a-z0-9]", "");
        String first = name.substring(0, 3) + "." + name.substring(3) + "@gmail.com";   // fc1.23456@gmail.com
        assertEquals(201, status(call("POST", "/api/auth/register", new MockHttpSession(),
                Map.of("name", "First", "email", first, "password", "secret123"))));

        for (String sameInbox : new String[]{
                name + "@gmail.com",                      // without the dot
                name.toUpperCase() + "+offers@gmail.com", // capitals and +alias
                name.charAt(0) + "." + name.substring(1) + "@googlemail.com"}) {
            MvcResult r = call("POST", "/api/auth/register", new MockHttpSession(),
                    Map.of("name", "Second", "email", sameInbox, "password", "secret123"));
            assertEquals(409, status(r), sameInbox);
            assertTrue(body(r).get("message").asText().contains("already exists"));
        }

        // The same person can log in with any form of their Gmail address.
        assertEquals(200, status(call("POST", "/api/auth/login", new MockHttpSession(),
                Map.of("email", name + "+x@gmail.com", "password", "secret123"))));

        // No real name part.
        assertEquals(400, status(call("POST", "/api/auth/register", new MockHttpSession(),
                Map.of("name", "X", "email", "+" + name + "@gmail.com", "password", "secret123"))));
    }

    @Test
    @DisplayName("Other email providers: dots are NOT ignored (they can be different people)")
    void otherProvidersKeepDots() throws Exception {
        String name = "ot" + UUID.randomUUID().toString().substring(0, 8).replaceAll("[^a-z0-9]", "");
        assertEquals(201, status(call("POST", "/api/auth/register", new MockHttpSession(),
                Map.of("name", "A", "email", name + "@test.local", "password", "secret123"))));
        assertEquals(201, status(call("POST", "/api/auth/register", new MockHttpSession(),
                Map.of("name", "B", "email", name.charAt(0) + "." + name.substring(1) + "@test.local",
                        "password", "secret123"))));
    }

    @Test
    @DisplayName("Wrong-password lock can't be dodged by adding dots to a Gmail address")
    void lockoutSharedAcrossGmailForms() throws Exception {
        String name = "lk" + UUID.randomUUID().toString().substring(0, 8).replaceAll("[^a-z0-9]", "");
        call("POST", "/api/auth/register", new MockHttpSession(),
                Map.of("name", "Lock", "email", name + "@gmail.com", "password", "right-pass"));
        for (int i = 0; i < 5; i++) {
            String form = name.substring(0, i + 1) + "." + name.substring(i + 1) + "@gmail.com";
            assertEquals(401, status(call("POST", "/api/auth/login", new MockHttpSession(),
                    Map.of("email", form, "password", "wrong"))));
        }
        assertEquals(429, status(call("POST", "/api/auth/login", new MockHttpSession(),
                Map.of("email", name + "+new@gmail.com", "password", "right-pass"))));
    }
}
