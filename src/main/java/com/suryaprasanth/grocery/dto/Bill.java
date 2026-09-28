package com.suryaprasanth.grocery.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The full bill, calculated ONLY on the server. The cart page, the checkout page and the
 * saved order all use this same calculation, so the customer never sees one total and
 * pays another. No hidden fees: every rupee is a line here.
 */
public record Bill(
        List<BillLine> lines,
        BigDecimal subtotal,
        BigDecimal discount,
        String couponCode,
        boolean couponApplied,
        String couponMessage,
        BigDecimal deliveryFee,
        BigDecimal freeDeliveryAbove,
        BigDecimal addForFreeDelivery,
        BigDecimal gstIncluded,
        BigDecimal total,
        boolean codAllowed,
        BigDecimal codMaxAmount,
        /** Things that block checkout, e.g. "Milk has expired" or "Only 2 Eggs left". */
        List<String> problems,
        /** Friendly notes such as "Tomatoes price changed from Rs 40 to Rs 45". */
        List<String> priceChangeNotes) {
}
