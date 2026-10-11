package com.suryaprasanth.grocery.model;

/**
 * Who a user is on NammaBasket.
 * <ul>
 *   <li>BUYER  - shops for groceries (everyone who signs up on the normal page)</li>
 *   <li>SELLER - owns a kirana shop and sells through the platform</li>
 *   <li>ADMIN  - runs the marketplace; never created through a public page</li>
 * </ul>
 * Stored in the database as text in users.role.
 */
public enum Role {
    BUYER,
    SELLER,
    ADMIN;

    /** Reads the text from the database. "USER" is the old FreshCart name for a buyer. */
    public static Role from(String value) {
        if (value == null || value.isBlank() || "USER".equalsIgnoreCase(value)) {
            return BUYER;
        }
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return BUYER; // unknown text never gives extra rights
        }
    }
}
