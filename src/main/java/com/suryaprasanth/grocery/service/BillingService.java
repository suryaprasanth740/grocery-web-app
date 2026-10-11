package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.config.ShopRules;
import com.suryaprasanth.grocery.dto.Bill;
import com.suryaprasanth.grocery.dto.BillLine;
import com.suryaprasanth.grocery.dto.ShopBill;
import com.suryaprasanth.grocery.model.CartItem;
import com.suryaprasanth.grocery.model.Coupon;
import com.suryaprasanth.grocery.model.Fulfilment;
import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.model.Shop;
import com.suryaprasanth.grocery.model.User;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the bill for a basket, on the server only.
 *
 * The basket is split by shop, because each shop sets its own delivery fee, free-delivery amount
 * and minimum order, and becomes its own order. A platform coupon is checked against the whole
 * basket and its discount is shared between the shops in proportion to their item totals, so a
 * later refund on one shop's order stays correct.
 */
@Service
public class BillingService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final ShopRules rules;
    private final CouponService couponService;

    public BillingService(ShopRules rules, CouponService couponService) {
        this.rules = rules;
        this.couponService = couponService;
    }

    /** Bill with default choices: delivery wherever the shop offers it, no PIN-code check. */
    public Bill buildBill(User user, List<CartItem> items, String couponCode, boolean strictCoupon) {
        return buildBill(user, items, couponCode, strictCoupon, Map.of(), null);
    }

    /**
     * @param strictCoupon true at checkout: an invalid coupon stops the order with an error.
     *                     false on the cart page: the bill is shown without the coupon, plus the reason.
     * @param choices      DELIVERY or PICKUP per shop id; missing shops get the default
     * @param pincode      when given, delivery shops that don't serve it are listed as problems
     */
    public Bill buildBill(User user, List<CartItem> items, String couponCode, boolean strictCoupon,
                          Map<Long, Fulfilment> choices, String pincode) {
        // ---- 1. Group the basket by shop, in the order the shops first appear ----
        Map<Long, List<CartItem>> byShop = new LinkedHashMap<>();
        Map<Long, Shop> shops = new LinkedHashMap<>();
        for (CartItem item : items) {
            Shop shop = item.getProduct().getShop();
            Long key = shop == null ? -1L : shop.getId();
            byShop.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
            if (shop != null) shops.put(key, shop);
        }

        // ---- 2. Lines, item totals and item problems per shop ----
        List<Group> groups = new ArrayList<>();
        List<String> priceNotes = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Map.Entry<Long, List<CartItem>> entry : byShop.entrySet()) {
            Group g = new Group(shops.get(entry.getKey()));
            for (CartItem item : entry.getValue()) {
                Product p = item.getProduct();
                BigDecimal lineTotal = p.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                g.subtotal = g.subtotal.add(lineTotal);
                // Prices already include GST, so the GST part of a price is: price x rate / (100 + rate)
                int gst = p.getGstPercent();
                if (gst > 0) {
                    g.gstBefore = g.gstBefore.add(lineTotal.multiply(BigDecimal.valueOf(gst))
                            .divide(HUNDRED.add(BigDecimal.valueOf(gst)), 4, RoundingMode.HALF_UP));
                }
                if (!p.isActive()) {
                    g.problems.add(p.getName() + " is no longer sold. Please remove it.");
                } else if (p.isExpired()) {
                    g.problems.add(p.getName() + " has expired or is too close to its expiry date. Please remove it.");
                } else if (p.getStock() <= 0) {
                    g.problems.add(p.getName() + " is out of stock. Please remove it.");
                } else if (p.getStock() < item.getQuantity()) {
                    g.problems.add("Only " + p.getStock() + " " + p.getName() + " left. Please reduce the quantity.");
                }
                if (item.isPriceChanged()) {
                    priceNotes.add(p.getName() + " price changed from Rs " + item.getPriceWhenAdded().toPlainString()
                            + " to Rs " + p.getPrice().toPlainString() + ".");
                }
                g.lines.add(new BillLine(p.getId(), p.getName(), p.getUnit(), item.getQuantity(), p.getPrice(),
                        item.getPriceWhenAdded(), item.isPriceChanged(), money(lineTotal), gst, p.isExpired(), p.getStock()));
            }
            subtotal = subtotal.add(g.subtotal);
            groups.add(g);
        }

        // ---- 3. Coupon on the whole basket ----
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
        shareDiscount(groups, discount, subtotal);

        // ---- 4. Per shop: fulfilment, delivery fee, minimum order, GST, total ----
        BigDecimal deliveryTotal = BigDecimal.ZERO;
        BigDecimal gstTotal = BigDecimal.ZERO;
        BigDecimal grandTotal = BigDecimal.ZERO;
        List<String> problems = new ArrayList<>();
        List<ShopBill> shopBills = new ArrayList<>();
        for (Group g : groups) {
            Shop shop = g.shop;
            Fulfilment f = shop == null ? Fulfilment.DELIVERY : chooseFulfilment(shop, choices.get(shop.getId()));
            if (shop == null) {
                g.problems.add(0, "Some items are no longer sold on NammaBasket. Please remove them.");
            } else {
                if (!shop.isVisibleToBuyers()) {
                    g.problems.add(0, shop.getName() + " is no longer on NammaBasket. Please remove its items.");
                } else if (!shop.isAcceptingOrders()) {
                    g.problems.add(0, shop.getName() + " isn't taking new orders right now. Remove its items or try later.");
                }
                Fulfilment wanted = choices.get(shop.getId());
                if (wanted == Fulfilment.DELIVERY && !shop.isOffersDelivery()) {
                    g.problems.add(shop.getName() + " doesn't deliver. Choose pickup for its items.");
                } else if (wanted == Fulfilment.PICKUP && !shop.isOffersPickup()) {
                    g.problems.add(shop.getName() + " doesn't offer pickup. Choose delivery for its items.");
                }
                if (f == Fulfilment.DELIVERY && pincode != null && Shop.isValidPincode(pincode) && !shop.servesPincode(pincode)) {
                    g.problems.add("Sorry, " + shop.getName() + " doesn't deliver to " + pincode
                            + ". Choose pickup or remove its items.");
                }
                if (g.subtotal.compareTo(shop.getMinOrderAmount()) < 0) {
                    g.problems.add("Add items worth Rs " + money(shop.getMinOrderAmount().subtract(g.subtotal)).toPlainString()
                            + " more from " + shop.getName() + " (minimum order Rs "
                            + money(shop.getMinOrderAmount()).toPlainString() + ").");
                }
            }

            BigDecimal afterDiscount = g.subtotal.subtract(g.discount);
            BigDecimal fee = BigDecimal.ZERO;
            BigDecimal addForFree = BigDecimal.ZERO;
            BigDecimal freeAbove = shop == null ? null : shop.getFreeDeliveryAbove();
            if (f == Fulfilment.DELIVERY && shop != null) {
                if (freeAbove == null) {
                    fee = shop.getDeliveryFee();
                } else if (afterDiscount.compareTo(freeAbove) < 0) {
                    fee = shop.getDeliveryFee();
                    addForFree = freeAbove.subtract(afterDiscount);
                }
            }
            // GST shrinks in the same ratio as the discount
            BigDecimal gst = BigDecimal.ZERO;
            if (g.subtotal.signum() > 0) {
                gst = g.gstBefore.multiply(afterDiscount).divide(g.subtotal, 2, RoundingMode.HALF_UP);
            }
            BigDecimal total = afterDiscount.add(fee);

            deliveryTotal = deliveryTotal.add(fee);
            gstTotal = gstTotal.add(gst);
            grandTotal = grandTotal.add(total);
            problems.addAll(g.problems);
            shopBills.add(new ShopBill(
                    shop == null ? null : shop.getId(), shop == null ? "Unavailable items" : shop.getName(),
                    shop == null ? null : shop.getSlug(), f.name(),
                    shop != null && shop.isOffersDelivery(), shop != null && shop.isOffersPickup(),
                    shop != null && shop.isAcceptingOrders(), shop != null && shop.isOpenNow(),
                    shop == null ? null : shop.getOpenTime().format(HH_MM), shop == null ? null : shop.getCloseTime().format(HH_MM),
                    g.lines, money(g.subtotal), money(g.discount), money(fee),
                    freeAbove == null ? null : money(freeAbove), money(addForFree),
                    shop == null ? null : money(shop.getMinOrderAmount()), money(gst), money(total), g.problems));
        }

        List<BillLine> allLines = new ArrayList<>();
        groups.forEach(g -> allLines.addAll(g.lines));
        boolean codAllowed = grandTotal.compareTo(rules.getCodMaxAmount()) <= 0;
        ShopBill single = shopBills.size() == 1 ? shopBills.get(0) : null;
        return new Bill(allLines, money(subtotal), money(discount), appliedCode, appliedCode != null, couponMessage,
                money(deliveryTotal),
                single == null ? null : single.freeDeliveryAbove(),
                single == null ? money(BigDecimal.ZERO) : single.addForFreeDelivery(),
                money(gstTotal), money(grandTotal), codAllowed, money(rules.getCodMaxAmount()),
                problems, priceNotes, shopBills);
    }

    /** What the buyer asked for if the shop offers it; otherwise delivery when offered, else pickup. */
    public static Fulfilment chooseFulfilment(Shop shop, Fulfilment wanted) {
        if (wanted == Fulfilment.PICKUP && shop.isOffersPickup()) return Fulfilment.PICKUP;
        if (wanted == Fulfilment.DELIVERY && shop.isOffersDelivery()) return Fulfilment.DELIVERY;
        return shop.isOffersDelivery() ? Fulfilment.DELIVERY : Fulfilment.PICKUP;
    }

    /**
     * Splits the discount by item total. Each share is rounded to paise and the last shop gets
     * whatever is left, so the shares always add up to exactly the discount.
     */
    private static void shareDiscount(List<Group> groups, BigDecimal discount, BigDecimal subtotal) {
        if (discount.signum() == 0 || subtotal.signum() == 0) return;
        BigDecimal given = BigDecimal.ZERO;
        for (int i = 0; i < groups.size(); i++) {
            Group g = groups.get(i);
            BigDecimal share = i == groups.size() - 1
                    ? discount.subtract(given)
                    : discount.multiply(g.subtotal).divide(subtotal, 2, RoundingMode.HALF_UP);
            g.discount = share.min(g.subtotal);
            given = given.add(g.discount);
        }
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Working totals for one shop while the bill is being built. */
    private static final class Group {
        final Shop shop;
        final List<BillLine> lines = new ArrayList<>();
        final List<String> problems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal gstBefore = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;

        Group(Shop shop) {
            this.shop = shop;
        }
    }
}
