package com.suryaprasanth.grocery.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(length = 20)
    private String unit; // e.g. "1 kg", "500 ml", "1 dozen"

    @Column(name = "image_emoji", length = 10)
    private String imageEmoji; // simple emoji used as a product thumbnail fallback

    @Column(name = "image_url", length = 500)
    private String imageUrl; // real product photo; falls back to imageEmoji when null/blank

    @Column(nullable = false)
    private Integer stock;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;

    public Product() {
    }

    public Product(String name, String description, BigDecimal price, String unit,
                    String imageEmoji, Integer stock, Category category) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.unit = unit;
        this.imageEmoji = imageEmoji;
        this.stock = stock;
        this.category = category;
    }

    public Product(String name, String description, BigDecimal price, String unit,
                    String imageEmoji, String imageUrl, Integer stock, Category category) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.unit = unit;
        this.imageEmoji = imageEmoji;
        this.imageUrl = imageUrl;
        this.stock = stock;
        this.category = category;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getImageEmoji() {
        return imageEmoji;
    }

    public void setImageEmoji(String imageEmoji) {
        this.imageEmoji = imageEmoji;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }
}
