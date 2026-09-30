package com.suryaprasanth.grocery.dto;

import com.suryaprasanth.grocery.model.Product;

import java.math.BigDecimal;
import java.util.List;

/**
 * Budget mode numbers for the current month (Indian time).
 *
 * @param budget         the monthly budget, or null if budget mode is off
 * @param spent          money spent on orders this month (cancelled / failed orders and refunds not counted)
 * @param remaining      budget - spent (null without a budget)
 * @param cartTotal      what the current cart would cost, delivery included
 * @param afterCart      remaining - cartTotal; negative means this cart goes over budget
 * @param status         NO_BUDGET, OK, NEAR (80%+ used after this cart) or OVER
 * @param swaps          cheaper alternatives for items in the cart
 * @param possibleSaving total saving if every swap is taken
 */
public record BudgetSummary(BigDecimal budget, String month, BigDecimal spent, int ordersThisMonth,
                            BigDecimal remaining, Integer percentUsed, BigDecimal cartTotal,
                            BigDecimal afterCart, String status, List<CategorySpend> byCategory,
                            List<Swap> swaps, BigDecimal possibleSaving) {

    public record CategorySpend(String category, BigDecimal amount) {
    }

    public record Swap(Long fromProductId, String fromName, Product to, int quantity, BigDecimal saving) {
    }
}
