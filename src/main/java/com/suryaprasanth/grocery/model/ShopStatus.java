package com.suryaprasanth.grocery.model;

/**
 * Where a shop is in its life on NammaBasket.
 *
 *   PENDING   -> just applied; the owner can log in and edit details, but buyers can't see it
 *   APPROVED  -> checked by an admin; visible to buyers and can receive orders
 *   REJECTED  -> an admin said no (with a reason); the owner can fix the details and resubmit
 *   SUSPENDED -> was approved, then paused by an admin; hidden from buyers
 */
public enum ShopStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED
}
