package com.suryaprasanth.grocery.model;

/**
 * Life cycle of an order.
 *
 * PAYMENT_PENDING -> PLACED -> PACKED -> OUT_FOR_DELIVERY -> DELIVERED
 *        |              |        |
 *        v              v        v
 *  PAYMENT_FAILED    CANCELLED (customer can cancel only while PLACED; admin also while PACKED)
 */
public enum OrderStatus {
    PAYMENT_PENDING,
    PLACED,
    PACKED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED,
    PAYMENT_FAILED
}
