package com.suryaprasanth.grocery.dto;

import com.suryaprasanth.grocery.model.Shop;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * What the API sends about a shop. The owner and admins see everything; ownerName/ownerEmail
 * are null in views meant for buyers.
 */
public record ShopView(
        Long id,
        String name,
        String slug,
        String category,
        String categoryLabel,
        String phone,
        String addressLine,
        String pincode,
        List<String> servicePincodes,
        String openTime,
        String closeTime,
        boolean openNow,
        boolean offersDelivery,
        boolean offersPickup,
        BigDecimal deliveryFee,
        BigDecimal freeDeliveryAbove,
        BigDecimal minOrderAmount,
        boolean acceptingOrders,
        String status,
        String statusReason,
        LocalDateTime createdAt,
        LocalDateTime approvedAt,
        String ownerName,
        String ownerEmail,
        String logoUrl,
        Double latitude,
        Double longitude,
        /** Only in buyer search results when the buyer shared their location. */
        Double distanceKm,
        String gstin,
        boolean slotsEnabled,
        int slotCapacity,
        Double ratingAverage,
        int ratingCount
) {
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    public static ShopView of(Shop s, boolean includeOwner) {
        return of(s, includeOwner, null);
    }

    /** The public view for buyers: no owner details. */
    public static ShopView forBuyer(Shop s, Double distanceKm) {
        return of(s, false, distanceKm);
    }

    public static ShopView of(Shop s, boolean includeOwner, Double distanceKm) {
        return new ShopView(
                s.getId(), s.getName(), s.getSlug(), s.getCategory().name(), s.getCategory().getLabel(),
                s.getPhone(), s.getAddressLine(), s.getPincode(), s.getServicePincodeList(),
                s.getOpenTime().format(HH_MM), s.getCloseTime().format(HH_MM), s.isOpenNow(),
                s.isOffersDelivery(), s.isOffersPickup(), s.getDeliveryFee(), s.getFreeDeliveryAbove(),
                s.getMinOrderAmount(), s.isAcceptingOrders(), s.getStatus().name(), s.getStatusReason(),
                s.getCreatedAt(), s.getApprovedAt(),
                includeOwner ? s.getOwner().getName() : null,
                includeOwner ? s.getOwner().getEmail() : null,
                s.getLogoUrl(), s.getLatitude(), s.getLongitude(), distanceKm,
                s.getGstin(), s.isSlotsEnabled(), s.getSlotCapacity(), s.getRatingAverage(), s.getRatingCount());
    }
}
