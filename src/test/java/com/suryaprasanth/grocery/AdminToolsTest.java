package com.suryaprasanth.grocery;

import com.fasterxml.jackson.databind.JsonNode;
import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.repository.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 7: admin tools. Buyer reports and their investigation, categories, promotions,
 * hiding products, the order search across shops and marketplace statistics.
 */
class AdminToolsTest extends ApiTestSupport {

    @Autowired private AuditLogRepository auditLogRepository;

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private Map<String, Object> coupon(String code, String type, int value) {
        Map<String, Object> c = new HashMap<>();
        c.put("code", code);
        c.put("description", "Test offer");
        c.put("type", type);
        c.put("value", value);
        c.put("minOrder", 0);
        c.put("lastDay", LocalDate.now().plusDays(10).toString());
        c.put("perUserLimit", 1);
        return c;
    }

    private Map<String, Object> resolve(String status, String note, String action) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", status);
        m.put("note", note);
        m.put("action", action);
        return m;
    }

    @Test
    @DisplayName("Buyer reports a product: admin sees it with shop and product names; a second open report is refused (409)")
    void buyerReportsProduct() throws Exception {
        SellerAccount seller = newApprovedSeller("560073", "");
        long productId = sellerAddsProduct(seller, "Suspicious Ghee", "400.00", 5);
        MockHttpSession buyer = newCustomer();
        Map<String, Object> body = Map.of("shopId", seller.shopId(), "productId", productId,
                "reason", "EXPIRED_OR_BAD_QUALITY", "details", "Smelled off, date looked changed");

        assertThat(status(call("POST", "/api/reports", buyer, body))).isEqualTo(201);
        assertThat(status(call("POST", "/api/reports", buyer, body))).isEqualTo(409);

        JsonNode open = body(call("GET", "/api/admin/reports", adminSession(), null));
        JsonNode mine = null;
        for (JsonNode r : open) if (r.get("productId").asLong() == productId) mine = r;
        assertThat(mine).isNotNull();
        assertThat(mine.get("targetType").asText()).isEqualTo("PRODUCT");
        assertThat(mine.get("productName").asText()).isEqualTo("Suspicious Ghee");
        assertThat(mine.get("reasonLabel").asText()).contains("Expired");
    }

    @Test
    @DisplayName("Reports: only buyers can send them (seller 403); a product from another shop is refused (404)")
    void reportRules() throws Exception {
        SellerAccount a = newApprovedSeller("560073", "");
        SellerAccount b = newApprovedSeller("560073", "");
        long bProduct = sellerAddsProduct(b, "B Item", "10.00", 5);

        assertThat(status(call("POST", "/api/reports", a.session(),
                Map.of("shopId", b.shopId(), "reason", "OTHER")))).isEqualTo(403);
        assertThat(status(call("POST", "/api/reports", newCustomer(),
                Map.of("shopId", a.shopId(), "productId", bProduct, "reason", "WRONG_PRICE")))).isEqualTo(404);
        assertThat(status(call("GET", "/api/admin/reports", newCustomer(), null))).isEqualTo(403);
    }

    @Test
    @DisplayName("Resolving a report with HIDE_PRODUCT hides it from buyers and writes the audit trail; closing twice is refused")
    void resolveHidesProduct() throws Exception {
        SellerAccount seller = newApprovedSeller("560073", "");
        long productId = sellerAddsProduct(seller, "Fake Saffron", "999.00", 5);
        MockHttpSession buyer = newCustomer();
        long reportId = body(call("POST", "/api/reports", buyer,
                Map.of("shopId", seller.shopId(), "productId", productId, "reason", "MISLEADING_INFO"))).get("id").asLong();
        MockHttpSession admin = adminSession();

        assertThat(status(call("POST", "/api/admin/reports/" + reportId + "/resolve", admin,
                resolve("DISMISSED", "Checked photos", "HIDE_PRODUCT")))).isEqualTo(400);
        MvcResult r = call("POST", "/api/admin/reports/" + reportId + "/resolve", admin,
                resolve("RESOLVED", "Photo was of a different product", "HIDE_PRODUCT"));
        assertThat(status(r)).isEqualTo(200);
        assertThat(body(r).get("status").asText()).isEqualTo("RESOLVED");

        assertThat(status(call("GET", "/api/products/" + productId, null, null))).isEqualTo(404);
        assertThat(auditLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("REPORT", reportId))
                .extracting("action").containsExactly("REPORT_RESOLVED");
        assertThat(auditLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("PRODUCT", productId))
                .extracting("action").contains("PRODUCT_HIDDEN");
        assertThat(status(call("POST", "/api/admin/reports/" + reportId + "/resolve", admin,
                resolve("DISMISSED", "Second look", "NONE")))).isEqualTo(409);
    }

    @Test
    @DisplayName("Resolving a shop report with SUSPEND_SHOP suspends the shop, so its products disappear")
    void resolveSuspendsShop() throws Exception {
        SellerAccount seller = newApprovedSeller("560073", "");
        long productId = sellerAddsProduct(seller, "Anything", "50.00", 5);
        long reportId = body(call("POST", "/api/reports", newCustomer(),
                Map.of("shopId", seller.shopId(), "reason", "FAKE_SHOP"))).get("id").asLong();

        assertThat(status(call("POST", "/api/admin/reports/" + reportId + "/resolve", adminSession(),
                resolve("RESOLVED", "Address does not exist", "SUSPEND_SHOP")))).isEqualTo(200);
        assertThat(body(call("GET", "/api/seller/shop", seller.session(), null)).get("status").asText()).isEqualTo("SUSPENDED");
        assertThat(status(call("GET", "/api/products/" + productId, null, null))).isEqualTo(404);
    }

    @Test
    @DisplayName("Categories: add, rename, duplicate names refused (409), and a category with products can't be deleted")
    void categories() throws Exception {
        MockHttpSession admin = adminSession();
        String name = "Test Cat " + unique("");
        MvcResult created = call("POST", "/api/admin/categories", admin, Map.of("name", name, "icon", "🧂"));
        assertThat(status(created)).isEqualTo(201);
        long id = body(created).get("id").asLong();

        assertThat(status(call("POST", "/api/admin/categories", admin, Map.of("name", name.toUpperCase())))).isEqualTo(409);
        assertThat(status(call("PUT", "/api/admin/categories/" + id, admin, Map.of("name", name + " Renamed")))).isEqualTo(200);
        assertThat(status(call("POST", "/api/admin/categories", newCustomer(), Map.of("name", "Nope")))).isEqualTo(403);

        long usedCategory = categoryRepository.findAll().get(0).getId();
        assertThat(status(call("DELETE", "/api/admin/categories/" + usedCategory, admin, null))).isEqualTo(409);
        assertThat(status(call("DELETE", "/api/admin/categories/" + id, admin, null))).isEqualTo(204);
        assertThat(categoryRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("Promotions: a new coupon works at checkout; bad percent coupons and duplicate codes are refused")
    void promotions() throws Exception {
        MockHttpSession admin = adminSession();
        String code = unique("NB");
        MvcResult created = call("POST", "/api/admin/coupons", admin, coupon(code, "FLAT", 40));
        assertThat(status(created)).isEqualTo(201);

        assertThat(status(call("POST", "/api/admin/coupons", admin, coupon(code, "FLAT", 40)))).isEqualTo(409);
        assertThat(status(call("POST", "/api/admin/coupons", admin, coupon(unique("PC"), "PERCENT", 95)))).isEqualTo(400);
        assertThat(status(call("POST", "/api/admin/coupons", admin, coupon(unique("PC"), "PERCENT", 10)))).isEqualTo(400); // no max discount
        Map<String, Object> past = coupon(unique("OLD"), "FLAT", 10);
        past.put("lastDay", LocalDate.now().minusDays(2).toString());
        assertThat(status(call("POST", "/api/admin/coupons", admin, past))).isEqualTo(400);

        MockHttpSession buyer = newCustomer();
        Product p = newProduct("300.00", 10);
        addToCart(buyer, p, 1);
        JsonNode bill = quote(buyer, code);
        assertThat(bill.get("couponApplied").asBoolean()).isTrue();
        assertThat(bill.get("discount").decimalValue()).isEqualByComparingTo("40.00");

        long couponId = body(created).get("id").asLong();
        call("PUT", "/api/admin/coupons/" + couponId + "/active", admin, Map.of("active", false));
        assertThat(quote(buyer, code).get("couponApplied").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Admin hides any shop's product with a reason (required) and can show it again")
    void hideProduct() throws Exception {
        SellerAccount seller = newApprovedSeller("560073", "");
        long productId = sellerAddsProduct(seller, "Loose Oil", "120.00", 5);
        MockHttpSession admin = adminSession();

        assertThat(status(call("PUT", "/api/admin/products/" + productId + "/active", admin, Map.of("active", false)))).isEqualTo(400);
        assertThat(status(call("PUT", "/api/admin/products/" + productId + "/active", admin,
                Map.of("active", false, "reason", "Unlabelled loose oil")))).isEqualTo(200);
        assertThat(status(call("GET", "/api/products/" + productId, null, null))).isEqualTo(404);
        assertThat(status(call("PUT", "/api/admin/products/" + productId + "/active", admin, Map.of("active", true)))).isEqualTo(200);
        assertThat(status(call("GET", "/api/products/" + productId, null, null))).isEqualTo(200);
    }

    @Test
    @DisplayName("Admin order search filters by shop and status; stats include last-7-days value and open reports")
    void orderSearchAndStats() throws Exception {
        SellerAccount seller = newApprovedSeller("560073", "");
        Product p = productRepository.findById(sellerAddsProduct(seller, "Search Item", "70.00", 10)).orElseThrow();
        MockHttpSession buyer = newCustomer();
        long orderId = placeOrder(buyer, p, 1, "COD", null).get("id").asLong();
        MockHttpSession admin = adminSession();

        JsonNode byShop = body(call("GET", "/api/admin/orders/search?shopId=" + seller.shopId(), admin, null));
        assertThat(byShop.get("totalItems").asLong()).isEqualTo(1);
        assertThat(byShop.get("items").get(0).get("id").asLong()).isEqualTo(orderId);
        assertThat(body(call("GET", "/api/admin/orders/search?shopId=" + seller.shopId() + "&status=DELIVERED", admin, null))
                .get("totalItems").asLong()).isZero();
        assertThat(status(call("GET", "/api/admin/orders/search?status=FLYING", admin, null))).isEqualTo(400);

        JsonNode stats = body(call("GET", "/api/admin/stats", admin, null));
        assertThat(stats.get("ordersLast7Days").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(stats.get("valueLast7Days").decimalValue()).isGreaterThanOrEqualTo(new java.math.BigDecimal("90.00"));
        assertThat(stats.has("openReports")).isTrue();
        assertThat(stats.get("topShopsLast7Days").isArray()).isTrue();
    }

    @Test
    @DisplayName("Report reasons list is public and the dispute list shows the order and shop of each problem")
    void reasonsAndDisputes() throws Exception {
        JsonNode reasons = body(call("GET", "/api/reports/reasons", null, null));
        assertThat(reasons.size()).isEqualTo(6);
        assertThat(reasons.get(0).get("label").asText()).isNotBlank();

        MockHttpSession admin = adminSession();
        MockHttpSession buyer = newCustomer();
        JsonNode order = placeOrder(buyer, newProduct("60.00", 10), 2, "COD", null);
        long id = order.get("id").asLong();
        deliverOrder(admin, id);
        call("POST", "/api/orders/" + id + "/issues", buyer,
                Map.of("orderItemId", order.get("items").get(0).get("id").asLong(), "quantity", 1, "reason", "MISSING"));
        boolean found = false;
        for (JsonNode issue : body(call("GET", "/api/admin/issues", admin, null))) {
            if (issue.get("orderId").asLong() == id) {
                found = true;
                assertThat(issue.get("shopName").asText()).isEqualTo(demoShop().getName());
            }
        }
        assertThat(found).isTrue();
    }
}
