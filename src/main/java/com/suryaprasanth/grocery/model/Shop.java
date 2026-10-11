package com.suryaprasanth.grocery.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A kirana shop on the marketplace. Each shop has exactly one owner (a SELLER account),
 * its own delivery rules and its own products.
 */
@Entity
@Table(name = "shops", indexes = {
        @Index(name = "idx_shop_status", columnList = "status"),
        @Index(name = "idx_shop_owner", columnList = "owner_id")
})
public class Shop {

    /** Shop hours are always read in Indian time, whatever the server's clock zone is. */
    public static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Kolkata");

    /** "560073" = one PIN code; "560xxx" = every PIN code that starts with 560. */
    private static final Pattern PIN = Pattern.compile("[1-9][0-9]{5}");
    private static final Pattern PIN_AREA = Pattern.compile("[1-9][0-9]{2}xxx");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 80)
    private String name;

    /** Used in shop page links, e.g. /shop.html?s=lakshmi-stores. Unique. */
    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShopCategory category = ShopCategory.KIRANA;

    @Column(nullable = false, length = 15)
    private String phone;

    @Column(name = "address_line", nullable = false, length = 250)
    private String addressLine;

    @Column(nullable = false, length = 6)
    private String pincode;

    /** Comma-separated: "560073,560076,560xxx". Always includes the shop's own PIN code. */
    @Column(name = "service_pincodes", nullable = false, length = 400)
    private String servicePincodes;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime = LocalTime.of(8, 0);

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime = LocalTime.of(21, 0);

    @Column(name = "offers_delivery", nullable = false)
    private boolean offersDelivery = true;

    @Column(name = "offers_pickup", nullable = false)
    private boolean offersPickup = true;

    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee = new BigDecimal("30.00");

    /** Orders at or above this amount get free delivery. Null = delivery is never free. */
    @Column(name = "free_delivery_above", precision = 10, scale = 2)
    private BigDecimal freeDeliveryAbove;

    @Column(name = "min_order_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    /** The owner can pause new orders (e.g. closed for a festival) without leaving the platform. */
    @Column(name = "accepting_orders", nullable = false)
    private boolean acceptingOrders = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ShopStatus status = ShopStatus.PENDING;

    /** Why an admin rejected or suspended the shop. Shown to the owner. */
    @Column(name = "status_reason", length = 300)
    private String statusReason;

    /** Shop photo or logo uploaded by the owner ("/api/images/12"). Optional. */
    @Column(name = "logo_url", length = 100)
    private String logoUrl;

    /** Optional map position, used only to show buyers how far away the shop is. */
    private Double latitude;
    private Double longitude;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Version
    private Long version;

    public Shop() {
    }

    // ---------- Rules ----------

    /** Can buyers see this shop and order from it right now (ignoring opening hours)? */
    @JsonIgnore
    public boolean isVisibleToBuyers() {
        return status == ShopStatus.APPROVED && owner != null && !owner.isSuspended();
    }

    public boolean isOpenAt(LocalTime time) {
        return !time.isBefore(openTime) && time.isBefore(closeTime);
    }

    @JsonIgnore
    public boolean isOpenNow() {
        return isOpenAt(LocalTime.now(SHOP_ZONE));
    }

    public boolean servesPincode(String pin) {
        if (pin == null || !PIN.matcher(pin).matches()) {
            return false;
        }
        for (String entry : getServicePincodeList()) {
            if (entry.equals(pin) || (entry.endsWith("xxx") && pin.startsWith(entry.substring(0, 3)))) {
                return true;
            }
        }
        return false;
    }

    public List<String> getServicePincodeList() {
        if (servicePincodes == null || servicePincodes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(servicePincodes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public static boolean isValidPincode(String pin) {
        return pin != null && PIN.matcher(pin).matches();
    }

    /**
     * Cleans a list typed by the owner ("560073, 560076\n560xxx") into the stored form.
     * Returns null if any entry is not a 6-digit PIN code or a "560xxx" area.
     */
    public static String normalisePincodes(String ownPincode, String typed) {
        Set<String> codes = new LinkedHashSet<>();
        codes.add(ownPincode);
        if (typed != null) {
            for (String raw : typed.split("[,\\s]+")) {
                String code = raw.trim().toLowerCase();
                if (code.isEmpty()) continue;
                if (!PIN.matcher(code).matches() && !PIN_AREA.matcher(code).matches()) {
                    return null;
                }
                codes.add(code);
            }
        }
        return String.join(",", codes);
    }

    // ---------- Getters and setters ----------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
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
    public boolean isAcceptingOrders() { return acceptingOrders; }
    public void setAcceptingOrders(boolean acceptingOrders) { this.acceptingOrders = acceptingOrders; }
    public ShopStatus getStatus() { return status; }
    public void setStatus(ShopStatus status) { this.status = status; }
    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String statusReason) { this.statusReason = statusReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public Long getVersion() { return version; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    /** Straight-line distance in km (haversine formula), or null if either position is unknown. */
    public Double distanceKmFrom(Double lat, Double lng) {
        if (latitude == null || longitude == null || lat == null || lng == null) {
            return null;
        }
        double dLat = Math.toRadians(lat - latitude);
        double dLng = Math.toRadians(lng - longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(lat)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double km = 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(km * 10) / 10.0;
    }
}
