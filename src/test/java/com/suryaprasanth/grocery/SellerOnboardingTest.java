package com.suryaprasanth.grocery;

import com.fasterxml.jackson.databind.JsonNode;
import com.suryaprasanth.grocery.model.Shop;
import com.suryaprasanth.grocery.repository.AuditLogRepository;
import com.suryaprasanth.grocery.repository.ShopRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5: shop applications, admin approval, account suspension and the audit trail.
 */
class SellerOnboardingTest extends ApiTestSupport {

    @Autowired private ShopRepository shopRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private static String uniqueEmail() {
        return "seller-" + UUID.randomUUID().toString().substring(0, 12) + "@test.local";
    }

    /** A valid "Sell on NammaBasket" form. Tests change one field to test one rule. */
    private Map<String, Object> sellerForm(String email) {
        Map<String, Object> f = new HashMap<>();
        f.put("ownerName", "Lakshmi Devi");
        f.put("email", email);
        f.put("password", "kirana123");
        f.put("shopName", "Sri Lakshmi Stores");
        f.put("category", "KIRANA");
        f.put("phone", "9876543210");
        f.put("addressLine", "14, 2nd Main, Nagasandra, Bengaluru");
        f.put("pincode", "560073");
        f.put("servicePincodes", "560076, 560068");
        f.put("openTime", "08:00");
        f.put("closeTime", "21:30");
        f.put("offersDelivery", true);
        f.put("offersPickup", true);
        f.put("deliveryFee", 25);
        f.put("freeDeliveryAbove", 400);
        f.put("minOrderAmount", 100);
        return f;
    }

    private MvcResult applyAsSeller(MockHttpSession session, Map<String, Object> form) throws Exception {
        return call("POST", "/api/auth/register-seller", session, form);
    }

    /** Applies as a new seller and returns the logged-in seller session; the shop id is in the session map. */
    private record Seller(MockHttpSession session, long shopId, String email) { }

    private Seller newSeller() throws Exception {
        MockHttpSession s = new MockHttpSession();
        String email = uniqueEmail();
        MvcResult r = applyAsSeller(s, sellerForm(email));
        assertThat(status(r)).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return new Seller(s, body(r).get("shop").get("id").asLong(), email);
    }

    private Seller approvedSeller() throws Exception {
        Seller seller = newSeller();
        assertThat(status(call("POST", "/api/admin/shops/" + seller.shopId() + "/approve", adminSession(), null))).isEqualTo(200);
        return seller;
    }

