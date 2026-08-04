package com.suryaprasanth.grocery.config;

import com.suryaprasanth.grocery.model.Category;
import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.repository.CategoryRepository;
import com.suryaprasanth.grocery.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataInitializer implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public DataInitializer(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) {
        if (categoryRepository.count() > 0) {
            return; // already seeded
        }

        Category fruitsVeg = categoryRepository.save(new Category("Fruits & Vegetables", "\uD83E\uDD66"));
        Category dairy = categoryRepository.save(new Category("Dairy & Eggs", "\uD83E\uDD5A"));
        Category bakery = categoryRepository.save(new Category("Bakery", "\uD83C\uDF5E"));
        Category beverages = categoryRepository.save(new Category("Beverages", "\uD83E\uDDC3"));
        Category snacks = categoryRepository.save(new Category("Snacks", "\uD83C\uDF7F"));
        Category staples = categoryRepository.save(new Category("Staples", "\uD83C\uDF5A"));

        productRepository.save(new Product("Fresh Bananas", "Naturally ripened, sweet bananas",
                new BigDecimal("49.00"), "1 dozen", "\uD83C\uDF4C", 50, fruitsVeg));
        productRepository.save(new Product("Red Apples", "Crisp and juicy Shimla apples",
                new BigDecimal("180.00"), "1 kg", "\uD83C\uDF4E", 40, fruitsVeg));
        productRepository.save(new Product("Tomatoes", "Farm-fresh red tomatoes",
                new BigDecimal("40.00"), "1 kg", "\uD83C\uDF45", 60, fruitsVeg));
        productRepository.save(new Product("Onions", "Everyday cooking onions",
                new BigDecimal("35.00"), "1 kg", "\uD83E\uDDC5", 70, fruitsVeg));
        productRepository.save(new Product("Spinach Bunch", "Freshly harvested spinach leaves",
                new BigDecimal("25.00"), "1 bunch", "\uD83E\uDD6C", 30, fruitsVeg));

        productRepository.save(new Product("Full Cream Milk", "Pasteurized, homogenized milk",
                new BigDecimal("32.00"), "500 ml", "\uD83E\uDD5B", 80, dairy));
        productRepository.save(new Product("Farm Eggs", "Fresh brown eggs, protein-rich",
                new BigDecimal("84.00"), "12 pcs", "\uD83E\uDD5A", 45, dairy));
        productRepository.save(new Product("Cheddar Cheese", "Aged, sharp cheddar block",
                new BigDecimal("210.00"), "200 g", "\uD83E\uDDC0", 25, dairy));
        productRepository.save(new Product("Greek Yogurt", "Thick, high-protein yogurt",
                new BigDecimal("65.00"), "400 g", "\uD83E\uDD5B", 35, dairy));

        productRepository.save(new Product("Whole Wheat Bread", "Soft multigrain loaf",
                new BigDecimal("45.00"), "400 g", "\uD83C\uDF5E", 30, bakery));
        productRepository.save(new Product("Butter Croissants", "Flaky, buttery croissants",
                new BigDecimal("120.00"), "pack of 4", "\uD83E\uDD50", 20, bakery));
        productRepository.save(new Product("Chocolate Muffins", "Rich chocolate chip muffins",
                new BigDecimal("99.00"), "pack of 4", "\uD83E\uDDC1", 25, bakery));

        productRepository.save(new Product("Orange Juice", "100% fresh-squeezed orange juice",
                new BigDecimal("110.00"), "1 L", "\uD83E\uDDC3", 30, beverages));
        productRepository.save(new Product("Filter Coffee Powder", "South Indian style filter coffee",
                new BigDecimal("175.00"), "200 g", "\u2615", 40, beverages));
        productRepository.save(new Product("Green Tea Bags", "Antioxidant-rich green tea",
                new BigDecimal("140.00"), "25 bags", "\uD83C\uDF75", 35, beverages));

        productRepository.save(new Product("Potato Chips", "Classic salted potato chips",
                new BigDecimal("30.00"), "70 g", "\uD83E\uDD54", 60, snacks));
        productRepository.save(new Product("Mixed Nuts", "Roasted almonds, cashews & pistachios",
                new BigDecimal("299.00"), "250 g", "\uD83E\uDD5C", 22, snacks));
        productRepository.save(new Product("Digestive Biscuits", "Whole wheat digestive biscuits",
                new BigDecimal("55.00"), "250 g", "\uD83C\uDF6A", 45, snacks));

        productRepository.save(new Product("Basmati Rice", "Long-grain aromatic basmati rice",
                new BigDecimal("165.00"), "1 kg", "\uD83C\uDF5A", 50, staples));
        productRepository.save(new Product("Toor Dal", "Premium quality split pigeon peas",
                new BigDecimal("140.00"), "1 kg", "\uD83E\uDED8", 40, staples));
        productRepository.save(new Product("Sunflower Cooking Oil", "Refined sunflower oil",
                new BigDecimal("155.00"), "1 L", "\uD83C\uDED9", 35, staples));
        productRepository.save(new Product("Iodized Salt", "Everyday table salt",
                new BigDecimal("22.00"), "1 kg", "\uD83E\uDDC2", 60, staples));
    }
}
