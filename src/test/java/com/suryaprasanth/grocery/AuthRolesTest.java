package com.suryaprasanth.grocery;

import com.suryaprasanth.grocery.model.AccountStatus;
import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.model.Role;
import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.util.PasswordUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Phase 4: secure login and role-based access (BUYER / SELLER / ADMIN).
 */
class AuthRolesTest extends ApiTestSupport {

    private static String uniqueEmail() {
        return "auth-" + UUID.randomUUID().toString().substring(0, 12) + "@test.local";
    }

    /** Creates an account straight in the database with the given role and BCrypt password. */
    private User saveUser(Role role, String email, String password) {
        User u = new User("Role Test", email, PasswordUtil.hash(password));
        u.setRole(role);
        return userRepository.save(u);
    }

    private MvcResult login(MockHttpSession s, String email, String password) throws Exception {
        return call("POST", "/api/auth/login", s, Map.of("email", email, "password", password));
    }

    @Test
    @DisplayName("Sign-up creates a BUYER account and stores a BCrypt hash, never the password")
    void signUpIsBuyerWithBcrypt() throws Exception {
        String email = uniqueEmail();
        MvcResult r = call("POST", "/api/auth/register", new MockHttpSession(),
                Map.of("name", "New Buyer", "email", email, "password", "secret123"));

        assertThat(status(r)).isEqualTo(201);
        assertThat(body(r).get("role").asText()).isEqualTo("BUYER");
        String stored = userRepository.findByEmailIgnoreCase(email).orElseThrow().getPasswordHash();
        assertThat(stored).startsWith("$2").doesNotContain("secret123");
    }

    @Test
    @DisplayName("Sign-up ignores a 'role' field in the request: nobody can make themselves admin or seller")
    void signUpCannotChooseRole() throws Exception {
        String email = uniqueEmail();
        MvcResult r = call("POST", "/api/auth/register", new MockHttpSession(),
                Map.of("name", "Sneaky", "email", email, "password", "secret123", "role", "ADMIN"));

        assertThat(status(r)).isEqualTo(201);
        assertThat(body(r).get("role").asText()).isEqualTo("BUYER");
        assertThat(userRepository.findByEmailIgnoreCase(email).orElseThrow().isAdmin()).isFalse();
    }

    @Test
    @DisplayName("Old FreshCart password hash still logs in, and is replaced by BCrypt on that login")
    void legacyHashUpgradedOnLogin() throws Exception {
        String email = uniqueEmail();
        User old = new User("Old Customer", email, PasswordUtil.legacyHash("oldpass1"));
        userRepository.save(old);
        assertThat(PasswordUtil.needsUpgrade(old.getPasswordHash())).isTrue();

        MvcResult r = login(new MockHttpSession(), email, "oldpass1");

        assertThat(status(r)).isEqualTo(200);
        String after = userRepository.findByEmailIgnoreCase(email).orElseThrow().getPasswordHash();
        assertThat(after).startsWith("$2");
        assertThat(status(login(new MockHttpSession(), email, "oldpass1"))).isEqualTo(200); // still works
        assertThat(status(login(new MockHttpSession(), email, "wrongpass"))).isEqualTo(401);
    }

