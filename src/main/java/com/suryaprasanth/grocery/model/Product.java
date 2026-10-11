package com.suryaprasanth.grocery.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
// Safety net: the database itself refuses a negative stock, even if a bug slips through.
@Check(constraints = "stock >= 0")
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

    /** Best-before date. Null means the item does not expire (e.g. salt). */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** GST rate already included in the price: 0, 5, 12 or 18. */
    @Column(name = "gst_percent")
    private Integer gstPercent = 0;

    /** The shop that sells this product. Older rows are moved to the demo shop at startup. */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "shop_id")
    private Shop shop;

    /** e.g. "Amul", "Aashirvaad". Optional: loose vegetables have no brand. */
    @Column(length = 60)
    private String brand;

    /** Maximum retail price printed on the pack. When higher than the price, buyers see the discount. */
    @Column(precision = 10, scale = 2)
    private BigDecimal mrp;

    /** A seller can switch a product off without deleting it (old orders still point to it). Null = on. */
    @Column(name = "active")
    private Boolean active = Boolean.TRUE;

    /** A product that expires today or earlier cannot be sold. */
    public static final int MIN_DAYS_OF_SHELF_LIFE = 1;
    /** Shown as "Expires soon" when this close to the date. */
    public static final int EXPIRES_SOON_DAYS = 3;

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

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public Integer getGstPercent() {
        return gstPercent == null ? 0 : gstPercent;
    }

    public void setGstPercent(Integer gstPercent) {
        this.gstPercent = gstPercent;
    }

    /** Days left before expiry (negative = already expired). Null if it never expires. */
    @JsonProperty("daysToExpiry")
    public Long daysToExpiry() {
        return expiryDate == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }

    /** True when the product is too close to (or past) its expiry date to sell. */
    @JsonProperty("expired")
    public boolean isExpired() {
        Long days = daysToExpiry();
        return days != null && days < MIN_DAYS_OF_SHELF_LIFE;
    }

    @JsonProperty("expiresSoon")
    public boolean isExpiresSoon() {
        Long days = daysToExpiry();
        return days != null && !isExpired() && days <= EXPIRES_SOON_DAYS;
    }

    public Shop getShop() {
        return shop;
    }

    public void setShop(Shop shop) {
        this.shop = shop;
    }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public BigDecimal getMrp() { return mrp; }
    public void setMrp(BigDecimal mrp) { this.mrp = mrp; }

    public boolean isActive() { return active == null || active; }
    public void setActive(boolean active) { this.active = active; }

    /** Whole-number discount from MRP, e.g. 12 for "12% off". 0 when there is no MRP or no saving. */
    @JsonProperty("discountPercent")
    public int getDiscountPercent() {
        if (mrp == null || price == null || mrp.compareTo(price) <= 0) {
            return 0;
        }
        return mrp.subtract(price).multiply(BigDecimal.valueOf(100))
                .divide(mrp, 0, java.math.RoundingMode.DOWN).intValue();
    }

    /** Can a buyer see and add this product: switched on, and its shop approved with an active owner. */
    @JsonIgnore
    public boolean isBuyable() {
        return isActive() && shop != null && shop.isVisibleToBuyers();
    }

    @JsonProperty("shopId")
    public Long getShopId() { return shop == null ? null : shop.getId(); }

    @JsonProperty("shopName")
    public String getShopName() { return shop == null ? null : shop.getName(); }

    @JsonProperty("shopSlug")
    public String getShopSlug() { return shop == null ? null : shop.getSlug(); }

    /** False when the shop has paused new orders: buyers can look but not order. */
    @JsonProperty("shopAcceptingOrders")
    public boolean isShopAcceptingOrders() { return shop != null && shop.isAcceptingOrders(); }
}
