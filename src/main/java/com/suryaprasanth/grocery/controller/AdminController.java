package com.suryaprasanth.grocery.controller;

import com.suryaprasanth.grocery.dto.ProductRequest;
import com.suryaprasanth.grocery.dto.StatusUpdateRequest;
import com.suryaprasanth.grocery.model.*;
import com.suryaprasanth.grocery.repository.*;
import com.suryaprasanth.grocery.service.OrderService;
import com.suryaprasanth.grocery.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
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

    public AdminController(UserRepository userRepository, ProductRepository productRepository,
                           CategoryRepository categoryRepository, OrderRepository orderRepository,
                           OrderIssueRepository issueRepository, CouponRepository couponRepository,
                           OrderService orderService) {
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
        SessionUtil.requireAdmin(session, userRepository);
        return orderService.adminUpdateStatus(id, request.getStatus());
    }

    // ---------------- Products ----------------

    @GetMapping("/products")
    public List<Product> products(HttpSession session) {
        SessionUtil.requireAdmin(session, userRepository);
        return productRepository.findAll();
    }

    @PostMapping("/products")
    public Product createProduct(@Valid @RequestBody ProductRequest request, HttpSession session) {
        SessionUtil.requireAdmin(session, userRepository);
        Product product = new Product();
        apply(product, request);
        return productRepository.save(product);
    }

    @PutMapping("/products/{id}")
    public Product updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest request,
                                 HttpSession session) {
        SessionUtil.requireAdmin(session, userRepository);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        apply(product, request);
        return productRepository.save(product);
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
        return couponRepository.findAll();
    }

    @PutMapping("/coupons/{id}/active")
    public Coupon setCouponActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body,
                                  HttpSession session) {
        SessionUtil.requireAdmin(session, userRepository);
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found"));
        coupon.setActive(Boolean.TRUE.equals(body.get("active")));
        return couponRepository.save(coupon);
    }
}
