package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.dto.SellerProductRequest;
import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.model.Shop;
import com.suryaprasanth.grocery.model.ShopStatus;
import com.suryaprasanth.grocery.repository.CategoryRepository;
import com.suryaprasanth.grocery.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;

/**
 * A seller's own products. Every method takes the seller's shop and only ever loads products
 * WHERE shop_id = that shop, so a seller can't read or change another shop's products even by
 * guessing ids: someone else's product simply answers 404.
 */
@Service
public class SellerProductService {

    private static final Set<Integer> GST_RATES = Set.of(0, 5, 12, 18);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ImageService imageService;

    public SellerProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                                ImageService imageService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.imageService = imageService;
    }

    @Transactional(readOnly = true)
    public Page<Product> list(Shop shop, String q, Long categoryId, String sort, int page, int size) {
        Sort order = switch (sort == null ? "" : sort) {
            case "price" -> Sort.by("price").ascending();
            case "stock" -> Sort.by("stock").ascending().and(Sort.by("name"));
            case "newest" -> Sort.by("id").descending();
            default -> Sort.by("name").ascending();
        };
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return productRepository.searchByShop(shop.getId(), like, categoryId,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100), order));
    }

    @Transactional(readOnly = true)
    public Product get(Shop shop, Long productId) {
        return productRepository.findByIdAndShopId(productId, shop.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found in your shop"));
    }

    @Transactional
    public Product create(Shop shop, SellerProductRequest request) {
        requireNotSuspended(shop);
        Product product = new Product();
        product.setShop(shop);
        apply(shop, product, request);
        return productRepository.save(product);
    }

    @Transactional
    public Product update(Shop shop, Long productId, SellerProductRequest request) {
        requireNotSuspended(shop);
        Product product = get(shop, productId);
        apply(shop, product, request);
        return productRepository.save(product);
    }

    @Transactional
    public Product setStock(Shop shop, Long productId, Integer stock) {
        requireNotSuspended(shop);
        if (stock == null || stock < 0 || stock > 10000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stock must be between 0 and 10000");
        }
        Product product = get(shop, productId);
        product.setStock(stock);
        return productRepository.save(product);
    }

    /** Switching off hides the product from buyers but keeps it, so old orders still make sense. */
    @Transactional
    public Product setActive(Shop shop, Long productId, boolean active) {
        requireNotSuspended(shop);
        Product product = get(shop, productId);
        product.setActive(active);
        return productRepository.save(product);
    }

    private static void requireNotSuspended(Shop shop) {
        if (shop.getStatus() == ShopStatus.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Your shop is suspended, so products can't be changed. Contact NammaBasket support.");
        }
    }

    private void apply(Shop shop, Product product, SellerProductRequest r) {
        if (r.getCategoryId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a category");
        }
        if (!GST_RATES.contains(r.getGstPercent())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GST must be 0, 5, 12 or 18 percent");
        }
        if (r.getMrp() != null && r.getMrp().compareTo(r.getPrice()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MRP can't be lower than your selling price");
        }
        product.setName(r.getName().trim());
        product.setBrand(blankToNull(r.getBrand()));
        product.setDescription(blankToNull(r.getDescription()));
        product.setPrice(BillingService.money(r.getPrice()));
        product.setMrp(r.getMrp() == null ? null : BillingService.money(r.getMrp()));
        product.setUnit(blankToNull(r.getUnit()));
        product.setStock(r.getStock());
        product.setExpiryDate(r.getExpiryDate());
        product.setGstPercent(r.getGstPercent());
        product.setActive(r.isActive());
        product.setImageEmoji(r.getImageEmoji() == null || r.getImageEmoji().isBlank() ? "🛒" : r.getImageEmoji().trim());
        product.setImageUrl(imageService.checkSellerImageUrl(shop.getId(), r.getImageUrl()));
        product.setCategory(categoryRepository.findById(r.getCategoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category not found")));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
