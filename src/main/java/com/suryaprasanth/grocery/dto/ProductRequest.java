package com.suryaprasanth.grocery.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Used by the admin panel to add or edit a product. */
public class ProductRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must be 150 characters or less")
    private String name;

    @Size(max = 500, message = "Description must be 500 characters or less")
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "1.00", message = "Price must be at least Rs 1")
    @DecimalMax(value = "100000.00", message = "Price must be at most Rs 1,00,000")
    @Digits(integer = 6, fraction = 2, message = "Price can have at most 2 decimal places")
    private BigDecimal price;

    @Size(max = 20, message = "Unit must be 20 characters or less")
    private String unit;

    @NotNull(message = "Stock is required")
    @Min(value = 0, message = "Stock cannot be negative")
    @Max(value = 10000, message = "Stock must be at most 10000")
    private Integer stock;

    private LocalDate expiryDate;

    @NotNull(message = "GST rate is required")
    private Integer gstPercent;

    private Long categoryId;

    @Size(max = 10, message = "Emoji is too long")
    private String imageEmoji;

    @Size(max = 500, message = "Image URL is too long")
    private String imageUrl;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public Integer getGstPercent() { return gstPercent; }
    public void setGstPercent(Integer gstPercent) { this.gstPercent = gstPercent; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getImageEmoji() { return imageEmoji; }
    public void setImageEmoji(String imageEmoji) { this.imageEmoji = imageEmoji; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
