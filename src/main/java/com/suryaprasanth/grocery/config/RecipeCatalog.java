package com.suryaprasanth.grocery.config;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
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

    /**
     * Real photos from Wikimedia Commons (all CC BY-SA 4.0). The licence asks us to credit
     * the photographer and link the licence, so the recipe page shows "Photo: name, CC BY-SA 4.0".
     */
    public record Photo(String url, String author, String sourcePage) {
        public static final String LICENSE = "CC BY-SA 4.0";
        public static final String LICENSE_URL = "https://creativecommons.org/licenses/by-sa/4.0/";
    }

    private static final Map<String, Photo> PHOTOS = Map.ofEntries(
            Map.entry("sambar", new Photo(
                    "https://thumb.wikimedia.org/wikipedia/commons/thumb/3/38/Indian_Sambar.jpg/500px-Indian_Sambar.jpg",
                    "Samphotography", "https://commons.wikimedia.org/wiki/File:Indian_Sambar.jpg")),
            Map.entry("veg-pulao", new Photo(
                    "https://thumb.wikimedia.org/wikipedia/commons/thumb/8/82/VEGETABLE_PULAO_~_An_Indian_cuisine_made_from_fried_rice_mixed_with_fried_vegetables.jpg/500px-VEGETABLE_PULAO_~_An_Indian_cuisine_made_from_fried_rice_mixed_with_fried_vegetables.jpg",
                    "Jagisnowjughead", "https://commons.wikimedia.org/wiki/File:VEGETABLE_PULAO_~_An_Indian_cuisine_made_from_fried_rice_mixed_with_fried_vegetables.jpg")),
            Map.entry("palak-paneer", new Photo(
                    "https://thumb.wikimedia.org/wikipedia/commons/thumb/8/8d/Palak_Paneer_%28Cottage_cheese_in_spinach_gravy%29.jpg/500px-Palak_Paneer_%28Cottage_cheese_in_spinach_gravy%29.jpg",
                    "DreamyFlutura11", "https://commons.wikimedia.org/wiki/File:Palak_Paneer_%28Cottage_cheese_in_spinach_gravy%29.jpg")),
            Map.entry("masala-omelette", new Photo(
                    "https://thumb.wikimedia.org/wikipedia/commons/thumb/5/57/Masala_omelette_with_bread_toasties.jpg/500px-Masala_omelette_with_bread_toasties.jpg",
                    "Lillottama", "https://commons.wikimedia.org/wiki/File:Masala_omelette_with_bread_toasties.jpg")),
            Map.entry("rava-upma", new Photo(
                    "https://thumb.wikimedia.org/wikipedia/commons/thumb/0/09/Upma_South_India.JPG/500px-Upma_South_India.JPG",
                    "Intodustin", "https://commons.wikimedia.org/wiki/File:Upma_South_India.JPG")),
            Map.entry("fruit-yogurt-bowl", new Photo(
                    "https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d1/Yogurt_fruit_bowl.jpg/500px-Yogurt_fruit_bowl.jpg",
                    "Jumbocombo0811", "https://commons.wikimedia.org/wiki/File:Yogurt_fruit_bowl.jpg")),
            Map.entry("pongal-kit", new Photo(
                    "https://upload.wikimedia.org/wikipedia/commons/4/43/Pongal_Pot.jpg",
                    "Pranathi Gubbala", "https://commons.wikimedia.org/wiki/File:Pongal_Pot.jpg")),
            Map.entry("diwali-kit", new Photo(
                    "https://thumb.wikimedia.org/wikipedia/commons/thumb/3/3a/Diwali_Diya_4.jpg/500px-Diwali_Diya_4.jpg",
                    "Slyronit", "https://commons.wikimedia.org/wiki/File:Diwali_Diya_4.jpg")),
            Map.entry("ugadi-kit", new Photo(
                    "https://thumb.wikimedia.org/wikipedia/commons/thumb/4/49/Ugadi_festival.jpg/500px-Ugadi_festival.jpg",
                    "Pragnya Aindleni", "https://commons.wikimedia.org/wiki/File:Ugadi_festival.jpg")),
            Map.entry("ganesh-kit", new Photo(
                    "https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d5/Puran_Modak_by_Dr._Raju_Kasambe_DSCN8818_%287%29_01.jpg/500px-Puran_Modak_by_Dr._Raju_Kasambe_DSCN8818_%287%29_01.jpg",
                    "Dr. Raju Kasambe", "https://commons.wikimedia.org/wiki/File:Puran_Modak_by_Dr._Raju_Kasambe_DSCN8818_%287%29_01.jpg")));

    /** The photo for a recipe or kit, or null (the page then shows the emoji). */
    public Photo photoFor(String id) {
        return PHOTOS.get(id);
    }

    public List<Recipe> all() {
        return recipes;
    }

    public Optional<Recipe> find(String id) {
        return recipes.stream().filter(r -> r.id().equals(id)).findFirst();
    }
}
