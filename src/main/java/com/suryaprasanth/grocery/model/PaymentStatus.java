package com.suryaprasanth.grocery.model;

public enum PaymentStatus {
    /** UPI order waiting for the customer to pay. */
    PENDING,
    /** Cash on delivery: collected when the order is delivered. */
    COD_PENDING,
    PAID,
    FAILED,
    /** Money was taken but the order could not go ahead, so it is returned. */
    REFUNDED,
    /** Order cancelled before any money was taken. */
    NOT_CHARGED
}
