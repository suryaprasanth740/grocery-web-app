package com.suryaprasanth.grocery.controller;

import com.suryaprasanth.grocery.dto.ShopView;
import com.suryaprasanth.grocery.model.Shop;
import com.suryaprasanth.grocery.model.ShopStatus;
import com.suryaprasanth.grocery.repository.ProductRepository;
import com.suryaprasanth.grocery.repository.ShopRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Finding shops. Only APPROVED shops with an active owner are shown. The buyer's location
 * (if they share it) is used only to work out distances for this one request; it is never stored.
 */
@RestController
@RequestMapping("/api/shops")
public class ShopController {

    /** Shops taking orders first, then nearest (when distance is known), then by name. */
    private static final Comparator<ShopView> NEAREST_OPEN_FIRST =
            Comparator.comparing((ShopView v) -> !v.acceptingOrders())
                    .thenComparingDouble(v -> v.distanceKm() == null ? Double.MAX_VALUE : v.distanceKm())
                    .thenComparing(ShopView::name);

    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;

    public ShopController(ShopRepository shopRepository, ProductRepository productRepository) {
        this.shopRepository = shopRepository;
        this.productRepository = productRepository;
    }

    /**
     * ?pincode=560073 shows shops that deliver there; &q= searches shop names; &category=KIRANA;
     * &lat=12.97&lng=77.59 adds distances and sorts nearest first.
     */
    @GetMapping
    public List<ShopView> list(@RequestParam(required = false) String pincode,
                               @RequestParam(required = false) String q,
                               @RequestParam(required = false) String category,
                               @RequestParam(required = false) Double lat,
                               @RequestParam(required = false) Double lng) {
        String text = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return shopRepository.findByStatusOrderByCreatedAtAsc(ShopStatus.APPROVED).stream()
                .filter(Shop::isVisibleToBuyers)
                .filter(s -> pincode == null || pincode.isBlank() || s.servesPincode(pincode.trim()))
                .filter(s -> text.isEmpty() || s.getName().toLowerCase(Locale.ROOT).contains(text))
                .filter(s -> category == null || category.isBlank() || s.getCategory().name().equalsIgnoreCase(category))
                .map(s -> ShopView.forBuyer(s, s.distanceKmFrom(lat, lng)))
                .sorted(NEAREST_OPEN_FIRST)
                .toList();
    }

    /** One shop's page: its details and the products buyers can order from it. */
    @GetMapping("/{slug}")
    public Map<String, Object> shop(@PathVariable String slug,
                                    @RequestParam(required = false) Double lat,
                                    @RequestParam(required = false) Double lng) {
        Shop shop = shopRepository.findBySlug(slug)
                .filter(Shop::isVisibleToBuyers)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("shop", ShopView.forBuyer(shop, shop.distanceKmFrom(lat, lng)));
        body.put("products", productRepository.findBuyableByShop(shop.getId()));
        return body;
    }
}
