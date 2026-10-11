package com.suryaprasanth.grocery.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One shop's part of the basket. Each shop has its own delivery fee, free-delivery amount and
 * minimum order, and becomes its own order at checkout.
 */
public record ShopBill(
        Long shopId,
        String shopName,
        String shopSlug,
        /** DELIVERY or PICKUP, as chosen (or the default when not chosen). */
        String fulfilment,
        boolean offersDelivery,
        boolean offersPickup,
        boolean acceptingOrders,
        boolean openNow,
        String openTime,
        String closeTime,
        List<BillLine> lines,
        BigDecimal subtotal,
        /** This shop's share of a platform coupon, split by subtotal. */
        BigDecimal discount,
        BigDecimal deliveryFee,
        BigDecimal freeDeliveryAbove,
        BigDecimal addForFreeDelivery,
        BigDecimal minOrderAmount,
        BigDecimal gstIncluded,
        BigDecimal total,
        /** Things that block checkout for this shop, e.g. "Add Rs 40 more (minimum order Rs 100)". */
        List<String> problems
) {
}
