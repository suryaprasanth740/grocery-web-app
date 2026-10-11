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

    public AdminMarketplaceController(UserRepository userRepository, ShopRepository shopRepository,
                                      OrderRepository orderRepository, ShopService shopService,
                                      AccountService accountService, AuditService auditService) {
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
        return body;
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
