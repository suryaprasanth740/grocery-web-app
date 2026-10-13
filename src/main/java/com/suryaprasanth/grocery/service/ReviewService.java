package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.dto.ReviewRequest;
import com.suryaprasanth.grocery.model.*;
import com.suryaprasanth.grocery.repository.OrderItemRepository;
import com.suryaprasanth.grocery.repository.ProductRepository;
import com.suryaprasanth.grocery.repository.ReviewRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ratings and reviews.
 *
 * Rules: only a buyer who RECEIVED the product (a delivered order) can review it; one review per
 * buyer per product (sending again updates it); hidden reviews don't show and don't count.
 * The product's and shop's averages are kept as running totals, updated in the same transaction.
 */
@Service
public class ReviewService {

    public static final int SHOWN = 20;

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final AuditService auditService;

    public ReviewService(ReviewRepository reviewRepository, ProductRepository productRepository,
                         OrderItemRepository orderItemRepository, AuditService auditService) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
        this.auditService = auditService;
    }

    /** Creates or updates the buyer's review. Returns [review, created?]. */
    @Transactional
    public Object[] save(User buyer, Long productId, ReviewRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        if (!orderItemRepository.boughtWithStatus(productId, buyer.getId(), OrderStatus.DELIVERED)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can review a product once an order with it has been delivered to you.");
        }
        String comment = request.getComment() == null || request.getComment().isBlank()
                ? null : request.getComment().trim();
        int stars = request.getRating();

        Review review = reviewRepository.findMine(productId, buyer.getId()).orElse(null);
        boolean created = review == null;
        if (created) {
            review = new Review(product, buyer);
        } else if (!review.isHidden()) {
            count(review, -1); // take the old stars out before adding the new ones
        }
        review.setRating(stars);
        review.setComment(comment);
        if (!created) {
            review.touch();
        }
        if (!review.isHidden()) {
            count(review, +1);
        }
        return new Object[]{reviewRepository.save(review), created};
    }

    /** Admin moderation: hide (with a reason) or show a review again. */
    @Transactional
    public Review setHidden(User admin, Long reviewId, boolean hidden, String reason) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        if (review.isHidden() == hidden) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    hidden ? "This review is already hidden" : "This review is already visible");
        }
        if (hidden && (reason == null || reason.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give a reason for hiding the review");
        }
        review.setHidden(hidden);
        review.setHiddenReason(hidden ? reason.trim() : null);
        count(review, hidden ? -1 : +1);
        auditService.record(admin, hidden ? "REVIEW_HIDDEN" : "REVIEW_SHOWN", "REVIEW", review.getId(),
                review.getProduct().getName() + (hidden ? ": " + reason.trim() : ""));
        return review;
    }

    private static void count(Review review, int sign) {
        review.getProduct().addRating(review.getRating(), sign);
        review.getShop().addRating(review.getRating(), sign);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> forProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .filter(Product::isBuyable)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productId", product.getId());
        body.put("ratingAverage", product.getRatingAverage());
        body.put("ratingCount", product.getRatingCount());
        body.put("reviews", reviewRepository.visibleForProduct(productId, PageRequest.of(0, SHOWN))
                .stream().map(r -> view(r, false)).toList());
        return body;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> forShop(Shop shop) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("shopId", shop.getId());
        body.put("ratingAverage", shop.getRatingAverage());
        body.put("ratingCount", shop.getRatingCount());
        body.put("reviews", reviewRepository.visibleForShop(shop.getId(), PageRequest.of(0, SHOWN))
                .stream().map(r -> view(r, false)).toList());
        return body;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine(User buyer) {
        return reviewRepository.byBuyer(buyer.getId()).stream().map(r -> view(r, true)).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> latestForAdmin() {
        return reviewRepository.latest(PageRequest.of(0, 100)).stream().map(r -> view(r, true)).toList();
    }

    /** Buyers see only the reviewer's first name; never an email. */
    public static Map<String, Object> view(Review r, boolean withModeration) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("productId", r.getProduct().getId());
        m.put("productName", r.getProduct().getName());
        m.put("shopName", r.getShop().getName());
        m.put("rating", r.getRating());
        m.put("comment", r.getComment());
        String name = r.getBuyer().getName() == null ? "" : r.getBuyer().getName().trim();
        m.put("buyerName", name.isEmpty() ? "Buyer" : name.split("\\s+")[0]);
        m.put("verifiedPurchase", true);
        m.put("createdAt", r.getCreatedAt());
        m.put("edited", r.getUpdatedAt() != null);
        if (withModeration) {
            m.put("hidden", r.isHidden());
            m.put("hiddenReason", r.getHiddenReason());
        }
        return m;
    }
}
