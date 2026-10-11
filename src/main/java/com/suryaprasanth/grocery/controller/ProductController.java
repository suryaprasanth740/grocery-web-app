package com.suryaprasanth.grocery.controller;

import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.model.Shop;
import com.suryaprasanth.grocery.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The buyer catalogue. Only products that are switched on, from approved shops whose owner
 * is not suspended, are ever returned. Hidden products answer 404, as if they did not exist.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Whole list, for the shop and smart-shopping pages. Optional filters: search text,
     * category, one shop, and a PIN code (only shops that deliver there).
     */
    @GetMapping
    public List<Product> list(@RequestParam(required = false) Long categoryId,
                              @RequestParam(required = false) String search,
                              @RequestParam(required = false) Long shopId,
                              @RequestParam(required = false) String pincode) {
        List<Product> products = shopId == null ? productRepository.findBuyable() : productRepository.findBuyableByShop(shopId);
        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return products.stream()
                .filter(p -> categoryId == null || (p.getCategory() != null && categoryId.equals(p.getCategory().getId())))
                .filter(p -> q.isEmpty() || p.getName().toLowerCase(Locale.ROOT).contains(q)
                        || (p.getBrand() != null && p.getBrand().toLowerCase(Locale.ROOT).contains(q)))
                .filter(p -> pincode == null || pincode.isBlank() || servesOrPickup(p.getShop(), pincode.trim()))
                .toList();
    }

    /** Paged search: ?q=atta&categoryId=6&shopId=3&sort=price&page=0&size=24 */
    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) Long categoryId,
                                      @RequestParam(required = false) Long shopId,
                                      @RequestParam(defaultValue = "relevance") String sort,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "24") int size) {
        Sort order = switch (sort) {
            case "price" -> Sort.by("price").ascending();
            case "price-desc" -> Sort.by("price").descending();
            case "name" -> Sort.by("name").ascending();
            default -> Sort.by("id").ascending();
        };
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        Page<Product> result = productRepository.searchBuyable(like, categoryId, shopId,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 60), order));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", result.getContent());
        body.put("page", result.getNumber());
        body.put("totalPages", result.getTotalPages());
        body.put("totalItems", result.getTotalElements());
        return body;
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable Long id) {
        return productRepository.findById(id)
                .filter(Product::isBuyable)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    /** A shop is useful to this PIN code if it delivers there. */
    private static boolean servesOrPickup(Shop shop, String pincode) {
        return shop != null && shop.isOffersDelivery() && shop.servesPincode(pincode);
    }
}
