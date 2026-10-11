package com.suryaprasanth.grocery.controller;

import com.suryaprasanth.grocery.service.AuditService;
import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.dto.ProductRequest;
import com.suryaprasanth.grocery.dto.StatusUpdateRequest;
import com.suryaprasanth.grocery.model.*;
import com.suryaprasanth.grocery.repository.*;
import com.suryaprasanth.grocery.service.OrderService;
import com.suryaprasanth.grocery.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything under /api/admin needs an ADMIN login. Normal customers get 403. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Set<Integer> GST_RATES = Set.of(0, 5, 12, 18);

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OrderRepository orderRepository;
    private final OrderIssueRepository issueRepository;
    private final CouponRepository couponRepository;
    private final OrderService orderService;
    private final AuditService auditService;
    private final ShopRepository shopRepository;

    public AdminController(UserRepository userRepository, ProductRepository productRepository,
                           CategoryRepository categoryRepository, OrderRepository orderRepository,
                           OrderIssueRepository issueRepository, CouponRepository couponRepository,
                           OrderService orderService, AuditService auditService, ShopRepository shopRepository) {
        this.auditService = auditService;
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.orderRepository = orderRepository;
        this.issueRepository = issueRepository;
        this.couponRepository = couponRepository;
        this.orderService = orderService;
    }

    // ---------------- Orders ----------------

    @GetMapping("/orders")
    public List<Order> allOrders(HttpSession session) {
        SessionUtil.requireAdmin(session, userRepository);
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    @PutMapping("/orders/{id}/status")
    public Order updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request,
                              HttpSession session) {
        User admin = SessionUtil.requireAdmin(session, userRepository);
        Order order = orderService.adminUpdateStatus(id, request.getStatus());
        auditService.record(admin, "ORDER_STATUS_CHANGED", "ORDER", id, "to " + order.getStatus());
        return order;
    }

    // ---------------- Products ----------------

    @GetMapping("/products")
    public List<Product> products(HttpSession session) {
        SessionUtil.requireAdmin(session, userRepository);
        return productRepository.findAll(Sort.by("id"));
    }

    @PostMapping("/products")
    public Product createProduct(@Valid @RequestBody ProductRequest request, HttpSession session) {
        User admin = SessionUtil.requireAdmin(session, userRepository);
        Product product = new Product();
        // Products added from the admin panel belong to the platform's own demo shop.
        product.setShop(shopRepository.findByOwnerId(admin.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "The admin account has no shop")));
        apply(product, request);
        product = productRepository.save(product);
        auditService.record(admin, "PRODUCT_CREATED", "PRODUCT", product.getId(), product.getName());
        return product;
    }

    @PutMapping("/products/{id}")
    public Product updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest request,
                                 HttpSession session) {
        User admin = SessionUtil.requireAdmin(session, userRepository);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        // Admins edit only the platform's own demo shop. A seller's prices and stock are the seller's;
        // admins can hide a seller's product (PUT /api/admin/products/{id}/active) but not rewrite it.
        if (product.getShop() == null || !product.getShop().getOwner().getId().equals(admin.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This product belongs to a seller's shop. You can hide it, but only the seller can edit it.");
        }
        apply(product, request);
        product = productRepository.save(product);
        auditService.record(admin, "PRODUCT_UPDATED", "PRODUCT", product.getId(),
                product.getName() + " price " + product.getPrice() + " stock " + product.getStock());
        return product;
    }

    private void apply(Product product, ProductRequest r) {
        if (!GST_RATES.contains(r.getGstPercent())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GST must be 0, 5, 12 or 18 percent");
        }
        product.setName(r.getName().trim());
        product.setDescription(r.getDescription());
        product.setPrice(r.getPrice());
        product.setUnit(r.getUnit());
        product.setStock(r.getStock());
        product.setExpiryDate(r.getExpiryDate());
        product.setGstPercent(r.getGstPercent());
        if (r.getImageEmoji() != null && !r.getImageEmoji().isBlank()) {
            product.setImageEmoji(r.getImageEmoji());
        }
        if (r.getImageUrl() != null) {
            product.setImageUrl(r.getImageUrl().isBlank() ? null : r.getImageUrl().trim());
        }
        if (r.getCategoryId() != null) {
            product.setCategory(categoryRepository.findById(r.getCategoryId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category not found")));
        }
    }

    // ---------------- Missing-item reports ----------------

    @GetMapping("/issues")
    public List<OrderIssue> issues(HttpSession session) {
        SessionUtil.requireAdmin(session, userRepository);
        return issueRepository.findAllByOrderByCreatedAtDesc();
    }

    // ---------------- Coupons ----------------

    @GetMapping("/coupons")
    public List<Coupon> coupons(HttpSession session) {
        SessionUtil.requireAdmin(session, userRepository);
        return couponRepository.findAll(Sort.by("id"));
    }

    @PutMapping("/coupons/{id}/active")
    public Coupon setCouponActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body,
                                  HttpSession session) {
        User admin = SessionUtil.requireAdmin(session, userRepository);
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found"));
        coupon.setActive(Boolean.TRUE.equals(body.get("active")));
        auditService.record(admin, coupon.isActive() ? "COUPON_ENABLED" : "COUPON_DISABLED", "COUPON",
                coupon.getId(), coupon.getCode());
        return couponRepository.save(coupon);
    }
}
