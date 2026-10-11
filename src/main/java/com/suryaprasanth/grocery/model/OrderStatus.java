package com.suryaprasanth.grocery.model;

/**
 * Life cycle of one shop's order.
 *
 * DELIVERY: PLACED -> ACCEPTED -> PREPARING -> OUT_FOR_DELIVERY -> DELIVERED
 * PICKUP:   PLACED -> ACCEPTED -> PREPARING -> READY_FOR_PICKUP -> DELIVERED (handed over)
 *
 * Before PREPARING the buyer can cancel, and the shop can reject (with a reason): both end in CANCELLED.
 * UPI orders start as PAYMENT_PENDING and become PLACED once paid, or PAYMENT_FAILED.
 * PACKED is the old FreshCart step; existing PACKED orders are moved to PREPARING at startup.
 */
public enum OrderStatus {
    PAYMENT_PENDING,
    PLACED,
    ACCEPTED,
    PREPARING,
    /** @deprecated old FreshCart step, kept so old rows can still be read */
    @Deprecated
    PACKED,
    READY_FOR_PICKUP,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED,
    PAYMENT_FAILED
}
