package com.suryaprasanth.grocery.config;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Recipes and festival kits. Each ingredient points to a product by name and says how much
 * is needed for the base number of people. The RecipeService scales it and turns it into packs.
 *
 * Kept in code (not the database) because it is fixed content, like a menu card.
 */
@Component
public class RecipeCatalog {

    public static final String RECIPE = "RECIPE";
    public static final String KIT = "KIT";

    /** How much of a product one recipe needs. base is "g", "ml", "pcs" or "bunch". */
    public record Ingredient(String productName, double amount, String base, boolean pantryStaple) {
    }

    public record Recipe(String id, String type, String name, String emoji, String description,
                         String time, int baseServings, List<Ingredient> ingredients) {
    }

    private static Ingredient need(String product, double amount, String base) {
        return new Ingredient(product, amount, base, false);
    }

    /** Things most kitchens already have (salt, oil...). The page lets you skip them. */
    private static Ingredient pantry(String product, double amount, String base) {
        return new Ingredient(product, amount, base, true);
    }

    private final List<Recipe> recipes = List.of(
            new Recipe("sambar", RECIPE, "Sambar", "🍲",
                    "South Indian lentil and vegetable stew, perfect with rice or idli.", "40 min", 4, List.of(
                    need("Toor Dal", 200, "g"),
                    need("Tomatoes", 200, "g"),
                    need("Onions", 150, "g"),
                    need("Carrots", 150, "g"),
                    need("Potatoes", 200, "g"),
                    need("Tamarind", 30, "g"),
                    need("Sambar Powder", 30, "g"),
                    need("Coriander Leaves", 0.5, "bunch"),
                    pantry("Mustard Seeds", 5, "g"),
                    pantry("Sunflower Cooking Oil", 30, "ml"),
                    pantry("Iodized Salt", 20, "g"))),
            new Recipe("veg-pulao", RECIPE, "Vegetable Pulao", "🍛",
                    "Fragrant basmati rice cooked with vegetables and ghee.", "35 min", 4, List.of(
                    need("Basmati Rice", 400, "g"),
                    need("Carrots", 150, "g"),
                    need("Potatoes", 150, "g"),
                    need("Onions", 150, "g"),
                    need("Green Chillies", 20, "g"),
                    need("Ginger", 20, "g"),
                    need("Ghee", 40, "ml"),
                    need("Coriander Leaves", 0.5, "bunch"),
                    pantry("Iodized Salt", 15, "g"))),
            new Recipe("palak-paneer", RECIPE, "Palak Paneer", "🥬",
                    "Soft paneer cubes in a smooth spinach gravy.", "30 min", 4, List.of(
                    need("Spinach Bunch", 2, "bunch"),
                    need("Paneer", 400, "g"),
                    need("Onions", 150, "g"),
                    need("Tomatoes", 200, "g"),
                    need("Ginger", 20, "g"),
                    need("Green Chillies", 20, "g"),
                    need("Ghee", 30, "ml"),
                    pantry("Iodized Salt", 10, "g"))),
            new Recipe("masala-omelette", RECIPE, "Masala Omelette & Toast", "🍳",
                    "A quick protein breakfast: spicy omelette with toasted bread.", "15 min", 4, List.of(
                    need("Farm Eggs", 8, "pcs"),
                    need("Whole Wheat Bread", 400, "g"),
                    need("Onions", 100, "g"),
                    need("Tomatoes", 100, "g"),
                    need("Green Chillies", 10, "g"),
                    need("Coriander Leaves", 0.5, "bunch"),
                    pantry("Sunflower Cooking Oil", 20, "ml"),
                    pantry("Iodized Salt", 5, "g"))),
            new Recipe("rava-upma", RECIPE, "Rava Upma", "🥣",
                    "Light, savoury semolina breakfast with a squeeze of lemon.", "20 min", 4, List.of(
                    need("Bombay Rava", 250, "g"),
                    need("Onions", 100, "g"),
                    need("Green Chillies", 20, "g"),
                    need("Ginger", 15, "g"),
                    need("Lemons", 2, "pcs"),
                    pantry("Mustard Seeds", 5, "g"),
                    pantry("Sunflower Cooking Oil", 30, "ml"),
                    pantry("Iodized Salt", 10, "g"))),
            new Recipe("fruit-yogurt-bowl", RECIPE, "Fruit & Yogurt Bowl", "🍌",
                    "No-cook healthy snack with banana, apple, yogurt and nuts.", "5 min", 2, List.of(
                    need("Fresh Bananas", 4, "pcs"),
                    need("Red Apples", 300, "g"),
                    need("Greek Yogurt", 400, "g"),
                    need("Mixed Nuts", 50, "g"))),

            // ---------------- Festival kits ----------------
            new Recipe("pongal-kit", KIT, "Pongal / Sankranti Kit", "🌾",
                    "Everything for sakkarai pongal and the festival pooja.", "Festival", 4, List.of(
                    need("Sona Masoori Rice", 500, "g"),
                    need("Moong Dal", 200, "g"),
                    need("Jaggery", 500, "g"),
                    need("Ghee", 200, "ml"),
                    need("Cashews", 100, "g"),
                    need("Cardamom", 10, "g"),
                    need("Fresh Coconut", 1, "pcs"),
                    need("Mango Leaves", 1, "bunch"),
                    need("Turmeric Powder", 50, "g"),
                    need("Clay Diyas", 12, "pcs"))),
            new Recipe("diwali-kit", KIT, "Diwali Kit", "🪔",
                    "Diyas, flowers and the basics for home-made sweets.", "Festival", 4, List.of(
                    need("Clay Diyas", 24, "pcs"),
                    need("Ghee", 500, "ml"),
                    need("Sugar", 1000, "g"),
                    need("Bombay Rava", 500, "g"),
                    need("Cashews", 200, "g"),
                    need("Cardamom", 20, "g"),
                    need("Marigold Flowers", 500, "g"),
                    need("Agarbatti", 1, "pcs"),
                    need("Sunflower Cooking Oil", 1000, "ml"))),
            new Recipe("ugadi-kit", KIT, "Ugadi Kit", "🍃",
                    "Mango leaf thoranam, bevu-bella basics and pooja items.", "Festival", 4, List.of(
                    need("Mango Leaves", 2, "bunch"),
                    need("Jaggery", 250, "g"),
                    need("Tamarind", 100, "g"),
                    need("Fresh Coconut", 2, "pcs"),
                    need("Marigold Flowers", 250, "g"),
                    need("Agarbatti", 1, "pcs"),
                    need("Sona Masoori Rice", 1000, "g"))),
            new Recipe("ganesh-kit", KIT, "Ganesh Chaturthi Kit", "🥥",
                    "Modak basics, fruits and pooja essentials.", "Festival", 4, List.of(
                    need("Fresh Coconut", 2, "pcs"),
                    need("Jaggery", 500, "g"),
                    need("Ghee", 200, "ml"),
                    need("Cardamom", 10, "g"),
                    need("Fresh Bananas", 12, "pcs"),
                    need("Marigold Flowers", 250, "g"),
                    need("Agarbatti", 1, "pcs"),
                    need("Camphor", 50, "g")))
    );

    public List<Recipe> all() {
        return recipes;
    }

    public Optional<Recipe> find(String id) {
        return recipes.stream().filter(r -> r.id().equals(id)).findFirst();
    }
}
