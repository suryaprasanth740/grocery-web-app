package com.suryaprasanth.grocery.controller;

import com.suryaprasanth.grocery.dto.ReasonRequest;
import com.suryaprasanth.grocery.dto.ReviewRequest;
import com.suryaprasanth.grocery.model.Review;
import com.suryaprasanth.grocery.model.Shop;
import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.repository.ShopRepository;
import com.suryaprasanth.grocery.repository.UserRepository;
import com.suryaprasanth.grocery.service.ReviewService;
import com.suryaprasanth.grocery.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Ratings and reviews: anyone can read them; only buyers who received the product can write one. */
@RestController
public class ReviewController {

    private final ReviewService reviewService;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;

    public ReviewController(ReviewService reviewService, UserRepository userRepository, ShopRepository shopRepository) {
        this.reviewService = reviewService;
        this.userRepository = userRepository;
        this.shopRepository = shopRepository;
    }

    @GetMapping("/api/products/{id}/reviews")
    public Map<String, Object> productReviews(@PathVariable Long id) {
        return reviewService.forProduct(id);
    }

    @GetMapping("/api/shops/{id:\\d+}/reviews")
    public Map<String, Object> shopReviews(@PathVariable Long id) {
        Shop shop = shopRepository.findById(id).filter(Shop::isVisibleToBuyers)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found"));
        return reviewService.forShop(shop);
    }

    /** 201 for a new review, 200 when the buyer's earlier review was updated. */
    @PostMapping("/api/products/{id}/reviews")
    public ResponseEntity<Map<String, Object>> review(@PathVariable Long id, @Valid @RequestBody ReviewRequest request,
                                                      HttpSession session) {
        User buyer = SessionUtil.requireBuyer(session, userRepository);
        Object[] saved = reviewService.save(buyer, id, request);
        return ResponseEntity.status((Boolean) saved[1] ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ReviewService.view((Review) saved[0], true));
    }

    /** The buyer's own reviews, so the order page can show "You rated 4 stars". */
    @GetMapping("/api/reviews/mine")
    public List<Map<String, Object>> mine(HttpSession session) {
        return reviewService.mine(SessionUtil.requireBuyer(session, userRepository));
    }

    @GetMapping("/api/admin/reviews")
    public List<Map<String, Object>> adminList(HttpSession session) {
        SessionUtil.requireAdmin(session, userRepository);
        return reviewService.latestForAdmin();
    }

    @PutMapping("/api/admin/reviews/{id}/hide")
    public Map<String, Object> hide(@PathVariable Long id, @Valid @RequestBody ReasonRequest request, HttpSession session) {
        User admin = SessionUtil.requireAdmin(session, userRepository);
        return ReviewService.view(reviewService.setHidden(admin, id, true, request.getReason()), true);
    }

    @PutMapping("/api/admin/reviews/{id}/show")
    public Map<String, Object> show(@PathVariable Long id, HttpSession session) {
        User admin = SessionUtil.requireAdmin(session, userRepository);
        return ReviewService.view(reviewService.setHidden(admin, id, false, null), true);
    }
}
