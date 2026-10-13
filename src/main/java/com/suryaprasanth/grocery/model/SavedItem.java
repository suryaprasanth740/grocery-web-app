package com.suryaprasanth.grocery.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A product the buyer saved for later (the heart button). Saving twice keeps one row. */
@Entity
@Table(name = "saved_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_saved_user_product", columnNames = {"user_id", "product_id"}))
public class SavedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public SavedItem() {
    }

    public SavedItem(User user, Product product) {
        this.user = user;
        this.product = product;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Product getProduct() { return product; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
