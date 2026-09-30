package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.config.RecipeCatalog;
import com.suryaprasanth.grocery.config.RecipeCatalog.Ingredient;
import com.suryaprasanth.grocery.config.RecipeCatalog.Recipe;
import com.suryaprasanth.grocery.dto.PhotoInfo;
import com.suryaprasanth.grocery.dto.RecipeSummary;
import com.suryaprasanth.grocery.dto.RecipeView;
import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.repository.ProductRepository;
import com.suryaprasanth.grocery.util.UnitUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Recipe to cart": pick a dish (or a festival kit) and the number of people, and get the
 * exact packs to buy. 200 g of dal for 4 people becomes 500 g for 10 people -> one 1 kg pack.
 */
@Service
public class RecipeService {

    public static final int MIN_SERVINGS = 1;
    public static final int MAX_SERVINGS = 50;

    private final RecipeCatalog catalog;
    private final ProductRepository productRepository;

    public RecipeService(RecipeCatalog catalog, ProductRepository productRepository) {
        this.catalog = catalog;
        this.productRepository = productRepository;
    }

    public List<RecipeSummary> list() {
        Map<String, Product> products = productsByName();
        List<RecipeSummary> result = new ArrayList<>();
        for (Recipe r : catalog.all()) {
            RecipeView view = build(r, r.baseServings(), products);
            result.add(new RecipeSummary(r.id(), r.type(), r.name(), r.emoji(), r.description(), r.time(),
                    r.baseServings(), r.ingredients().size(), view.estimatedTotal(), photoOf(r.id())));
        }
        return result;
    }

    public RecipeView view(String id, Integer servings) {
        Recipe recipe = catalog.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found"));
        int people = servings == null ? recipe.baseServings() : servings;
        if (people < MIN_SERVINGS || people > MAX_SERVINGS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Number of people must be between " + MIN_SERVINGS + " and " + MAX_SERVINGS + ".");
        }
        return build(recipe, people, productsByName());
    }

    private RecipeView build(Recipe recipe, int servings, Map<String, Product> products) {
        double scale = (double) servings / recipe.baseServings();
        List<RecipeView.Line> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int missing = 0;

        for (Ingredient ing : recipe.ingredients()) {
            UnitUtil.Amount needed = new UnitUtil.Amount(ing.amount() * scale, ing.base());
            Product product = products.get(ing.productName().toLowerCase());
            boolean available = product != null && product.getStock() > 0 && !product.isExpired();
            int packs = 0;
            BigDecimal lineTotal = BigDecimal.ZERO;
            if (product != null) {
                packs = packsFor(needed, product);
                if (available) {
                    packs = Math.min(packs, product.getStock());
                    lineTotal = product.getPrice().multiply(BigDecimal.valueOf(packs));
                    total = total.add(lineTotal);
                }
            }
            if (!available) {
                missing++;
            }
            lines.add(new RecipeView.Line(ing.productName(), UnitUtil.format(needed), product, packs,
                    lineTotal, available, ing.pantryStaple()));
        }
        return new RecipeView(recipe.id(), recipe.type(), recipe.name(), recipe.emoji(), recipe.description(),
                recipe.time(), recipe.baseServings(), servings, lines, total, missing, photoOf(recipe.id()));
    }

    private PhotoInfo photoOf(String id) {
        RecipeCatalog.Photo p = catalog.photoFor(id);
        return p == null ? null : new PhotoInfo(p.url(), p.author(), p.sourcePage(),
                RecipeCatalog.Photo.LICENSE, RecipeCatalog.Photo.LICENSE_URL);
    }

    /** Packs of this product that cover the amount needed (at least 1, at most 20). */
    static int packsFor(UnitUtil.Amount needed, Product product) {
        int packs = UnitUtil.packsNeeded(needed, UnitUtil.parsePackSize(product.getUnit()));
        if (packs < 0) {
            // Units don't match (e.g. "1 box"): count pieces, otherwise one pack.
            packs = "pcs".equals(needed.base()) ? (int) Math.ceil(needed.value()) : 1;
        }
        return Math.max(1, Math.min(20, packs));
    }

    private Map<String, Product> productsByName() {
        Map<String, Product> map = new HashMap<>();
        for (Product p : productRepository.findAll()) {
            map.putIfAbsent(p.getName().toLowerCase(), p);
        }
        return map;
    }
}
