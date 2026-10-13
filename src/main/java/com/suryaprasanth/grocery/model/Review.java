package com.suryaprasanth.grocery.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A buyer's star rating (1-5) and optional comment for a product they received.
 * One review per buyer per product: reviewing again updates the old one.
 */
@Entity
@Table(name = "reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_review_buyer_product", columnNames = {"product_id", "buyer_id"}),
        indexes = @Index(name = "idx_review_shop", columnList = "shop_id"))
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @Column(nullable = false)
    private Integer rating;

    @Column(length = 500)
    private String comment;

    /** Hidden by an admin (e.g. abusive). Hidden reviews are not shown and don't count in averages. */
    @Column(name = "hidden")
    private Boolean hidden = Boolean.FALSE;

    @Column(name = "hidden_reason", length = 300)
    private String hiddenReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Review() {
    }

    public Review(Product product, User buyer) {
        this.product = product;
        this.shop = product.getShop();
        this.buyer = buyer;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public Shop getShop() { return shop; }
    public User getBuyer() { return buyer; }
    public int getRating() { return rating == null ? 0 : rating; }
    public void setRating(int rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public boolean isHidden() { return hidden != null && hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
    public String getHiddenReason() { return hiddenReason; }
    public void setHiddenReason(String hiddenReason) { this.hiddenReason = hiddenReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void touch() { this.updatedAt = LocalDateTime.now(); }
}
