package com.suryaprasanth.grocery.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupons")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_type", nullable = false, length = 10)
    private CouponType type;

    /** Percent (e.g. 10) for PERCENT coupons, rupees (e.g. 50) for FLAT coupons. */
    // Column is not called "value": that is a reserved word in some databases (H2).
    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    /** Upper limit on the discount for PERCENT coupons. Null = no limit. */
    @Column(name = "max_discount", precision = 10, scale = 2)
    private BigDecimal maxDiscount;

    /** Minimum item total needed to use the coupon. */
    @Column(name = "min_order", nullable = false, precision = 10, scale = 2)
    private BigDecimal minOrder = BigDecimal.ZERO;

    /** Coupon stops working at this exact moment. Null = never expires. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** How many times one customer may use it. */
    @Column(name = "per_user_limit", nullable = false)
    private Integer perUserLimit = 1;

    @Column(nullable = false)
    private boolean active = true;

    public Coupon() {
    }

    public Coupon(String code, String description, CouponType type, BigDecimal value,
                  BigDecimal maxDiscount, BigDecimal minOrder, LocalDateTime expiresAt, Integer perUserLimit) {
        this.code = code;
        this.description = description;
        this.type = type;
        this.value = value;
        this.maxDiscount = maxDiscount;
        this.minOrder = minOrder;
        this.expiresAt = expiresAt;
        this.perUserLimit = perUserLimit;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public CouponType getType() { return type; }
    public void setType(CouponType type) { this.type = type; }

    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }

    public BigDecimal getMaxDiscount() { return maxDiscount; }
    public void setMaxDiscount(BigDecimal maxDiscount) { this.maxDiscount = maxDiscount; }

    public BigDecimal getMinOrder() { return minOrder; }
    public void setMinOrder(BigDecimal minOrder) { this.minOrder = minOrder; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public Integer getPerUserLimit() { return perUserLimit; }
    public void setPerUserLimit(Integer perUserLimit) { this.perUserLimit = perUserLimit; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
