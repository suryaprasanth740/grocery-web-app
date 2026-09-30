package com.suryaprasanth.grocery;

import com.fasterxml.jackson.databind.JsonNode;
import com.suryaprasanth.grocery.model.Order;
import com.suryaprasanth.grocery.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Smart shopping: paste-a-list, recipes & festival kits, budget mode, cheaper swaps, running low. */
class SmartShoppingTest extends ApiTestSupport {

    private JsonNode parse(String text) throws Exception {
        MvcResult r = call("POST", "/api/smart-list/parse", null, Map.of("text", text));
        assertEquals(200, status(r), r.getResponse().getContentAsString());
        return body(r);
    }

    private static String productName(JsonNode line) {
        return line.get("product").isNull() ? null : line.get("product").get("name").asText();
    }

    private Product seeded(String name) {
        return productRepository.findAll().stream().filter(p -> p.getName().equals(name)).findFirst().orElseThrow();
    }

    // ---------------- Paste your list ----------------

    @Test
    @DisplayName("Shopping list: quantities, units and Indian-language words are understood")
    void parsesRealWorldList() throws Exception {
        JsonNode lines = parse("2 kg tomatoes\n1 litre milk\natta 5kg, dozen eggs; thakkali\n2 packet doodh\n- 500 g paneer");
        assertEquals(7, lines.size());

        assertEquals("Tomatoes", productName(lines.get(0)));
        assertEquals(2, lines.get(0).get("quantity").asInt(), "2 kg of a 1 kg pack = 2 packs");

        assertEquals("Full Cream Milk", productName(lines.get(1)));
        assertEquals(2, lines.get(1).get("quantity").asInt(), "1 litre of 500 ml packs = 2 packs");

        assertEquals("Whole Wheat Atta", productName(lines.get(2)));
        assertEquals(1, lines.get(2).get("quantity").asInt(), "5 kg of a 5 kg pack = 1 pack");

        assertEquals("Farm Eggs", productName(lines.get(3)));
        assertEquals(1, lines.get(3).get("quantity").asInt(), "a dozen eggs = one box of 12");

        assertEquals("Tomatoes", productName(lines.get(4)), "thakkali is Tamil for tomato");
        assertEquals("Full Cream Milk", productName(lines.get(5)), "doodh is Hindi for milk");
        assertEquals(2, lines.get(5).get("quantity").asInt(), "2 packets = 2 packs");

        assertEquals("Paneer", productName(lines.get(6)));
        assertEquals(3, lines.get(6).get("quantity").asInt(), "500 g of 200 g packs = 3 packs");
    }

    @Test
    @DisplayName("Shopping list: the most specific product wins, typos are forgiven, unknown items are flagged")
    void picksTheRightProduct() throws Exception {
        JsonNode lines = parse("moong dal\nbasmati rice\nrice\ntoned milk\ntomatos\nunicorn food");
        assertEquals("Moong Dal", productName(lines.get(0)));
        assertEquals("Basmati Rice", productName(lines.get(1)));
        assertEquals("Sona Masoori Rice", productName(lines.get(2)), "plain 'rice' = everyday rice");
        assertEquals("Toned Milk", productName(lines.get(3)));
        assertEquals("Tomatoes", productName(lines.get(4)), "spelling mistake is forgiven");
        assertNull(productName(lines.get(5)));
        assertNotNull(lines.get(5).get("note").asText());
    }

    @Test
    @DisplayName("Shopping list: empty text is refused")
    void emptyListRefused() throws Exception {
        assertEquals(400, status(call("POST", "/api/smart-list/parse", null, Map.of("text", "   "))));
    }

    // ---------------- Add many ----------------

    @Test
    @DisplayName("Add all to cart: good items are added, out-of-stock items are skipped with a reason")
    void addManySkipsBadItems() throws Exception {
        Product ok = newProduct("40.00", 10);
        Product empty = newProduct("40.00", 0);
        MockHttpSession s = newCustomer();

        MvcResult r = call("POST", "/api/cart/add-many", s, Map.of("items", List.of(
                Map.of("productId", ok.getId(), "quantity", 2),
                Map.of("productId", empty.getId(), "quantity", 1))));
        assertEquals(200, status(r));
        JsonNode result = body(r);
        assertEquals(1, result.get("added").size());
        assertEquals(1, result.get("skipped").size());
        assertTrue(result.get("skipped").get(0).asText().contains("out of stock"));
        assertEquals(1, result.get("cart").size());

        assertEquals(401, status(call("POST", "/api/cart/add-many", new MockHttpSession(),
                Map.of("items", List.of(Map.of("productId", ok.getId(), "quantity", 1))))));
    }

    // ---------------- Recipes & kits ----------------

    @Test
    @DisplayName("Recipes: ingredients scale with the number of people and become whole packs")
    void recipeScales() throws Exception {
        JsonNode list = body(call("GET", "/api/recipes", null, null));
        assertTrue(list.size() >= 8);
        boolean hasKit = false;
        for (JsonNode r : list) {
            hasKit |= "KIT".equals(r.get("type").asText());
        }
        assertTrue(hasKit, "festival kits are listed");

        JsonNode forFour = body(call("GET", "/api/recipes/sambar?servings=4", null, null));
        JsonNode dal = findLine(forFour, "Toor Dal");
        assertEquals("200 g", dal.get("needed").asText());
        assertEquals(1, dal.get("packs").asInt());

        JsonNode forTwentyFour = body(call("GET", "/api/recipes/sambar?servings=24", null, null));
        JsonNode bigDal = findLine(forTwentyFour, "Toor Dal");
        assertEquals("1.2 kg", bigDal.get("needed").asText());
        assertEquals(2, bigDal.get("packs").asInt(), "1.2 kg needs two 1 kg packs");
        assertTrue(forTwentyFour.get("estimatedTotal").decimalValue()
                .compareTo(forFour.get("estimatedTotal").decimalValue()) > 0);

        assertEquals(400, status(call("GET", "/api/recipes/sambar?servings=0", null, null)));
        assertEquals(404, status(call("GET", "/api/recipes/no-such-dish", null, null)));
    }

