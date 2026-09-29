package com.suryaprasanth.grocery.config;

import com.suryaprasanth.grocery.model.Category;
import com.suryaprasanth.grocery.model.Coupon;
import com.suryaprasanth.grocery.model.CouponType;
import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.util.EmailUtil;
import com.suryaprasanth.grocery.repository.CategoryRepository;
import com.suryaprasanth.grocery.repository.CouponRepository;
import com.suryaprasanth.grocery.repository.ProductRepository;
import com.suryaprasanth.grocery.repository.UserRepository;
import com.suryaprasanth.grocery.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CouponRepository couponRepository;
    private final UserRepository userRepository;
    private final String adminEmail;
    private final String adminPassword;
    private final boolean refreshExpiredDemoStock;

    public DataInitializer(CategoryRepository categoryRepository, ProductRepository productRepository,
                           CouponRepository couponRepository, UserRepository userRepository,
                           @Value("${app.admin.email:admin@freshcart.com}") String adminEmail,
                           @Value("${app.admin.password:}") String adminPassword,
                           @Value("${app.demo.refresh-expired-stock:true}") boolean refreshExpiredDemoStock) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.couponRepository = couponRepository;
        this.userRepository = userRepository;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.refreshExpiredDemoStock = refreshExpiredDemoStock;
    }

    @Override
    public void run(String... args) {
        if (categoryRepository.count() == 0) {
            seed();
        }
        backfillProductImages();
        backfillShelfLifeAndGst();
        if (couponRepository.count() == 0) {
            seedCoupons();
        }
        ensureAdminAccount();
        backfillEmailKeys();
    }

    /** Accounts made before the one-account-per-Gmail rule get their email key here. */
    private void backfillEmailKeys() {
        for (User user : userRepository.findByEmailKeyIsNull()) {
            String key = EmailUtil.canonical(user.getEmail());
            if (userRepository.existsByEmailKey(key)) {
                // An older duplicate (e.g. s.urya@gmail.com next to surya@gmail.com). Keep it
                // so its orders are not lost; it can still log in with its exact email.
                log.warn("User {} shares an inbox with another account; left without email key", user.getId());
                continue;
            }
            user.setEmailKey(key);
            userRepository.saveAndFlush(user);
        }
    }

    /**
     * Shelf life (days) and GST rate for each seeded product. GST rates are approximate
     * values for a demo. A shelf life of -1 means "does not expire".
     */
    private static final Map<String, int[]> SHELF_LIFE_AND_GST = Map.ofEntries(
            Map.entry("Fresh Bananas", new int[]{5, 0}),
            Map.entry("Red Apples", new int[]{20, 0}),
            Map.entry("Tomatoes", new int[]{7, 0}),
            Map.entry("Onions", new int[]{30, 0}),
            Map.entry("Spinach Bunch", new int[]{3, 0}),
            Map.entry("Full Cream Milk", new int[]{3, 0}),
            Map.entry("Farm Eggs", new int[]{14, 0}),
            Map.entry("Cheddar Cheese", new int[]{60, 12}),
            Map.entry("Greek Yogurt", new int[]{10, 5}),
            Map.entry("Whole Wheat Bread", new int[]{4, 0}),
            Map.entry("Butter Croissants", new int[]{3, 18}),
            Map.entry("Chocolate Muffins", new int[]{5, 18}),
            Map.entry("Orange Juice", new int[]{7, 12}),
            Map.entry("Filter Coffee Powder", new int[]{180, 5}),
            Map.entry("Green Tea Bags", new int[]{365, 5}),
            Map.entry("Potato Chips", new int[]{120, 12}),
            Map.entry("Mixed Nuts", new int[]{180, 5}),
            Map.entry("Digestive Biscuits", new int[]{180, 18}),
            Map.entry("Basmati Rice", new int[]{365, 5}),
            Map.entry("Toor Dal", new int[]{365, 5}),
            Map.entry("Sunflower Cooking Oil", new int[]{270, 5}),
            Map.entry("Iodized Salt", new int[]{-1, 0}));

    /**
     * Gives the seeded products an expiry date and GST rate if they don't have one yet.
     * Demo helper: on the free live site nobody restocks, so milk would stay "expired"
     * forever. When app.demo.refresh-expired-stock=true, an expired seeded product gets a
     * fresh batch on start-up (like new stock arriving). Set it to false to test expiry.
     */
    private void backfillShelfLifeAndGst() {
        LocalDate today = LocalDate.now();
        for (Product p : productRepository.findAll()) {
            int[] info = SHELF_LIFE_AND_GST.get(p.getName());
            if (info == null) {
                continue;
            }
            boolean changed = false;
            boolean expired = p.getExpiryDate() != null && p.isExpired();
            if (info[0] > 0 && (p.getExpiryDate() == null || (expired && refreshExpiredDemoStock))) {
                p.setExpiryDate(today.plusDays(info[0]));
                changed = true;
            }
            if (p.getGstPercent() == 0 && info[1] > 0) {
                p.setGstPercent(info[1]);
                changed = true;
            }
            if (changed) {
                productRepository.save(p);
            }
        }
    }

    private void seedCoupons() {
        LocalDateTime now = LocalDateTime.now();
        couponRepository.save(new Coupon("FRESH50", "Flat Rs 50 off on orders above Rs 299",
                CouponType.FLAT, new BigDecimal("50"), null, new BigDecimal("299"), now.plusYears(1), 1));
        couponRepository.save(new Coupon("SAVE10", "10% off up to Rs 100 on orders above Rs 199",
                CouponType.PERCENT, new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("199"),
                now.plusYears(1), 3));
        couponRepository.save(new Coupon("BIG100", "Flat Rs 100 off on orders above Rs 999",
                CouponType.FLAT, new BigDecimal("100"), null, new BigDecimal("999"), now.plusYears(1), 1));
        // For testing: this one is already expired and must always be rejected.
        couponRepository.save(new Coupon("OLD20", "Expired offer (for testing)",
                CouponType.PERCENT, new BigDecimal("20"), new BigDecimal("80"), BigDecimal.ZERO,
                now.minusDays(1), 1));
    }

    /** Creates the admin account once. The password comes from ADMIN_PASSWORD, or is random. */
    private void ensureAdminAccount() {
        if (userRepository.existsByEmailIgnoreCase(adminEmail)) {
            return;
        }
        String password = adminPassword;
        if (password == null || password.isBlank()) {
            byte[] bytes = new byte[9];
            new SecureRandom().nextBytes(bytes);
            password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            log.warn("============================================================");
            log.warn(" ADMIN_PASSWORD is not set. Created admin {} with password: {}", adminEmail, password);
            log.warn(" Set ADMIN_PASSWORD in your environment to choose your own.");
            log.warn("============================================================");
        }
        User admin = new User("FreshCart Admin", adminEmail.trim().toLowerCase(), PasswordUtil.hash(password));
        admin.setRole("ADMIN");
        userRepository.save(admin);
    }

    private void seed() {
        Category fruitsVeg = categoryRepository.save(new Category("Fruits & Vegetables", "🥦"));
        Category dairy = categoryRepository.save(new Category("Dairy & Eggs", "🥚"));
        Category bakery = categoryRepository.save(new Category("Bakery", "🍞"));
        Category beverages = categoryRepository.save(new Category("Beverages", "🧃"));
        Category snacks = categoryRepository.save(new Category("Snacks", "🍿"));
        Category staples = categoryRepository.save(new Category("Staples", "🍚"));

        Map<String, String> images = productImages();

        productRepository.save(new Product("Fresh Bananas", "Naturally ripened, sweet bananas",
                new BigDecimal("49.00"), "1 dozen", "🍌", images.get("Fresh Bananas"), 50, fruitsVeg));
        productRepository.save(new Product("Red Apples", "Crisp and juicy Shimla apples",
                new BigDecimal("180.00"), "1 kg", "🍎", images.get("Red Apples"), 40, fruitsVeg));
        productRepository.save(new Product("Tomatoes", "Farm-fresh red tomatoes",
                new BigDecimal("40.00"), "1 kg", "🍅", images.get("Tomatoes"), 60, fruitsVeg));
        productRepository.save(new Product("Onions", "Everyday cooking onions",
                new BigDecimal("35.00"), "1 kg", "🧅", images.get("Onions"), 70, fruitsVeg));
        productRepository.save(new Product("Spinach Bunch", "Freshly harvested spinach leaves",
                new BigDecimal("25.00"), "1 bunch", "🥬", images.get("Spinach Bunch"), 30, fruitsVeg));

        productRepository.save(new Product("Full Cream Milk", "Pasteurized, homogenized milk",
                new BigDecimal("32.00"), "500 ml", "🥛", images.get("Full Cream Milk"), 80, dairy));
        productRepository.save(new Product("Farm Eggs", "Fresh brown eggs, protein-rich",
                new BigDecimal("84.00"), "12 pcs", "🥚", images.get("Farm Eggs"), 45, dairy));
        productRepository.save(new Product("Cheddar Cheese", "Aged, sharp cheddar block",
                new BigDecimal("210.00"), "200 g", "🧀", images.get("Cheddar Cheese"), 25, dairy));
        productRepository.save(new Product("Greek Yogurt", "Thick, high-protein yogurt",
                new BigDecimal("65.00"), "400 g", "🥛", images.get("Greek Yogurt"), 35, dairy));

        productRepository.save(new Product("Whole Wheat Bread", "Soft multigrain loaf",
                new BigDecimal("45.00"), "400 g", "🍞", images.get("Whole Wheat Bread"), 30, bakery));
        productRepository.save(new Product("Butter Croissants", "Flaky, buttery croissants",
                new BigDecimal("120.00"), "pack of 4", "🥐", images.get("Butter Croissants"), 20, bakery));
        productRepository.save(new Product("Chocolate Muffins", "Rich chocolate chip muffins",
                new BigDecimal("99.00"), "pack of 4", "🧁", images.get("Chocolate Muffins"), 25, bakery));

        productRepository.save(new Product("Orange Juice", "100% fresh-squeezed orange juice",
                new BigDecimal("110.00"), "1 L", "🧃", images.get("Orange Juice"), 30, beverages));
        productRepository.save(new Product("Filter Coffee Powder", "South Indian style filter coffee",
                new BigDecimal("175.00"), "200 g", "☕", images.get("Filter Coffee Powder"), 40, beverages));
        productRepository.save(new Product("Green Tea Bags", "Antioxidant-rich green tea",
                new BigDecimal("140.00"), "25 bags", "🍵", images.get("Green Tea Bags"), 35, beverages));

        productRepository.save(new Product("Potato Chips", "Classic salted potato chips",
                new BigDecimal("30.00"), "70 g", "🥔", images.get("Potato Chips"), 60, snacks));
        productRepository.save(new Product("Mixed Nuts", "Roasted almonds, cashews & pistachios",
                new BigDecimal("299.00"), "250 g", "🥜", images.get("Mixed Nuts"), 22, snacks));
        productRepository.save(new Product("Digestive Biscuits", "Whole wheat digestive biscuits",
                new BigDecimal("55.00"), "250 g", "🍪", images.get("Digestive Biscuits"), 45, snacks));

        productRepository.save(new Product("Basmati Rice", "Long-grain aromatic basmati rice",
                new BigDecimal("165.00"), "1 kg", "🍚", images.get("Basmati Rice"), 50, staples));
        productRepository.save(new Product("Toor Dal", "Premium quality split pigeon peas",
                new BigDecimal("140.00"), "1 kg", "🫘", images.get("Toor Dal"), 40, staples));
        productRepository.save(new Product("Sunflower Cooking Oil", "Refined sunflower oil",
                new BigDecimal("155.00"), "1 L", "🋙", images.get("Sunflower Cooking Oil"), 35, staples));
        productRepository.save(new Product("Iodized Salt", "Everyday table salt",
                new BigDecimal("22.00"), "1 kg", "🧂", images.get("Iodized Salt"), 60, staples));
    }

    /**
     * Runs on every startup. Fills in imageUrl for products that don't have one yet
     * (matched by name) without touching anything else - safe to run against a
     * database that was already seeded by an earlier version of this app.
     */
    private void backfillProductImages() {
        Map<String, String> images = productImages();
        for (Product p : productRepository.findAll()) {
            if ((p.getImageUrl() == null || p.getImageUrl().isBlank()) && images.containsKey(p.getName())) {
                p.setImageUrl(images.get(p.getName()));
                productRepository.save(p);
            }
        }
    }

    /**
     * Real product photos (Wikimedia Commons, freely licensed), keyed by product name.
     * Iodized Salt has no entry on purpose - the storefront falls back to its emoji icon
     * for that one, since a clean photo of plain table salt wasn't available.
     */
    private Map<String, String> productImages() {
        Map<String, String> m = new HashMap<>();
        m.put("Fresh Bananas", "https://thumb.wikimedia.org/wikipedia/commons/thumb/4/45/Banana_bunch_in_a_banana_farm_at_Chinawal.jpg/500px-Banana_bunch_in_a_banana_farm_at_Chinawal.jpg");
        m.put("Red Apples", "https://thumb.wikimedia.org/wikipedia/commons/thumb/1/15/Red_Apple.jpg/500px-Red_Apple.jpg");
        m.put("Tomatoes", "https://thumb.wikimedia.org/wikipedia/commons/thumb/8/89/Tomato_je.jpg/500px-Tomato_je.jpg");
        m.put("Onions", "https://thumb.wikimedia.org/wikipedia/commons/thumb/2/20/Harvested_vegetables%28Onions%29.jpg/500px-Harvested_vegetables%28Onions%29.jpg");
        m.put("Spinach Bunch", "https://thumb.wikimedia.org/wikipedia/commons/thumb/f/fe/Spinach_leaves.jpg/500px-Spinach_leaves.jpg");
        m.put("Full Cream Milk", "https://thumb.wikimedia.org/wikipedia/commons/thumb/f/f2/Glass_Milk_Bottles.tif/lossy-page1-500px-Glass_Milk_Bottles.tif.jpg");
        m.put("Farm Eggs", "https://thumb.wikimedia.org/wikipedia/commons/thumb/8/83/Egg_cartons_with_chicken_eggs_03.jpg/500px-Egg_cartons_with_chicken_eggs_03.jpg");
        m.put("Cheddar Cheese", "https://thumb.wikimedia.org/wikipedia/commons/thumb/2/2e/Montgomerys_cheddar_cheese.jpg/500px-Montgomerys_cheddar_cheese.jpg");
        m.put("Greek Yogurt", "https://thumb.wikimedia.org/wikipedia/commons/thumb/e/e7/A_bowl_of_frozen_yogurt.jpg/500px-A_bowl_of_frozen_yogurt.jpg");
        m.put("Whole Wheat Bread", "https://thumb.wikimedia.org/wikipedia/commons/thumb/7/79/Vegan_no-knead_whole_wheat_bread_loaf%2C_sliced%2C_September_2010.jpg/500px-Vegan_no-knead_whole_wheat_bread_loaf%2C_sliced%2C_September_2010.jpg");
        m.put("Butter Croissants", "https://thumb.wikimedia.org/wikipedia/commons/thumb/d/dc/Croissants_au_beurre_%2818953292873%29.jpg/500px-Croissants_au_beurre_%2818953292873%29.jpg");
        m.put("Chocolate Muffins", "https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c2/Vegan_oatmeal_chocolate_chip_muffins.jpg/500px-Vegan_oatmeal_chocolate_chip_muffins.jpg");
        m.put("Orange Juice", "https://thumb.wikimedia.org/wikipedia/commons/thumb/6/67/Orange_juice_1_edit1.jpg/500px-Orange_juice_1_edit1.jpg");
        m.put("Filter Coffee Powder", "https://thumb.wikimedia.org/wikipedia/commons/thumb/8/84/Indian_filter_coffee_in_Dabarah.jpg/500px-Indian_filter_coffee_in_Dabarah.jpg");
        m.put("Green Tea Bags", "https://thumb.wikimedia.org/wikipedia/commons/thumb/8/8d/Canister_with_bags_of_green_tea.jpg/500px-Canister_with_bags_of_green_tea.jpg");
        m.put("Potato Chips", "https://thumb.wikimedia.org/wikipedia/commons/thumb/6/69/Potato-Chips.jpg/500px-Potato-Chips.jpg");
        m.put("Mixed Nuts", "https://thumb.wikimedia.org/wikipedia/commons/thumb/3/37/Almonds_-_in_shell%2C_shell_cracked_open%2C_shelled%2C_blanched.jpg/500px-Almonds_-_in_shell%2C_shell_cracked_open%2C_shelled%2C_blanched.jpg");
        m.put("Digestive Biscuits", "https://thumb.wikimedia.org/wikipedia/commons/thumb/4/47/Digestive_biscuits.jpg/500px-Digestive_biscuits.jpg");
        m.put("Basmati Rice", "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/ae/Grano_de_arroz_basmati_integral%2C_2020-06-12%2C_DD_01-11_FS.jpg/500px-Grano_de_arroz_basmati_integral%2C_2020-06-12%2C_DD_01-11_FS.jpg");
        m.put("Toor Dal", "https://thumb.wikimedia.org/wikipedia/commons/thumb/0/0f/Tadka_Daal_%28Indian_lentil_curry%29.jpg/500px-Tadka_Daal_%28Indian_lentil_curry%29.jpg");
        m.put("Sunflower Cooking Oil", "https://thumb.wikimedia.org/wikipedia/commons/thumb/3/33/Bottle_1_liter_Sunflower_refined_oil.jpg/500px-Bottle_1_liter_Sunflower_refined_oil.jpg");
        return m;
    }
}
