package com.suryaprasanth.grocery.dto;

import com.suryaprasanth.grocery.model.ShopCategory;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalTime;

/** Shop details typed by the owner, when applying and when editing later. */
public class ShopDetailsRequest {

    @NotBlank(message = "Shop name is required")
    @Size(max = 80, message = "Shop name must be 80 characters or less")
    private String shopName;

    @NotNull(message = "Choose the type of shop")
    private ShopCategory category;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "[6-9][0-9]{9}", message = "Enter a 10-digit mobile number, e.g. 9876543210")
    private String phone;

    @NotBlank(message = "Shop address is required")
    @Size(min = 10, max = 250, message = "Shop address must be 10 to 250 characters")
    private String addressLine;

    @NotBlank(message = "PIN code is required")
    @Pattern(regexp = "[1-9][0-9]{5}", message = "Enter a valid 6-digit PIN code")
    private String pincode;

    /** Extra PIN codes the shop delivers to, e.g. "560076, 560068" or "560xxx". Optional. */
    @Size(max = 400, message = "Too many PIN codes. Use areas like 560xxx instead.")
    private String servicePincodes;

    @NotNull(message = "Opening time is required")
    private LocalTime openTime;

    @NotNull(message = "Closing time is required")
    private LocalTime closeTime;

    private boolean offersDelivery = true;

    private boolean offersPickup = true;

    @NotNull(message = "Delivery fee is required (0 for free delivery)")
    @DecimalMin(value = "0.00", message = "Delivery fee can't be negative")
    @DecimalMax(value = "200.00", message = "Delivery fee can be at most Rs 200")
    @Digits(integer = 3, fraction = 2, message = "Delivery fee can have at most 2 decimal places")
    private BigDecimal deliveryFee;

    @DecimalMin(value = "1.00", message = "Free-delivery amount must be at least Rs 1")
    @DecimalMax(value = "10000.00", message = "Free-delivery amount can be at most Rs 10,000")
    @Digits(integer = 5, fraction = 2, message = "Free-delivery amount can have at most 2 decimal places")
    private BigDecimal freeDeliveryAbove;

    @NotNull(message = "Minimum order is required (0 for no minimum)")
    @DecimalMin(value = "0.00", message = "Minimum order can't be negative")
    @DecimalMax(value = "5000.00", message = "Minimum order can be at most Rs 5,000")
    @Digits(integer = 4, fraction = 2, message = "Minimum order can have at most 2 decimal places")
    private BigDecimal minOrderAmount;

    @Size(max = 100, message = "Logo address is too long")
    private String logoUrl;

    @DecimalMin(value = "6.0", message = "Location must be inside India")
    @DecimalMax(value = "37.5", message = "Location must be inside India")
    private Double latitude;

    @DecimalMin(value = "68.0", message = "Location must be inside India")
    @DecimalMax(value = "97.5", message = "Location must be inside India")
    private Double longitude;

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }
    public ShopCategory getCategory() { return category; }
    public void setCategory(ShopCategory category) { this.category = category; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddressLine() { return addressLine; }
    public void setAddressLine(String addressLine) { this.addressLine = addressLine; }
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    public String getServicePincodes() { return servicePincodes; }
    public void setServicePincodes(String servicePincodes) { this.servicePincodes = servicePincodes; }
    public LocalTime getOpenTime() { return openTime; }
    public void setOpenTime(LocalTime openTime) { this.openTime = openTime; }
    public LocalTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalTime closeTime) { this.closeTime = closeTime; }
    public boolean isOffersDelivery() { return offersDelivery; }
    public void setOffersDelivery(boolean offersDelivery) { this.offersDelivery = offersDelivery; }
    public boolean isOffersPickup() { return offersPickup; }
    public void setOffersPickup(boolean offersPickup) { this.offersPickup = offersPickup; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public void setDeliveryFee(BigDecimal deliveryFee) { this.deliveryFee = deliveryFee; }
    public BigDecimal getFreeDeliveryAbove() { return freeDeliveryAbove; }
    public void setFreeDeliveryAbove(BigDecimal freeDeliveryAbove) { this.freeDeliveryAbove = freeDeliveryAbove; }
    public BigDecimal getMinOrderAmount() { return minOrderAmount; }
    public void setMinOrderAmount(BigDecimal minOrderAmount) { this.minOrderAmount = minOrderAmount; }
}
