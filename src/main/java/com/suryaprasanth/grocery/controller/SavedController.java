package com.suryaprasanth.grocery.controller;

import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.repository.UserRepository;
import com.suryaprasanth.grocery.service.CartService;
import com.suryaprasanth.grocery.service.SavedService;
import com.suryaprasanth.grocery.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Saved for later. Buyers only, like the cart. */
@RestController
public class SavedController {

    private final SavedService savedService;
    private final CartService cartService;
    private final UserRepository userRepository;

    public SavedController(SavedService savedService, CartService cartService, UserRepository userRepository) {
        this.savedService = savedService;
        this.cartService = cartService;
        this.userRepository = userRepository;
    }

    @GetMapping("/api/saved")
    public List<Map<String, Object>> list(HttpSession session) {
        return savedService.list(SessionUtil.requireBuyer(session, userRepository));
    }

    /** 201 when newly saved, 200 when it was already saved. */
    @PostMapping("/api/saved/{productId}")
    public ResponseEntity<List<Map<String, Object>>> save(@PathVariable Long productId, HttpSession session) {
        User user = SessionUtil.requireBuyer(session, userRepository);
        boolean created = savedService.save(user, productId);
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).body(savedService.list(user));
    }

    @DeleteMapping("/api/saved/{productId}")
    public List<Map<String, Object>> remove(@PathVariable Long productId, HttpSession session) {
        User user = SessionUtil.requireBuyer(session, userRepository);
        savedService.remove(user, productId);
        return savedService.list(user);
    }

    @PostMapping("/api/saved/{productId}/move-to-cart")
    public Map<String, Object> moveToCart(@PathVariable Long productId, HttpSession session) {
        User user = SessionUtil.requireBuyer(session, userRepository);
        savedService.moveToCart(user, productId);
        return both(user);
    }

    @PostMapping("/api/cart/save-for-later/{productId}")
    public Map<String, Object> saveFromCart(@PathVariable Long productId, HttpSession session) {
        User user = SessionUtil.requireBuyer(session, userRepository);
        savedService.saveFromCart(user, productId);
        return both(user);
    }

    private Map<String, Object> both(User user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cart", cartService.cartOf(user));
        body.put("saved", savedService.list(user));
        return body;
    }
}
