package com.suryaprasanth.grocery.dto;

import com.suryaprasanth.grocery.model.Product;

import java.time.LocalDate;

/**
 * A product the customer buys regularly.
 *
 * @param everyDays       how often they usually buy it (null if bought only once)
 * @param daysLeft        days until they usually run out (negative = overdue, null if unknown)
 * @param status          DUE (run out by now), SOON (within 2 days), LATER, or BOUGHT_ONCE
 * @param typicalQuantity usual number of packs per order
 */
public record ReorderSuggestion(Product product, int typicalQuantity, Integer everyDays, LocalDate lastBought,
                                Integer daysLeft, String status, int timesBought) {
}
