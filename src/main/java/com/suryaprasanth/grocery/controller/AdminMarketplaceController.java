package com.suryaprasanth.grocery.controller;

import com.suryaprasanth.grocery.dto.AccountView;
import com.suryaprasanth.grocery.dto.ReasonRequest;
import com.suryaprasanth.grocery.dto.ShopView;
import com.suryaprasanth.grocery.model.AuditLog;
import com.suryaprasanth.grocery.model.Role;
import com.suryaprasanth.grocery.model.Shop;
import com.suryaprasanth.grocery.model.ShopStatus;
import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.repository.OrderRepository;
import com.suryaprasanth.grocery.repository.ShopRepository;
import com.suryaprasanth.grocery.repository.UserRepository;
import com.suryaprasanth.grocery.service.AccountService;
import com.suryaprasanth.grocery.service.AuditService;
import com.suryaprasanth.grocery.service.ShopService;
import com.suryaprasanth.grocery.service.ReportService;
import com.suryaprasanth.grocery.service.MarketplaceAdminService;
import com.suryaprasanth.grocery.service.SellerDashboardService;
import com.suryaprasanth.grocery.dto.CategoryRequest;
import com.suryaprasanth.grocery.dto.CouponRequest;
import com.suryaprasanth.grocery.dto.ResolveReportRequest;
import com.suryaprasanth.grocery.model.Category;
import com.suryaprasanth.grocery.model.Coupon;
import com.suryaprasanth.grocery.model.Order;
import com.suryaprasanth.grocery.model.OrderStatus;
import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.model.Report;
import com.suryaprasanth.grocery.model.ReportStatus;
import com.suryaprasanth.grocery.repository.ReportRepository;
import org.springframework.http.ResponseEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import com.suryaprasanth.grocery.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
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

/** Admin tools for the marketplace: shop applications, accounts, the audit trail and basic stats. */
@RestController
@RequestMapping("/api/admin")
public class AdminMarketplaceController {

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final OrderRepository orderRepository;
    private final ShopService shopService;
    private final AccountService accountService;
    private final AuditService auditService;
    private final ReportService reportService;
    private final ReportRepository reportRepository;
    private final MarketplaceAdminService adminService;

    public AdminMarketplaceController(UserRepository userRepository, ShopRepository shopRepository,
                                      OrderRepository orderRepository, ShopService shopService,
                                      AccountService accountService, AuditService auditService,
                                      ReportService reportService, ReportRepository reportRepository,
                                      MarketplaceAdminService adminService) {
        this.reportService = reportService;
        this.reportRepository = reportRepository;
        this.adminService = adminService;
        this.userRepository = userRepository;
        this.shopRepository = shopRepository;
        this.orderRepository = orderRepository;
        this.shopService = shopService;
        this.accountService = accountService;
        this.auditService = auditService;
    }

    // ---------------- Shops ----------------

    /** ?status=PENDING for the applications waiting for a decision; no status = all shops. */
    @GetMapping("/shops")
    public List<ShopView> shops(@RequestParam(required = false) String status, HttpSession session) {
        SessionUtil.requireAdmin(session, userRepository);
        return shopService.list(parseStatus(status)).stream().map(s -> ShopView.of(s, true)).toList();
    }

    @GetMapping("/shops/{id}")
    public Map<String, Object> shop(@PathVariable Long id, HttpSession session) {
        SessionUtil.requireAdmin(session, userRepository);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("shop", ShopView.of(shopService.get(id), true));
        body.put("history", auditService.forTarget("SHOP", id));
        return body;
    }

    @PostMapping("/shops/{id}/approve")
    public ShopView approve(@PathVariable Long id, HttpSession session) {
        User admin = SessionUtil.requireAdmin(session, userRepository);
        return ShopView.of(shopService.approve(admin, id), true);
    }

    @PostMapping("/shops/{id}/reject")
    public ShopView reject(@PathVariable Long id, @Valid @RequestBody ReasonRequest request, HttpSession session) {
        User admin = SessionUtil.requireAdmin(session, userRepository);
        return ShopView.of(shopService.reject(admin, id, request.getReason()), true);
    }

    @PostMapping("/shops/{id}/suspend")
    public ShopView suspend(@PathVariable Long id, @Valid @RequestBody ReasonRequest request, HttpSession session) {
        User admin = SessionUtil.requireAdmin(session, userRepository);
        return ShopView.of(shopService.suspend(admin, id, request.getReason()), true);
    }

