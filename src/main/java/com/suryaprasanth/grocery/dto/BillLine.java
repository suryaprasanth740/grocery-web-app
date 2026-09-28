package com.suryaprasanth.grocery.dto;

import java.math.BigDecimal;

/** One row of the bill: a product, its quantity and price. */
public record BillLine(
        Long productId,
        String name,
        String unit,
        int quantity,
        BigDecimal price,
        BigDecimal priceWhenAdded,
        boolean priceChanged,
        BigDecimal lineTotal,
        int gstPercent,
        boolean expired,
        int stock) {
}