    private static JsonNode findLine(JsonNode recipe, String ingredient) {
        for (JsonNode line : recipe.get("lines")) {
            if (ingredient.equals(line.get("ingredient").asText())) {
                return line;
            }
        }
        throw new AssertionError(ingredient + " not in recipe");
    }

    // ---------------- Budget ----------------

    @Test
    @DisplayName("Budget: spent this month, remaining, and a warning when the cart goes over")
    void budgetMode() throws Exception {
        MockHttpSession s = newCustomer();
        assertEquals("NO_BUDGET", body(call("GET", "/api/budget", s, null)).get("status").asText());

        assertEquals(400, status(call("PUT", "/api/budget", s, Map.of("amount", 50))), "too small");

        JsonNode set = body(call("PUT", "/api/budget", s, Map.of("amount", 1000)));
        assertEquals(0, new BigDecimal("1000").compareTo(set.get("budget").decimalValue()));
        assertEquals("OK", set.get("status").asText());

        JsonNode order = placeOrder(s, newProduct("300.00", 10), 1, "COD", null);
        BigDecimal paid = order.get("totalAmount").decimalValue();

        JsonNode afterOrder = body(call("GET", "/api/budget", s, null));
        assertEquals(0, paid.compareTo(afterOrder.get("spent").decimalValue()));
        assertEquals(0, new BigDecimal("1000").subtract(paid).compareTo(afterOrder.get("remaining").decimalValue()));
        assertEquals(1, afterOrder.get("ordersThisMonth").asInt());

        addToCart(s, newProduct("700.00", 10), 1);
        assertEquals("OVER", body(call("GET", "/api/budget", s, null)).get("status").asText());

        // Turning it off
        Map<String, Object> off = new HashMap<>();
        off.put("amount", null);
        assertEquals("NO_BUDGET", body(call("PUT", "/api/budget", s, off)).get("status").asText());
    }

    @Test
    @DisplayName("Budget: cheaper swap is suggested and can be applied in one click")
    void cheaperSwap() throws Exception {
        Product fullCream = seeded("Full Cream Milk");
        Product toned = seeded("Toned Milk");
        MockHttpSession s = newCustomer();
        addToCart(s, fullCream, 2);

        JsonNode swaps = body(call("GET", "/api/budget", s, null)).get("swaps");
        assertEquals(1, swaps.size());
        assertEquals("Toned Milk", swaps.get(0).get("to").get("name").asText());
        BigDecimal expected = fullCream.getPrice().subtract(toned.getPrice()).multiply(BigDecimal.valueOf(2));
        assertEquals(0, expected.compareTo(swaps.get(0).get("saving").decimalValue()));

        JsonNode cart = body(call("POST", "/api/cart/swap", s,
                Map.of("fromProductId", fullCream.getId(), "toProductId", toned.getId())));
        assertEquals(1, cart.size());
        assertEquals("Toned Milk", cart.get(0).get("product").get("name").asText());
        assertEquals(2, cart.get(0).get("quantity").asInt(), "same quantity after swap");
    }

    // ---------------- Running low ----------------

    @Test
    @DisplayName("Running low: learns how often you buy something and says when it's due")
    void reorderReminders() throws Exception {
        MockHttpSession s = newCustomer();
        Product rice = newProduct("50.00", 20);
        Product milk = newProduct("30.00", 20);

        // rice every 4 days: bought 8 and 4 days ago and today -> next in 4 days
        backdate(placeOrder(s, rice, 1, "COD", null), 8);
        backdate(placeOrder(s, rice, 1, "COD", null), 4);
        placeOrder(s, rice, 1, "COD", null);
        // milk every 4 days: bought 10 and 6 days ago -> was due 2 days ago
        backdate(placeOrder(s, milk, 2, "COD", null), 10);
        backdate(placeOrder(s, milk, 2, "COD", null), 6);

        JsonNode list = body(call("GET", "/api/reorder", s, null));
        assertEquals(2, list.size());

        JsonNode first = list.get(0);
        assertEquals(milk.getName(), first.get("product").get("name").asText(), "most urgent first");
        assertEquals("DUE", first.get("status").asText());
        assertEquals(4, first.get("everyDays").asInt());
        assertEquals(-2, first.get("daysLeft").asInt());
        assertEquals(2, first.get("typicalQuantity").asInt());

        JsonNode second = list.get(1);
        assertEquals(rice.getName(), second.get("product").get("name").asText());
        assertEquals("LATER", second.get("status").asText());
        assertEquals(4, second.get("daysLeft").asInt());
        assertEquals(3, second.get("timesBought").asInt());
    }

    private void backdate(JsonNode orderJson, int days) {
        Order order = orderRepository.findById(orderJson.get("id").asLong()).orElseThrow();
        order.setCreatedAt(LocalDateTime.now().minusDays(days));
        orderRepository.save(order);
    }
}