    @PostMapping("/shops/{id}/reactivate")
    public ShopView reactivate(@PathVariable Long id, HttpSession session) {
        User admin = SessionUtil.requireAdmin(session, userRepository);
        return ShopView.of(shopService.reactivate(admin, id), true);
    }

    // ---------------- Accounts ----------------

    /** ?role=BUYER or SELLER, newest first, 25 per page (page starts at 0). */
    @GetMapping("/users")
    public Map<String, Object> users(@RequestParam(defaultValue = "BUYER") String role,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "25") int size,
                                     HttpSession session) {
        SessionUtil.requireAdmin(session, userRepository);
        Role wanted = parseRole(role);
        PageRequest request = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<User> users = userRepository.findByRole(wanted.name(), request);
        List<AccountView> rows = users.getContent().stream()
                .map(u -> AccountView.of(u, wanted == Role.SELLER ? shopRepository.findByOwnerId(u.getId()).orElse(null) : null))
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", rows);
        body.put("page", users.getNumber());
        body.put("totalPages", users.getTotalPages());
        body.put("totalItems", users.getTotalElements());
        return body;
    }

    @PostMapping("/users/{id}/suspend")
    public AccountView suspendUser(@PathVariable Long id, @Valid @RequestBody ReasonRequest request, HttpSession session) {
        User admin = SessionUtil.requireAdmin(session, userRepository);
        User user = accountService.suspend(admin, id, request.getReason());
        return AccountView.of(user, shopRepository.findByOwnerId(user.getId()).orElse(null));
    }

    @PostMapping("/users/{id}/reactivate")
    public AccountView reactivateUser(@PathVariable Long id, HttpSession session) {
        User admin = SessionUtil.requireAdmin(session, userRepository);
        User user = accountService.reactivate(admin, id);
        return AccountView.of(user, shopRepository.findByOwnerId(user.getId()).orElse(null));
    }

    // ---------------- Audit trail and stats ----------------

    @GetMapping("/audit")
    public List<AuditLog> audit(@RequestParam(defaultValue = "100") int limit, HttpSession session) {
        SessionUtil.requireAdmin(session, userRepository);
        return auditService.latest(limit);
    }

    /** Real counts from the database; nothing here is estimated. */
    @GetMapping("/stats")
    public Map<String, Object> stats(HttpSession session) {
        SessionUtil.requireAdmin(session, userRepository);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("buyers", userRepository.countByRole(Role.BUYER.name()));
        body.put("sellers", userRepository.countByRole(Role.SELLER.name()));
        Map<String, Long> shops = new LinkedHashMap<>();
        for (ShopStatus s : ShopStatus.values()) {
            shops.put(s.name(), shopRepository.countByStatus(s));
        }
        body.put("shops", shops);
        body.put("orders", orderRepository.count());
        body.put("openReports", reportRepository.countByStatus(ReportStatus.OPEN));

        // Last 7 days, in Indian time: orders actually placed (not cancelled or unpaid) and their value
        ZoneId server = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(Shop.SHOP_ZONE);
        LocalDateTime since = today.minusDays(6).atStartOfDay(Shop.SHOP_ZONE).withZoneSameInstant(server).toLocalDateTime();
        List<Order> week = orderRepository.findPlacedSince(since, SellerDashboardService.COUNTED);
        BigDecimal weekValue = BigDecimal.ZERO;
        long todayOrders = 0;
        BigDecimal todayValue = BigDecimal.ZERO;
        Map<String, BigDecimal> byShop = new HashMap<>();
        for (Order o : week) {
            weekValue = weekValue.add(o.getTotalAmount());
            LocalDate day = o.getCreatedAt().atZone(server).withZoneSameInstant(Shop.SHOP_ZONE).toLocalDate();
            if (day.equals(today)) {
                todayOrders++;
                todayValue = todayValue.add(o.getTotalAmount());
            }
            if (o.getShop() != null) byShop.merge(o.getShop().getName(), o.getTotalAmount(), BigDecimal::add);
        }
        body.put("ordersToday", todayOrders);
        body.put("valueToday", todayValue.setScale(2, java.math.RoundingMode.HALF_UP));
        body.put("ordersLast7Days", week.size());
        body.put("valueLast7Days", weekValue.setScale(2, java.math.RoundingMode.HALF_UP));
        body.put("topShopsLast7Days", byShop.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(5)
                .map(e -> Map.of("shop", e.getKey(), "value", e.getValue().setScale(2, java.math.RoundingMode.HALF_UP)))
                .toList());
        return body;
    }

    // ---------------- Orders across all shops ----------------

    /** ?status=PLACED&shopId=3&page=0 (both filters optional), newest first, 25 per page. */
    @GetMapping("/orders/search")
    public Map<String, Object> searchOrders(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) Long shopId,
                                            @RequestParam(defaultValue = "0") int page,
                                            HttpSession session) {
        SessionUtil.requireAdmin(session, userRepository);
        OrderStatus wanted = null;
        if (status != null && !status.isBlank()) {
            try {
                wanted = OrderStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown order status: " + status);
            }
        }
        Page<Order> result = orderRepository.searchForAdmin(wanted, shopId,
                PageRequest.of(Math.max(0, page), 25, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", result.getContent());
        body.put("page", result.getNumber());
        body.put("totalPages", result.getTotalPages());
        body.put("totalItems", result.getTotalElements());
        return body;
    }

    // ---------------- Reports from buyers ----------------

    /** ?status=OPEN (default) | RESOLVED | DISMISSED | ALL */
    @GetMapping("/reports")
    public List<Report> reports(@RequestParam(defaultValue = "OPEN") String status, HttpSession session) {
        SessionUtil.requireAdmin(session, userRepository);
        if ("ALL".equalsIgnoreCase(status)) return reportService.list(null);
        try {
            return reportService.list(ReportStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be OPEN, RESOLVED, DISMISSED or ALL");
        }
    }

    @PostMapping("/reports/{id}/resolve")
    public Report resolveReport(@PathVariable Long id, @Valid @RequestBody ResolveReportRequest request, HttpSession session) {
        User admin = SessionUtil.requireAdmin(session, userRepository);
        return reportService.resolve(admin, id, request);
    }

    // ---------------- Categories ----------------

    @PostMapping("/categories")
    public ResponseEntity<Category> createCategory(@Valid @RequestBody CategoryRequest request, HttpSession session) {
        User admin = SessionUtil.requireAdmin(session, userRepository);
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createCategory(admin, request));
    }

    @PutMapping("/categories/{id}")
    public Category updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request, HttpSession session) {
        User admin = SessionUtil.requireAdmin(session, userRepository);
        return adminService.updateCategory(admin, id, request);
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id, HttpSession session) {
        User admin = SessionUtil.requireAdmin(session, userRepository);
        adminService.deleteCategory(admin, id);
        return ResponseEntity.noContent().build();
    }

    // ---------------- Promotions ----------------

    @PostMapping("/coupons")
    public ResponseEntity<Coupon> createCoupon(@Valid @RequestBody CouponRequest request, HttpSession session) {
        User admin = SessionUtil.requireAdmin(session, userRepository);
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createCoupon(admin, request));
    }

    @PutMapping("/coupons/{id}")
    public Coupon updateCoupon(@PathVariable Long id, @Valid @RequestBody CouponRequest request, HttpSession session) {
        User admin = SessionUtil.requireAdmin(session, userRepository);
        return adminService.updateCoupon(admin, id, request);
    }

    // ---------------- Product moderation ----------------

    /** Body: {"active": false, "reason": "Expired stock reported by buyers"} */
    @PutMapping("/products/{id}/active")
    public Product setProductActive(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpSession session) {
        User admin = SessionUtil.requireAdmin(session, userRepository);
        Object active = body.get("active");
        if (!(active instanceof Boolean on)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Send active: true or false");
        }
        Object reason = body.get("reason");
        return adminService.setProductActive(admin, id, on, reason == null ? null : reason.toString());
    }

    // ---------------- Helpers ----------------

    private static ShopStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return ShopStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown shop status: " + status);
        }
    }

    private static Role parseRole(String role) {
        try {
            Role r = Role.valueOf(role.trim().toUpperCase(Locale.ROOT));
            if (r == Role.ADMIN) throw new IllegalArgumentException();
            return r;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role must be BUYER or SELLER");
        }
    }
}