    @Test
    @DisplayName("Old role text 'USER' is read as BUYER, and unknown role text never gives extra rights")
    void legacyRoleText() {
        assertThat(Role.from("USER")).isEqualTo(Role.BUYER);
        assertThat(Role.from(null)).isEqualTo(Role.BUYER);
        assertThat(Role.from("SUPERADMIN")).isEqualTo(Role.BUYER);
        assertThat(Role.from("admin")).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("Unknown email and wrong password get the same 401 and the same message")
    void loginDoesNotRevealAccounts() throws Exception {
        String email = uniqueEmail();
        saveUser(Role.BUYER, email, "rightpass1");

        MvcResult wrongPassword = login(new MockHttpSession(), email, "nope12345");
        MvcResult noSuchUser = login(new MockHttpSession(), uniqueEmail(), "nope12345");

        assertThat(status(wrongPassword)).isEqualTo(401);
        assertThat(status(noSuchUser)).isEqualTo(401);
        assertThat(body(wrongPassword).get("message").asText())
                .isEqualTo(body(noSuchUser).get("message").asText())
                .isEqualTo("Invalid email or password");
    }

    @Test
    @DisplayName("Not logged in: shopping endpoints return 401")
    void anonymousGets401() throws Exception {
        assertThat(status(call("GET", "/api/cart", new MockHttpSession(), null))).isEqualTo(401);
        assertThat(status(call("GET", "/api/orders", new MockHttpSession(), null))).isEqualTo(401);
    }

    @Test
    @DisplayName("Seller and admin accounts get 403 on buyer-only endpoints (cart, orders)")
    void onlyBuyersCanShop() throws Exception {
        String sellerEmail = uniqueEmail();
        saveUser(Role.SELLER, sellerEmail, "sellerpass1");
        MockHttpSession seller = new MockHttpSession();
        assertThat(status(login(seller, sellerEmail, "sellerpass1"))).isEqualTo(200);
        Product p = newProduct("50.00", 10);

        assertThat(status(addToCart(seller, p, 1))).isEqualTo(403);
        assertThat(status(call("GET", "/api/orders", seller, null))).isEqualTo(403);
        assertThat(status(call("GET", "/api/cart", adminSession(), null))).isEqualTo(403);
        assertThat(stockOf(p)).isEqualTo(10);
    }

    @Test
    @DisplayName("Buyer and seller accounts get 403 on admin endpoints")
    void onlyAdminsCanAdmin() throws Exception {
        String sellerEmail = uniqueEmail();
        saveUser(Role.SELLER, sellerEmail, "sellerpass1");
        MockHttpSession seller = new MockHttpSession();
        login(seller, sellerEmail, "sellerpass1");

        assertThat(status(call("GET", "/api/admin/orders", newCustomer(), null))).isEqualTo(403);
        assertThat(status(call("GET", "/api/admin/orders", seller, null))).isEqualTo(403);
        assertThat(status(call("GET", "/api/admin/orders", adminSession(), null))).isEqualTo(200);
    }

    @Test
    @DisplayName("Suspended account: cannot log in, and an open session stops working (403)")
    void suspendedAccount() throws Exception {
        String email = uniqueEmail();
        User user = saveUser(Role.BUYER, email, "buyerpass1");
        MockHttpSession open = new MockHttpSession();
        assertThat(status(login(open, email, "buyerpass1"))).isEqualTo(200);

        user = userRepository.findById(user.getId()).orElseThrow();
        user.setAccountStatus(AccountStatus.SUSPENDED);
        userRepository.save(user);

        MvcResult inSession = call("GET", "/api/cart", open, null);
        assertThat(status(inSession)).isEqualTo(403);
        assertThat(body(inSession).get("message").asText()).contains("suspended");
        assertThat(status(login(new MockHttpSession(), email, "buyerpass1"))).isEqualTo(403);
    }

    @Test
    @DisplayName("CSRF: a POST without the X-Requested-With header is refused (403) and changes nothing")
    void csrfHeaderRequired() throws Exception {
        MockHttpSession buyer = newCustomer();
        Product p = newProduct("20.00", 5);

        MvcResult forged = mvc.perform(post("/api/cart/add")
                .session(buyer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("productId", p.getId(), "quantity", 1))))
                .andReturn();

        assertThat(status(forged)).isEqualTo(403);
        assertThat(body(call("GET", "/api/cart", buyer, null)).size()).isZero(); // cart is still empty
        assertThat(status(addToCart(buyer, p, 1))).isEqualTo(200); // same request from our page works
    }

    @Test
    @DisplayName("Password longer than 72 bytes is refused with 400, not a server error")
    void passwordTooLong() throws Exception {
        String longPassword = "é".repeat(40); // 40 characters but 80 bytes
        MvcResult r = call("POST", "/api/auth/register", new MockHttpSession(),
                Map.of("name", "Long", "email", uniqueEmail(), "password", longPassword));
        assertThat(status(r)).isEqualTo(400);
    }
}
