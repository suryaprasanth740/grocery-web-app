package com.suryaprasanth.grocery;

import com.fasterxml.jackson.databind.JsonNode;
import com.suryaprasanth.grocery.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Phase 6a: sellers manage their own products and photos; buyers only ever see products
 * from approved shops; shops can be found by PIN code.
 */
class SellerCatalogTest extends ApiTestSupport {

    /** Smallest valid PNG header bytes (the server checks the first bytes, not the file name). */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    private MvcResult upload(MockHttpSession s, byte[] bytes, String name) throws Exception {
        return mvc.perform(multipart("/api/seller/images")
                .file(new MockMultipartFile("file", name, "image/png", bytes))
                .session(s)
                .header("X-Requested-With", "XMLHttpRequest")).andReturn();
    }

    private boolean catalogHas(long productId) throws Exception {
        for (JsonNode p : body(call("GET", "/api/products", null, null))) {
            if (p.get("id").asLong() == productId) return true;
        }
        return false;
    }

    @Test
    @DisplayName("Approved seller adds a product: it appears in the catalogue with its shop's name and the MRP discount")
    void approvedSellerProductIsVisible() throws Exception {
        SellerAccount seller = newApprovedSeller("560073", "");
        Map<String, Object> form = sellerProduct("Sona Masoori Rice", "88.00", 40);
        form.put("mrp", 100);
        form.put("brand", "Test Farms");
        MvcResult r = call("POST", "/api/seller/products", seller.session(), form);

        assertThat(status(r)).isEqualTo(201);
        long id = body(r).get("id").asLong();
        JsonNode p = body(call("GET", "/api/products/" + id, null, null));
        assertThat(p.get("shopId").asLong()).isEqualTo(seller.shopId());
        assertThat(p.get("discountPercent").asInt()).isEqualTo(12);
        assertThat(p.get("brand").asText()).isEqualTo("Test Farms");
        assertThat(body(call("GET", "/api/products?shopId=" + seller.shopId(), null, null)).size()).isEqualTo(1);
    }

    @Test
    @DisplayName("A pending shop's products are hidden from buyers (404) and can't be added to a cart")
    void pendingShopProductsHidden() throws Exception {
        SellerAccount pending = newPendingSeller("560073", "");
        long id = sellerAddsProduct(pending, "Hidden Atta", "60.00", 10);

        assertThat(status(call("GET", "/api/products/" + id, null, null))).isEqualTo(404);
        assertThat(catalogHas(id)).isFalse();
        MvcResult add = call("POST", "/api/cart/add", newCustomer(), Map.of("productId", id, "quantity", 1));
        assertThat(status(add)).isEqualTo(400);
        assertThat(body(add).get("message").asText()).contains("no longer sold");
    }

    @Test
    @DisplayName("Seller A can't read, edit, restock or switch off seller B's product (404), and B's product is unchanged")
    void sellersOnlyTouchOwnProducts() throws Exception {
        SellerAccount a = newApprovedSeller("560073", "");
        SellerAccount b = newApprovedSeller("560073", "");
        long bProduct = sellerAddsProduct(b, "B's Dal", "120.00", 15);

        assertThat(status(call("PUT", "/api/seller/products/" + bProduct, a.session(), sellerProduct("Stolen", "1.00", 0)))).isEqualTo(404);
        assertThat(status(call("PUT", "/api/seller/products/" + bProduct + "/stock", a.session(), Map.of("stock", 0)))).isEqualTo(404);
        assertThat(status(call("PUT", "/api/seller/products/" + bProduct + "/active", a.session(), Map.of("active", false)))).isEqualTo(404);
        assertThat(body(call("GET", "/api/seller/products", a.session(), null)).get("totalItems").asLong()).isZero();

        Product unchanged = productRepository.findById(bProduct).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("B's Dal");
        assertThat(unchanged.getStock()).isEqualTo(15);
        assertThat(unchanged.isActive()).isTrue();
    }

    @Test
    @DisplayName("Buyers and logged-out visitors can't use seller product endpoints (403 / 401)")
    void buyersCantManageProducts() throws Exception {
        assertThat(status(call("POST", "/api/seller/products", newCustomer(), sellerProduct("X", "10.00", 1)))).isEqualTo(403);
        assertThat(status(call("GET", "/api/seller/products", new MockHttpSession(), null))).isEqualTo(401);
    }

    @Test
    @DisplayName("Product validation: price 0, negative stock, MRP below price, GST 7% and no category are refused (400)")
    void productValidation() throws Exception {
        SellerAccount seller = newApprovedSeller("560073", "");
        Object[][] cases = {
                {"price", 0, "at least Rs 1"},
                {"stock", -1, "cannot be negative"},
                {"mrp", 50, "MRP can't be lower"},
                {"gstPercent", 7, "GST must be"},
                {"categoryId", null, "Choose a category"},
        };
        for (Object[] c : cases) {
            Map<String, Object> form = sellerProduct("Bad " + c[0], "80.00", 5);
            form.put((String) c[0], c[1]);
            MvcResult r = call("POST", "/api/seller/products", seller.session(), form);
            assertThat(status(r)).as("field " + c[0]).isEqualTo(400);
            assertThat(body(r).get("message").asText()).as("field " + c[0]).contains((String) c[2]);
        }
        assertThat(body(call("GET", "/api/seller/products", seller.session(), null)).get("totalItems").asLong()).isZero();
    }

