package com.suryaprasanth.grocery.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/** All business rules in one place. Values come from application.properties. */
@Component
public class ShopRules {

    @Value("${app.delivery.fee:30}")
    private BigDecimal deliveryFee;

    @Value("${app.delivery.free-above:500}")
    private BigDecimal freeDeliveryAbove;

    @Value("${app.delivery.pincode-prefixes:560}")
    private String pincodePrefixes;

    @Value("${app.delivery.eta:30-45 min}")
    private String deliveryEta;

    @Value("${app.cod.max-amount:3000}")
    private BigDecimal codMaxAmount;

    @Value("${app.cart.max-qty-per-item:20}")
    private int maxQtyPerItem;

    @Value("${app.upi.timeout-minutes:10}")
    private int upiTimeoutMinutes;

    @Value("${app.issue.window-hours:48}")
    private int issueWindowHours;

    public BigDecimal getDeliveryFee() { return deliveryFee; }

    public BigDecimal getFreeDeliveryAbove() { return freeDeliveryAbove; }

    public List<String> getPincodePrefixes() {
        return Arrays.stream(pincodePrefixes.split(",")).map(String::trim).filter(p -> !p.isEmpty()).toList();
    }

    public String getDeliveryEta() { return deliveryEta; }

    public BigDecimal getCodMaxAmount() { return codMaxAmount; }

    public int getMaxQtyPerItem() { return maxQtyPerItem; }

    public int getUpiTimeoutMinutes() { return upiTimeoutMinutes; }

    public int getIssueWindowHours() { return issueWindowHours; }
}
