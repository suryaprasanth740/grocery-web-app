package com.suryaprasanth.grocery.config;

import com.suryaprasanth.grocery.model.*;
import com.suryaprasanth.grocery.repository.CategoryRepository;
import com.suryaprasanth.grocery.repository.ProductRepository;
import com.suryaprasanth.grocery.repository.ShopRepository;
import com.suryaprasanth.grocery.repository.UserRepository;
import com.suryaprasanth.grocery.util.EmailUtil;
import com.suryaprasanth.grocery.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Demo data for the marketplace: three more neighbourhood shops in Bengaluru, each with its own
 * products, so buyers can see a basket split across shops. Also points every demo product at its
 * bundled photo in /img/products/ (photos and their licences: img/products/credits.json).
 *
 * Safe on a live database: shops and products are only added when missing, never changed.
 * Turned off in the API tests (app.demo.marketplace-shops=false) so tests control their own data.
 */
@Component
public class MarketplaceDemoData {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceDemoData.class);

    /** Shops whose products may get the bundled photos (never a real seller's shop). */
    public static final Set<String> DEMO_SLUGS = Set.of("nammabasket-demo-kirana", "hasiru-tarkari-angadi",
            "sri-lakshmi-provisions", "gokula-dairy-bakery");

    private record DemoShop(String slug, String name, ShopCategory category, String owner, String email,
                            String address, String pincode, double lat, double lng, LocalTime open, LocalTime close,
                            String fee, String freeAbove, String minOrder, String gstin, int slotCapacity) {
    }

    private record DemoProduct(String shop, String name, String description, String price, String mrp, String unit,
                               String emoji, int stock, String category, int shelfLifeDays, int gst) {
    }

    private static final List<DemoShop> SHOPS = List.of(
            new DemoShop("hasiru-tarkari-angadi", "Hasiru Tarkari Angadi", ShopCategory.FRUITS_VEGETABLES,
                    "Manjunath R", "hasiru.tarkari@demo.nammabasket.in", "14, 8th Cross, Malleshwaram, Bengaluru",
                    "560003", 13.0035, 77.5709, LocalTime.of(6, 0), LocalTime.of(21, 0), "25.00", "300.00", "100.00",
                    null, 8),
            new DemoShop("sri-lakshmi-provisions", "Sri Lakshmi Provisions", ShopCategory.KIRANA,
                    "Lakshmi Narayana", "lakshmi.provisions@demo.nammabasket.in", "221, 9th Main, Jayanagar 4th Block, Bengaluru",
                    "560041", 12.9250, 77.5938, LocalTime.of(8, 0), LocalTime.of(22, 0), "30.00", "600.00", "0.00",
                    "29AAPFL1234K1Z5", 10),
            new DemoShop("gokula-dairy-bakery", "Gokula Dairy & Bakery", ShopCategory.BAKERY,
                    "Gopal Krishna", "gokula.bakery@demo.nammabasket.in", "3, 100 Feet Road, Indiranagar, Bengaluru",
                    "560038", 12.9784, 77.6408, LocalTime.of(7, 0), LocalTime.of(21, 0), "20.00", "250.00", "0.00",
                    null, 12));

    private static final String VEG = "Fruits & Vegetables";
    private static final String DAIRY = "Dairy & Eggs";
    private static final String STAPLES = "Staples";
    private static final String SNACKS = "Snacks";
    private static final String BAKERY = "Bakery";
    private static final String BEVERAGES = "Beverages";
    private static final String H = "hasiru-tarkari-angadi";
    private static final String L = "sri-lakshmi-provisions";
    private static final String G = "gokula-dairy-bakery";

    private static final List<DemoProduct> PRODUCTS = List.of(
            new DemoProduct(H, "Brinjal", "Purple brinjal for sambar and ennegai", "40.00", null, "500 g", "🍆", 40, VEG, 5, 0),
            new DemoProduct(H, "Lady's Finger", "Tender bhindi, picked this morning", "30.00", null, "250 g", "🥒", 40, VEG, 4, 0),
            new DemoProduct(H, "French Beans", "Crisp beans for palya", "35.00", null, "250 g", "🫘", 40, VEG, 5, 0),
            new DemoProduct(H, "Cauliflower", "Medium white cauliflower", "45.00", null, "1 pc", "🥦", 30, VEG, 5, 0),
            new DemoProduct(H, "Cabbage", "Fresh green cabbage", "30.00", null, "1 pc", "🥬", 30, VEG, 10, 0),
            new DemoProduct(H, "Cucumber", "Cool salad cucumbers", "25.00", null, "500 g", "🥒", 40, VEG, 6, 0),
            new DemoProduct(H, "Beetroot", "Sweet red beetroot", "35.00", null, "500 g", "🟣", 30, VEG, 14, 0),
            new DemoProduct(H, "Garlic", "Strong country garlic", "60.00", null, "250 g", "🧄", 40, VEG, 60, 0),
            new DemoProduct(H, "Curry Leaves", "Fresh karibevu for tadka", "10.00", null, "1 bunch", "🌿", 50, VEG, 4, 0),
            new DemoProduct(H, "Mint Leaves", "Pudina for chutney and biryani", "15.00", null, "1 bunch", "🌿", 40, VEG, 3, 0),
            new DemoProduct(H, "Drumsticks", "Nuggekai for sambar", "30.00", null, "250 g", "🥢", 30, VEG, 5, 0),
            new DemoProduct(H, "Alphonso Mangoes", "Sweet Ratnagiri Alphonso, carbide-free", "450.00", "520.00", "1 dozen", "🥭", 20, VEG, 6, 0),
            new DemoProduct(H, "Pomegranate", "Bhagwa pomegranates", "160.00", null, "1 kg", "🔴", 25, VEG, 20, 0),
            new DemoProduct(H, "Papaya", "Ripe papaya, ready to eat", "50.00", null, "1 pc", "🧡", 20, VEG, 4, 0),
            new DemoProduct(H, "Guava", "Crunchy pink guava", "70.00", null, "500 g", "🍏", 30, VEG, 6, 0),
            new DemoProduct(H, "Green Grapes", "Seedless green grapes", "90.00", null, "500 g", "🍇", 25, VEG, 6, 0),
            new DemoProduct(H, "Watermelon", "Kiran watermelon, about 2 kg", "60.00", null, "1 pc", "🍉", 15, VEG, 8, 0),
            new DemoProduct(H, "Sweet Corn", "Sweet corn cobs", "40.00", null, "2 pcs", "🌽", 30, VEG, 4, 0),

            new DemoProduct(L, "Ragi Flour", "Stone-ground finger millet flour for mudde and dosa", "65.00", "75.00", "1 kg", "🌾", 40, STAPLES, 120, 0),
            new DemoProduct(L, "Ragi Malt", "Sprouted ragi drink mix - stir into hot milk", "120.00", "135.00", "500 g", "🥣", 30, BEVERAGES, 180, 5),
            new DemoProduct(L, "Idli Rice", "Parboiled rice for soft idlis", "60.00", null, "1 kg", "🍚", 40, STAPLES, 365, 5),
            new DemoProduct(L, "Urad Dal", "Split white urad dal", "140.00", "160.00", "1 kg", "🫘", 40, STAPLES, 365, 5),
            new DemoProduct(L, "Chana Dal", "Split Bengal gram", "95.00", null, "1 kg", "🫘", 40, STAPLES, 365, 5),
            new DemoProduct(L, "Rajma", "Red kidney beans", "75.00", null, "500 g", "🫘", 30, STAPLES, 365, 5),
            new DemoProduct(L, "Poha", "Thick avalakki for breakfast", "45.00", null, "500 g", "🍚", 40, STAPLES, 180, 5),
            new DemoProduct(L, "Besan", "Fine gram flour for bajji and laddu", "55.00", null, "500 g", "🌾", 40, STAPLES, 180, 5),
            new DemoProduct(L, "Groundnut Oil", "Cold-pressed (ganaga) groundnut oil", "240.00", "270.00", "1 L", "🥜", 30, STAPLES, 270, 5),
            new DemoProduct(L, "Cumin Seeds", "Jeera", "60.00", null, "100 g", "🌱", 40, STAPLES, 365, 5),
            new DemoProduct(L, "Red Chilli Powder", "Byadgi chilli powder, bright colour, mild heat", "70.00", null, "200 g", "🌶️", 40, STAPLES, 240, 5),
            new DemoProduct(L, "Black Pepper", "Whole Malabar peppercorns", "90.00", null, "100 g", "⚫", 30, STAPLES, 365, 5),
            new DemoProduct(L, "Coconut Oil", "Pure coconut oil for cooking", "190.00", null, "500 ml", "🥥", 30, STAPLES, 365, 5),
            new DemoProduct(L, "Vermicelli", "Roasted semiya for payasam and upma", "35.00", null, "400 g", "🍜", 40, STAPLES, 240, 18),
            new DemoProduct(L, "Rock Salt", "Saindhava lavana", "40.00", null, "500 g", "🧂", 30, STAPLES, -1, 0),

            new DemoProduct(G, "Butter", "Fresh salted butter", "56.00", null, "100 g", "🧈", 30, DAIRY, 60, 12),
            new DemoProduct(G, "Buttermilk", "Spiced majjige with curry leaves", "15.00", null, "200 ml", "🥛", 40, BEVERAGES, 2, 0),
            new DemoProduct(G, "Idli Dosa Batter", "Fresh-ground batter, makes about 25 idlis", "70.00", null, "1 kg", "🥣", 25, DAIRY, 3, 5),
            new DemoProduct(G, "Milk Bread", "Soft white sandwich loaf", "40.00", null, "400 g", "🍞", 30, BAKERY, 4, 0),
            new DemoProduct(G, "Rusk", "Crunchy tea-time rusk", "45.00", null, "300 g", "🍞", 40, BAKERY, 120, 5),
            new DemoProduct(G, "Nankhatai", "Ghee and cardamom tea-time cookies", "60.00", null, "200 g", "🍪", 30, BAKERY, 30, 18),
            new DemoProduct(G, "Banana Chips", "Kerala-style chips fried in coconut oil", "80.00", null, "200 g", "🍌", 40, SNACKS, 60, 12),
            new DemoProduct(G, "Murukku", "Crunchy rice murukku", "60.00", null, "200 g", "🥨", 40, SNACKS, 45, 12),
            new DemoProduct(G, "Mysore Pak", "Soft ghee Mysore pak", "180.00", "200.00", "250 g", "🟨", 25, SNACKS, 15, 5),
            new DemoProduct(G, "Peanut Chikki", "Crunchy groundnut and jaggery bars", "50.00", null, "200 g", "🥜", 30, SNACKS, 30, 5),
            new DemoProduct(G, "Mixture Namkeen", "Spicy Bengaluru mixture", "55.00", null, "200 g", "🥨", 40, SNACKS, 60, 12),
            new DemoProduct(G, "Bun", "Soft sweet buns", "30.00", null, "pack of 4", "🍞", 30, BAKERY, 3, 0),
            new DemoProduct(G, "Coconut Cookies", "Bakery-fresh coconut biscuits", "70.00", null, "250 g", "🍪", 30, BAKERY, 30, 18));

    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final boolean enabled;
    private final String demoSellerPassword;

    public MarketplaceDemoData(ShopRepository shopRepository, ProductRepository productRepository,
                               CategoryRepository categoryRepository, UserRepository userRepository,
                               @Value("${app.demo.marketplace-shops:true}") boolean enabled,
                               @Value("${app.demo.seller-password:}") String demoSellerPassword) {
        this.shopRepository = shopRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.enabled = enabled;
        this.demoSellerPassword = demoSellerPassword;
    }

    /** "Lady's Finger" -> "lady-s-finger": the photo file name of a product. */
    public static String photoSlug(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    }

    /** "/img/products/<slug>.jpg" when that bundled photo exists, otherwise null. */
    public static String bundledPhoto(String productName) {
        String path = "img/products/" + photoSlug(productName) + ".jpg";
        return new ClassPathResource("static/" + path).exists() ? "/" + path : null;
    }

    public void run() {
        if (enabled) {
            for (DemoShop d : SHOPS) {
                ensureShop(d);
            }
        }
        usePhotos();
    }

    private void ensureShop(DemoShop d) {
        Shop shop = shopRepository.findBySlug(d.slug()).orElse(null);
        if (shop == null) {
            User owner = userRepository.findByEmailIgnoreCase(d.email()).orElseGet(() -> {
                User u = new User(d.owner(), d.email(), PasswordUtil.hash(password()));
                u.setRole(Role.SELLER);
                u.setEmailKey(EmailUtil.canonical(d.email()));
                return userRepository.save(u);
            });
            shop = new Shop();
            shop.setOwner(owner);
            shop.setName(d.name());
            shop.setSlug(d.slug());
            shop.setCategory(d.category());
            shop.setPhone("9000000001");
            shop.setAddressLine(d.address());
            shop.setPincode(d.pincode());
            shop.setServicePincodes(d.pincode() + ",560xxx");
            shop.setOpenTime(d.open());
            shop.setCloseTime(d.close());
            shop.setDeliveryFee(new BigDecimal(d.fee()));
            shop.setFreeDeliveryAbove(new BigDecimal(d.freeAbove()));
            shop.setMinOrderAmount(new BigDecimal(d.minOrder()));
            shop.setLatitude(d.lat());
            shop.setLongitude(d.lng());
            shop.setGstin(d.gstin());
            shop.setSlotsEnabled(true);
            shop.setSlotCapacity(d.slotCapacity());
            shop.setStatus(ShopStatus.APPROVED);
            shop.setApprovedAt(LocalDateTime.now());
            shop = shopRepository.save(shop);
            log.info("Created demo shop {}", d.name());
        }
        for (DemoProduct p : PRODUCTS) {
            if (!p.shop().equals(d.slug()) || productRepository.existsByNameIgnoreCase(p.name())) {
                continue;
            }
            Category category = categoryRepository.findByNameIgnoreCase(p.category())
                    .orElseGet(() -> categoryRepository.save(new Category(p.category(), "🛒")));
            Product product = new Product(p.name(), p.description(), new BigDecimal(p.price()), p.unit(), p.emoji(),
                    bundledPhoto(p.name()), p.stock(), category);
            product.setShop(shop);
            if (p.mrp() != null) {
                product.setMrp(new BigDecimal(p.mrp()));
            }
            if (p.shelfLifeDays() > 0) {
                product.setExpiryDate(LocalDate.now().plusDays(p.shelfLifeDays()));
            }
            product.setGstPercent(p.gst());
            productRepository.save(product);
        }
    }

    /**
     * Demo products get their bundled photo when they have none, or still use one of the old
     * Wikimedia links (those were hot-linked and could break). Sellers' own photos are never touched.
     */
    private void usePhotos() {
        int changed = 0;
        for (Product p : productRepository.findAll()) {
            if (p.getShop() == null || !DEMO_SLUGS.contains(p.getShop().getSlug())) {
                continue;
            }
            String current = p.getImageUrl();
            boolean replaceable = current == null || current.isBlank() || current.contains("wikimedia.org/");
            String photo = bundledPhoto(p.getName());
            if (replaceable && photo != null && !photo.equals(current)) {
                p.setImageUrl(photo);
                productRepository.save(p);
                changed++;
            }
        }
        if (changed > 0) {
            log.info("Gave {} demo product(s) their bundled photo", changed);
        }
    }

    /** DEMO_SELLER_PASSWORD lets you log in as the demo shops; without it they get an unguessable one. */
    private String password() {
        if (demoSellerPassword != null && !demoSellerPassword.isBlank()) {
            return demoSellerPassword;
        }
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
