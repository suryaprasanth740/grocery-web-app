package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.dto.CategoryRequest;
import com.suryaprasanth.grocery.dto.CouponRequest;
import com.suryaprasanth.grocery.model.*;
import com.suryaprasanth.grocery.repository.CategoryRepository;
import com.suryaprasanth.grocery.repository.CouponRepository;
import com.suryaprasanth.grocery.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;

/** Admin changes to the shared catalogue setup: categories, promotions (coupons) and hiding products. */
@Service
public class MarketplaceAdminService {

    private final CategoryRepository categoryRepository;
    private final CouponRepository couponRepository;
    private final ProductRepository productRepository;
    private final AuditService auditService;

    public MarketplaceAdminService(CategoryRepository categoryRepository, CouponRepository couponRepository,
                                   ProductRepository productRepository, AuditService auditService) {
        this.categoryRepository = categoryRepository;
        this.couponRepository = couponRepository;
        this.productRepository = productRepository;
        this.auditService = auditService;
    }

    // ---------------- Categories ----------------

    @Transactional
    public Category createCategory(User admin, CategoryRequest r) {
        String name = r.getName().trim();
        if (categoryRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A category called " + name + " already exists");
        }
        Category c = categoryRepository.save(new Category(name, blankToDefault(r.getIcon())));
        auditService.record(admin, "CATEGORY_CREATED", "CATEGORY", c.getId(), name);
        return c;
    }

    @Transactional
    public Category updateCategory(User admin, Long id, CategoryRequest r) {
        Category c = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        String name = r.getName().trim();
        categoryRepository.findByNameIgnoreCase(name).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A category called " + name + " already exists");
        });
        String old = c.getName();
        c.setName(name);
        c.setIcon(blankToDefault(r.getIcon()));
        auditService.record(admin, "CATEGORY_UPDATED", "CATEGORY", id, old + " -> " + name);
        return categoryRepository.save(c);
    }

    /** Only an empty category can be deleted, so no product is ever left without one. */
    @Transactional
    public void deleteCategory(User admin, Long id) {
        Category c = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        long used = productRepository.countInCategory(id);
        if (used > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    c.getName() + " still has " + used + " product(s). Move them to another category first.");
        }
        categoryRepository.delete(c);
        auditService.record(admin, "CATEGORY_DELETED", "CATEGORY", id, c.getName());
    }

    private static String blankToDefault(String icon) {
        return icon == null || icon.isBlank() ? "🛒" : icon.trim();
    }

    // ---------------- Promotions (platform coupons) ----------------

    @Transactional
    public Coupon createCoupon(User admin, CouponRequest r) {
        String code = r.getCode().trim().toUpperCase(Locale.ROOT);
        if (couponRepository.findByCodeIgnoreCase(code).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Coupon " + code + " already exists");
        }
        Coupon c = new Coupon();
        c.setCode(code);
        apply(c, r);
        c.setActive(true);
        c = couponRepository.save(c);
        auditService.record(admin, "COUPON_CREATED", "COUPON", c.getId(), code + ": " + c.getDescription());
        return c;
    }

    /** Edits everything except the code (buyers may already have it). */
    @Transactional
    public Coupon updateCoupon(User admin, Long id, CouponRequest r) {
        Coupon c = couponRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found"));
        if (!c.getCode().equalsIgnoreCase(r.getCode().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A coupon's code can't be changed. Create a new coupon instead.");
        }
        apply(c, r);
        auditService.record(admin, "COUPON_UPDATED", "COUPON", id, c.getCode() + ": " + c.getDescription());
        return couponRepository.save(c);
    }

    private static void apply(Coupon c, CouponRequest r) {
        if (r.getType() == CouponType.PERCENT) {
            if (r.getValue().compareTo(BigDecimal.valueOf(90)) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A percentage coupon can be at most 90% off");
            }
            if (r.getMaxDiscount() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Set a maximum discount in rupees for a percentage coupon");
            }
        }
        if (r.getLastDay().isBefore(LocalDate.now(Shop.SHOP_ZONE))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The last day can't be in the past");
        }
        c.setDescription(r.getDescription().trim());
        c.setType(r.getType());
        c.setValue(r.getValue().setScale(2, RoundingMode.HALF_UP));
        c.setMaxDiscount(r.getType() == CouponType.PERCENT ? r.getMaxDiscount().setScale(2, RoundingMode.HALF_UP) : null);
        c.setMinOrder(r.getMinOrder().setScale(2, RoundingMode.HALF_UP));
        c.setExpiresAt(r.getLastDay().atTime(23, 59, 59));
        c.setPerUserLimit(r.getPerUserLimit());
    }

    // ---------------- Product moderation ----------------

    /** Hide (or show again) any shop's product, e.g. after a complaint. */
    @Transactional
    public Product setProductActive(User admin, Long productId, boolean active, String reason) {
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        if (!active && (reason == null || reason.trim().length() < 5)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give a reason (at least 5 characters) for hiding it");
        }
        p.setActive(active);
        auditService.record(admin, active ? "PRODUCT_SHOWN" : "PRODUCT_HIDDEN", "PRODUCT", productId,
                p.getName() + (p.getShop() != null ? " (" + p.getShop().getName() + ")" : "") + (reason == null ? "" : ": " + reason.trim()));
        return productRepository.save(p);
    }
}