    @Test
    @DisplayName("Seller application creates a SELLER account and a PENDING shop with the owner's details")
    void applicationCreatesPendingShop() throws Exception {
        MockHttpSession s = new MockHttpSession();
        MvcResult r = applyAsSeller(s, sellerForm(uniqueEmail()));

        assertThat(status(r)).isEqualTo(201);
        JsonNode b = body(r);
        assertThat(b.get("role").asText()).isEqualTo("SELLER");
        JsonNode shop = b.get("shop");
        assertThat(shop.get("status").asText()).isEqualTo("PENDING");
        assertThat(shop.get("slug").asText()).startsWith("sri-lakshmi-stores");
        // own PIN code is always included, typed ones are cleaned up
        assertThat(shop.get("servicePincodes").toString()).isEqualTo("[\"560073\",\"560076\",\"560068\"]");
        assertThat(shop.get("openTime").asText()).isEqualTo("08:00");
        assertThat(shop.get("deliveryFee").decimalValue()).isEqualByComparingTo("25.00");
        // the seller is logged in and can read their own shop
        assertThat(body(call("GET", "/api/seller/shop", s, null)).get("status").asText()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Bad shop details are refused with 400 and create no account (all-or-nothing)")
    void invalidApplications() throws Exception {
        Object[][] cases = {
                {"phone", "12345", "10-digit mobile"},
                {"pincode", "56007", "6-digit PIN"},
                {"servicePincodes", "560076, abc", "PIN codes must be 6 digits"},
                {"closeTime", "07:00", "Closing time must be after opening time"},
                {"deliveryFee", -5, "can't be negative"},
                {"shopName", "  ", "Shop name is required"},
                {"password", "short", "8 to 72"},
        };
        for (Object[] c : cases) {
            String email = uniqueEmail();
            Map<String, Object> form = sellerForm(email);
            form.put((String) c[0], c[1]);
            MvcResult r = applyAsSeller(new MockHttpSession(), form);
            assertThat(status(r)).as("field " + c[0]).isEqualTo(400);
            assertThat(body(r).get("message").asText()).as("field " + c[0]).contains((String) c[2]);
            assertThat(userRepository.findByEmailIgnoreCase(email)).as("no account for " + c[0]).isEmpty();
        }
        Map<String, Object> noFulfilment = sellerForm(uniqueEmail());
        noFulfilment.put("offersDelivery", false);
        noFulfilment.put("offersPickup", false);
        assertThat(status(applyAsSeller(new MockHttpSession(), noFulfilment))).isEqualTo(400);
    }

    @Test
    @DisplayName("Applying with an email that already has an account is refused (409)")
    void duplicateEmail() throws Exception {
        Seller first = newSeller();
        MvcResult r = applyAsSeller(new MockHttpSession(), sellerForm(first.email().toUpperCase()));
        assertThat(status(r)).isEqualTo(409);
    }

    @Test
    @DisplayName("A pending shop can't take orders yet: pausing/resuming is refused (409)")
    void pendingShopCannotToggleOrders() throws Exception {
        Seller seller = newSeller();
        MvcResult r = call("PUT", "/api/seller/shop/accepting", seller.session(), Map.of("acceptingOrders", false));
        assertThat(status(r)).isEqualTo(409);
        assertThat(body(r).get("message").asText()).contains("approves");
    }

    @Test
    @DisplayName("Buyers and logged-out visitors can't use seller endpoints (403 / 401)")
    void sellerEndpointsNeedSeller() throws Exception {
        assertThat(status(call("GET", "/api/seller/shop", newCustomer(), null))).isEqualTo(403);
        assertThat(status(call("GET", "/api/seller/shop", new MockHttpSession(), null))).isEqualTo(401);
        assertThat(status(call("GET", "/api/seller/shop", adminSession(), null))).isEqualTo(403);
    }

    @Test
    @DisplayName("Sellers and buyers can't approve shops or read the admin shop list (403)")
    void onlyAdminsDecide() throws Exception {
        Seller seller = newSeller();
        Seller other = newSeller();
        assertThat(status(call("POST", "/api/admin/shops/" + other.shopId() + "/approve", seller.session(), null))).isEqualTo(403);
        assertThat(status(call("POST", "/api/admin/shops/" + other.shopId() + "/approve", newCustomer(), null))).isEqualTo(403);
        assertThat(status(call("GET", "/api/admin/shops?status=PENDING", seller.session(), null))).isEqualTo(403);
        assertThat(shopRepository.findById(other.shopId()).orElseThrow().getStatus().name()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Admin approves: shop becomes APPROVED, can pause orders, and an audit row is written")
    void approveFlow() throws Exception {
        Seller seller = newSeller();
        MockHttpSession admin = adminSession();
        assertThat(body(call("GET", "/api/admin/shops?status=PENDING", admin, null)).toString()).contains("\"id\":" + seller.shopId());

        MvcResult r = call("POST", "/api/admin/shops/" + seller.shopId() + "/approve", admin, null);
        assertThat(status(r)).isEqualTo(200);
        assertThat(body(r).get("status").asText()).isEqualTo("APPROVED");
        assertThat(body(r).get("approvedAt").isNull()).isFalse();

        assertThat(auditLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("SHOP", seller.shopId()))
                .extracting("action").containsExactly("SHOP_APPROVED");

        MvcResult paused = call("PUT", "/api/seller/shop/accepting", seller.session(), Map.of("acceptingOrders", false));
        assertThat(status(paused)).isEqualTo(200);
        assertThat(body(paused).get("acceptingOrders").asBoolean()).isFalse();

        // approving twice is refused: only PENDING can be approved
        assertThat(status(call("POST", "/api/admin/shops/" + seller.shopId() + "/approve", admin, null))).isEqualTo(409);
    }

    @Test
    @DisplayName("Admin rejects with a reason; the seller sees it, fixes the details and resubmits")
    void rejectAndResubmit() throws Exception {
        Seller seller = newSeller();
        MockHttpSession admin = adminSession();

        assertThat(status(call("POST", "/api/admin/shops/" + seller.shopId() + "/reject", admin, Map.of("reason", "")))).isEqualTo(400);
        MvcResult r = call("POST", "/api/admin/shops/" + seller.shopId() + "/reject", admin,
                Map.of("reason", "Shop address is incomplete. Add the street name."));
        assertThat(status(r)).isEqualTo(200);

        JsonNode mine = body(call("GET", "/api/seller/shop", seller.session(), null));
        assertThat(mine.get("status").asText()).isEqualTo("REJECTED");
        assertThat(mine.get("statusReason").asText()).contains("street name");

        Map<String, Object> fixed = sellerForm(seller.email());
        fixed.put("addressLine", "14, 2nd Main Road, Nagasandra Main Street, Bengaluru");
        assertThat(status(call("PUT", "/api/seller/shop", seller.session(), fixed))).isEqualTo(200);
        MvcResult again = call("POST", "/api/seller/shop/resubmit", seller.session(), null);
        assertThat(status(again)).isEqualTo(200);
        assertThat(body(again).get("status").asText()).isEqualTo("PENDING");
        assertThat(body(again).get("statusReason").isNull()).isTrue();

        // resubmitting a pending application is refused
        assertThat(status(call("POST", "/api/seller/shop/resubmit", seller.session(), null))).isEqualTo(409);
    }

    @Test
    @DisplayName("Suspend and reactivate a shop: only APPROVED can be suspended, only SUSPENDED reactivated")
    void suspendShop() throws Exception {
        Seller seller = approvedSeller();
        MockHttpSession admin = adminSession();
        String url = "/api/admin/shops/" + seller.shopId();

        assertThat(status(call("POST", url + "/reactivate", admin, null))).isEqualTo(409);
        assertThat(status(call("POST", url + "/suspend", admin, Map.of("reason", "Selling expired stock")))).isEqualTo(200);
        assertThat(status(call("PUT", "/api/seller/shop/accepting", seller.session(), Map.of("acceptingOrders", true)))).isEqualTo(409);
        assertThat(status(call("POST", url + "/reactivate", admin, null))).isEqualTo(200);

        assertThat(auditLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("SHOP", seller.shopId()))
                .extracting("action").contains("SHOP_APPROVED", "SHOP_SUSPENDED", "SHOP_REACTIVATED");
    }

    @Test
    @DisplayName("A seller's edits only ever change their OWN shop")
    void sellerEditsOnlyOwnShop() throws Exception {
        Seller a = newSeller();
        Seller b = newSeller();
        Map<String, Object> form = sellerForm(a.email());
        form.put("shopName", "Renamed By A");
        assertThat(status(call("PUT", "/api/seller/shop", a.session(), form))).isEqualTo(200);

        Shop shopA = shopRepository.findById(a.shopId()).orElseThrow();
        Shop shopB = shopRepository.findById(b.shopId()).orElseThrow();
        assertThat(shopA.getName()).isEqualTo("Renamed By A");
        assertThat(shopB.getName()).isEqualTo("Sri Lakshmi Stores");
    }

    @Test
    @DisplayName("Admin suspends a seller account: login refused; admins can't be suspended; audit written")
    void suspendAccount() throws Exception {
        Seller seller = newSeller();
        MockHttpSession admin = adminSession();
        long sellerId = userRepository.findByEmailIgnoreCase(seller.email()).orElseThrow().getId();
        long adminId = userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow().getId();

        assertThat(status(call("POST", "/api/admin/users/" + sellerId + "/suspend", admin, Map.of("reason", "Fake shop details")))).isEqualTo(200);
        assertThat(status(call("POST", "/api/auth/login", new MockHttpSession(),
                Map.of("email", seller.email(), "password", "kirana123")))).isEqualTo(403);
        assertThat(status(call("POST", "/api/admin/users/" + adminId + "/suspend", admin, Map.of("reason", "Testing self")))).isEqualTo(409);

        assertThat(status(call("POST", "/api/admin/users/" + sellerId + "/reactivate", admin, null))).isEqualTo(200);
        assertThat(status(call("POST", "/api/auth/login", new MockHttpSession(),
                Map.of("email", seller.email(), "password", "kirana123")))).isEqualTo(200);
        assertThat(auditLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("USER", sellerId))
                .extracting("action").contains("ACCOUNT_SUSPENDED", "ACCOUNT_REACTIVATED");
    }

    @Test
    @DisplayName("Admin account list and stats show real counts, and never a password hash")
    void accountListAndStats() throws Exception {
        Seller seller = newSeller();
        MockHttpSession admin = adminSession();

        MvcResult list = call("GET", "/api/admin/users?role=SELLER&size=100", admin, null);
        assertThat(status(list)).isEqualTo(200);
        String text = list.getResponse().getContentAsString();
        assertThat(text).contains(seller.email()).contains("Sri Lakshmi Stores").doesNotContain("passwordHash").doesNotContain("$2");
        assertThat(status(call("GET", "/api/admin/users?role=ADMIN", admin, null))).isEqualTo(400);

        JsonNode stats = body(call("GET", "/api/admin/stats", admin, null));
        assertThat(stats.get("sellers").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(stats.get("shops").get("PENDING").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Shop PIN-code rules: exact codes, 560xxx areas, and invalid input")
    void pincodeRules() {
        Shop s = new Shop();
        s.setServicePincodes(Shop.normalisePincodes("560073", "560076 560xxx"));
        assertThat(s.servesPincode("560073")).isTrue();
        assertThat(s.servesPincode("560099")).isTrue();   // inside 560xxx
        assertThat(s.servesPincode("110001")).isFalse();
        assertThat(s.servesPincode("56007")).isFalse();
        assertThat(Shop.normalisePincodes("560073", "56xxxx")).isNull();
        assertThat(Shop.normalisePincodes("560073", null)).isEqualTo("560073");
    }
}
