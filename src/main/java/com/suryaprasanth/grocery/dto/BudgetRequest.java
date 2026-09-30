package com.suryaprasanth.grocery.dto;

import java.math.BigDecimal;

/** New monthly budget in rupees. null or 0 turns budget mode off. */
public class BudgetRequest {

    private BigDecimal amount;

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