    @Test
    @DisplayName("Switching a product off hides it and blocks adding it; switching it back on shows it again")
    void deactivateAndReactivate() throws Exception {
        SellerAccount seller = newApprovedSeller("560073", "");
        long id = sellerAddsProduct(seller, "Seasonal Mangoes", "150.00", 20);

        assertThat(status(call("PUT", "/api/seller/products/" + id + "/active", seller.session(), Map.of("active", false)))).isEqualTo(200);
        assertThat(catalogHas(id)).isFalse();
        assertThat(status(call("POST", "/api/cart/add", newCustomer(), Map.of("productId", id, "quantity", 1)))).isEqualTo(400);

        assertThat(status(call("PUT", "/api/seller/products/" + id + "/active", seller.session(), Map.of("active", true)))).isEqualTo(200);
        assertThat(catalogHas(id)).isTrue();
    }

    @Test
    @DisplayName("Paused shop: products stay visible but marked, and adding to cart is refused with a clear reason")
    void pausedShop() throws Exception {
        SellerAccount seller = newApprovedSeller("560073", "");
        long id = sellerAddsProduct(seller, "Filter Coffee", "220.00", 8);
        call("PUT", "/api/seller/shop/accepting", seller.session(), Map.of("acceptingOrders", false));

        JsonNode p = body(call("GET", "/api/products/" + id, null, null));
        assertThat(p.get("shopAcceptingOrders").asBoolean()).isFalse();
        MvcResult add = call("POST", "/api/cart/add", newCustomer(), Map.of("productId", id, "quantity", 1));
        assertThat(status(add)).isEqualTo(400);
        assertThat(body(add).get("message").asText()).contains("isn't taking new orders");
    }

    @Test
    @DisplayName("Suspended shop: all its products disappear from the catalogue")
    void suspendedShopHidden() throws Exception {
        SellerAccount seller = newApprovedSeller("560073", "");
        long id = sellerAddsProduct(seller, "Ghee", "550.00", 4);
        call("POST", "/api/admin/shops/" + seller.shopId() + "/suspend", adminSession(), Map.of("reason", "Quality complaints"));
        assertThat(catalogHas(id)).isFalse();
        assertThat(status(call("GET", "/api/products/" + id, null, null))).isEqualTo(404);
    }

    @Test
    @DisplayName("Photo upload: PNG accepted and served back; a text file (415) and a 301 KB file (413) refused")
    void photoUpload() throws Exception {
        SellerAccount seller = newApprovedSeller("560073", "");
        MvcResult ok = upload(seller.session(), PNG, "rice.png");
        assertThat(status(ok)).isEqualTo(201);
        String url = body(ok).get("url").asText();

        MvcResult served = call("GET", url, null, null);
        assertThat(status(served)).isEqualTo(200);
        assertThat(served.getResponse().getContentType()).isEqualTo("image/png");
        assertThat(served.getResponse().getContentAsByteArray()).isEqualTo(PNG);

        assertThat(status(upload(seller.session(), "hello, not a photo".getBytes(), "photo.png"))).isEqualTo(415);
        assertThat(status(upload(seller.session(), new byte[301 * 1024], "big.png"))).isEqualTo(413);
    }

    @Test
    @DisplayName("A product may only use photos its own shop uploaded: another shop's photo or an outside link is refused")
    void productPhotoOwnership() throws Exception {
        SellerAccount a = newApprovedSeller("560073", "");
        SellerAccount b = newApprovedSeller("560073", "");
        String bPhoto = body(upload(b.session(), PNG, "b.png")).get("url").asText();
        String aPhoto = body(upload(a.session(), PNG, "a.png")).get("url").asText();

        Map<String, Object> form = sellerProduct("Photo Test", "10.00", 1);
        form.put("imageUrl", bPhoto);
        assertThat(status(call("POST", "/api/seller/products", a.session(), form))).isEqualTo(400);
        form.put("imageUrl", "https://tracker.example.com/pixel.png");
        assertThat(status(call("POST", "/api/seller/products", a.session(), form))).isEqualTo(400);
        form.put("imageUrl", aPhoto);
        assertThat(status(call("POST", "/api/seller/products", a.session(), form))).isEqualTo(201);
    }

    @Test
    @DisplayName("Shop search by PIN code: only approved shops that deliver there, nearest first when location is shared")
    void shopDiscovery() throws Exception {
        SellerAccount near = newApprovedSeller("560034", "560095");
        SellerAccount pending = newPendingSeller("560034", "560095");
        SellerAccount elsewhere = newApprovedSeller("110001", "");

        JsonNode shops = body(call("GET", "/api/shops?pincode=560095", null, null));
        String ids = shops.toString();
        assertThat(ids).contains("\"id\":" + near.shopId() + ",");
        assertThat(ids).doesNotContain("\"id\":" + pending.shopId() + ",");
        assertThat(ids).doesNotContain("\"id\":" + elsewhere.shopId() + ",");
        assertThat(ids).doesNotContain("ownerEmail\":\"");  // buyers never see owner details

        String slug = body(call("GET", "/api/seller/shop", pending.session(), null)).get("slug").asText();
        assertThat(status(call("GET", "/api/shops/" + slug, null, null))).isEqualTo(404);
        assertThat(body(call("GET", "/api/delivery/check?pincode=560095", null, null)).get("serviceable").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Paged product search returns one page with totals")
    void pagedSearch() throws Exception {
        SellerAccount seller = newApprovedSeller("560073", "");
        for (int i = 0; i < 3; i++) sellerAddsProduct(seller, "Paged Biscuit " + i, "20.00", 5);
        JsonNode page = body(call("GET", "/api/products/search?q=paged%20biscuit&shopId=" + seller.shopId() + "&size=2", null, null));
        assertThat(page.get("items").size()).isEqualTo(2);
        assertThat(page.get("totalItems").asLong()).isEqualTo(3);
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);
    }
}
