package com.suryaprasanth.grocery.dto;

import com.suryaprasanth.grocery.model.CouponType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** An admin creating or editing a platform-wide promotion (coupon). NammaBasket pays for the discount. */
public class CouponRequest {

    @NotBlank(message = "Coupon code is required")
    @Pattern(regexp = "[A-Za-z0-9]{3,20}", message = "Code must be 3 to 20 letters or digits, e.g. DIWALI100")
    private String code;

    @NotBlank(message = "Describe the offer, e.g. Rs 100 off on orders above Rs 999")
    @Size(max = 150, message = "Description must be 150 characters or less")
    private String description;

    @NotNull(message = "Choose FLAT (rupees off) or PERCENT")
    private CouponType type;

    @NotNull(message = "Discount value is required")
    @DecimalMin(value = "1", message = "Discount must be at least 1")
    @DecimalMax(value = "1000", message = "Discount can be at most Rs 1,000 (or 90%)")
    private BigDecimal value;

    /** Only for PERCENT coupons: the most it can take off, in rupees. */
    @DecimalMin(value = "1", message = "Maximum discount must be at least Rs 1")
    @DecimalMax(value = "1000", message = "Maximum discount can be at most Rs 1,000")
    private BigDecimal maxDiscount;

    @NotNull(message = "Minimum order is required (0 for none)")
    @DecimalMin(value = "0", message = "Minimum order can't be negative")
    @DecimalMax(value = "100000", message = "Minimum order is too high")
    private BigDecimal minOrder;

    @NotNull(message = "Choose the last day the coupon works")
    private LocalDate lastDay;

    @NotNull(message = "Uses per buyer is required")
    @Min(value = 1, message = "Each buyer must be able to use it at least once")
    @Max(value = 50, message = "At most 50 uses per buyer")
    private Integer perUserLimit;

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
    public LocalDate getLastDay() { return lastDay; }
    public void setLastDay(LocalDate lastDay) { this.lastDay = lastDay; }
    public Integer getPerUserLimit() { return perUserLimit; }
    public void setPerUserLimit(Integer perUserLimit) { this.perUserLimit = perUserLimit; }
}
