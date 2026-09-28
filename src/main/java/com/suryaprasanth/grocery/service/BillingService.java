package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.config.ShopRules;
import com.suryaprasanth.grocery.dto.Bill;
import com.suryaprasanth.grocery.dto.BillLine;
import com.suryaprasanth.grocery.model.CartItem;
import com.suryaprasanth.grocery.model.Coupon;
import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.model.User;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class BillingService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ShopRules rules;
    private final CouponService couponService;

    public BillingService(ShopRules rules, CouponService couponService) {
        this.rules = rules;
        this.couponService = couponService;
    }

    /**
     * Builds the bill for a cart.
     *
     * @param strictCoupon true at checkout: an invalid coupon stops the order with an error.
     *                     false on the cart page: the bill is shown without the coupon, plus the reason.
     */
    public Bill buildBill(User user, List<CartItem> items, String couponCode, boolean strictCoupon) {
        List<BillLine> lines = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        List<String> priceNotes = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal gstBeforeDiscount = BigDecimal.ZERO;

        for (CartItem item : items) {
            Product p = item.getProduct();
            BigDecimal lineTotal = p.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            subtotal = subtotal.add(lineTotal);

            // Prices already include GST, so the GST part of a price is: price x rate / (100 + rate)
            int gst = p.getGstPercent();
            if (gst > 0) {
                gstBeforeDiscount = gstBeforeDiscount.add(lineTotal.multiply(BigDecimal.valueOf(gst))
                        .divide(HUNDRED.add(BigDecimal.valueOf(gst)), 4, RoundingMode.HALF_UP));
            }

            if (p.isExpired()) {
                problems.add(p.getName() + " has expired or is too close to its expiry date. Please remove it.");
            } else if (p.getStock() <= 0) {
                problems.add(p.getName() + " is out of stock. Please remove it.");
            } else if (p.getStock() < item.getQuantity()) {
                problems.add("Only " + p.getStock() + " " + p.getName() + " left. Please reduce the quantity.");
            }
            if (item.isPriceChanged()) {
                priceNotes.add(p.getName() + " price changed from Rs " + item.getPriceWhenAdded().toPlainString()
                        + " to Rs " + p.getPrice().toPlainString() + ".");
            }

            lines.add(new BillLine(p.getId(), p.getName(), p.getUnit(), item.getQuantity(), p.getPrice(),
                    item.getPriceWhenAdded(), item.isPriceChanged(), money(lineTotal), gst, p.isExpired(), p.getStock()));
        }

        // ---- Coupon ----
        BigDecimal discount = BigDecimal.ZERO;
        String appliedCode = null;
        String couponMessage = null;
        if (couponCode != null && !couponCode.isBlank() && !items.isEmpty()) {
            try {
                Coupon coupon = couponService.validate(couponCode, user, subtotal);
                discount = couponService.discountFor(coupon, subtotal);
                appliedCode = coupon.getCode();
                couponMessage = coupon.getCode() + " applied. You save Rs " + discount.toPlainString() + ".";
            } catch (ResponseStatusException e) {
                if (strictCoupon) {
                    throw e;
                }
                couponMessage = e.getReason();
            }
        }

        // ---- Delivery fee: free above the limit (after discount) ----
        BigDecimal afterDiscount = subtotal.subtract(discount);
        BigDecimal deliveryFee = BigDecimal.ZERO;
        BigDecimal addForFree = BigDecimal.ZERO;
        if (!items.isEmpty() && afterDiscount.compareTo(rules.getFreeDeliveryAbove()) < 0) {
            deliveryFee = rules.getDeliveryFee();
            addForFree = rules.getFreeDeliveryAbove().subtract(afterDiscount);
        }

        // GST shrinks in the same ratio as the discount
        BigDecimal gstIncluded = BigDecimal.ZERO;
        if (subtotal.signum() > 0) {
            gstIncluded = gstBeforeDiscount.multiply(afterDiscount).divide(subtotal, 2, RoundingMode.HALF_UP);
        }

        BigDecimal total = afterDiscount.add(deliveryFee);
        boolean codAllowed = total.compareTo(rules.getCodMaxAmount()) <= 0;

        return new Bill(lines, money(subtotal), money(discount), appliedCode, appliedCode != null, couponMessage,
                money(deliveryFee), money(rules.getFreeDeliveryAbove()), money(addForFree), money(gstIncluded),
                money(total), codAllowed, money(rules.getCodMaxAmount()), problems, priceNotes);
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
