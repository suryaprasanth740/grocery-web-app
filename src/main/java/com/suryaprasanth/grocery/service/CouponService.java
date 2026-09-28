package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.model.Coupon;
import com.suryaprasanth.grocery.model.CouponType;
import com.suryaprasanth.grocery.model.OrderStatus;
import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.repository.CouponRepository;
import com.suryaprasanth.grocery.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CouponService {

    /** Orders in these states do not "use up" a coupon (the customer can use it again). */
    public static final List<OrderStatus> NOT_COUNTED = List.of(OrderStatus.CANCELLED, OrderStatus.PAYMENT_FAILED);

    private final CouponRepository couponRepository;
    private final OrderRepository orderRepository;

    public CouponService(CouponRepository couponRepository, OrderRepository orderRepository) {
        this.couponRepository = couponRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * Checks every coupon rule and returns the coupon, or throws 400 with a clear reason.
     * Rules: must exist, be active, not be expired, meet the minimum order, and not be over-used.
     */
    public Coupon validate(String rawCode, User user, BigDecimal subtotal) {
        String code = rawCode == null ? "" : rawCode.trim();
        if (code.isEmpty()) {
            throw bad("Enter a coupon code.");
        }
        Coupon coupon = couponRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> bad("Coupon " + code.toUpperCase() + " does not exist."));
        if (!coupon.isActive()) {
            throw bad("Coupon " + coupon.getCode() + " is no longer active.");
        }
        if (coupon.isExpired(LocalDateTime.now())) {
            throw bad("Coupon " + coupon.getCode() + " has expired.");
        }
        if (subtotal.compareTo(coupon.getMinOrder()) < 0) {
            BigDecimal more = coupon.getMinOrder().subtract(subtotal).setScale(2, RoundingMode.HALF_UP);
            throw bad("Add items worth Rs " + more.toPlainString() + " more to use " + coupon.getCode()
                    + " (minimum order Rs " + coupon.getMinOrder().toPlainString() + ").");
        }
        long used = orderRepository.countByUserIdAndCouponCodeIgnoreCaseAndStatusNotIn(
                user.getId(), coupon.getCode(), NOT_COUNTED);
        if (used >= coupon.getPerUserLimit()) {
            throw bad("You have already used coupon " + coupon.getCode() + ".");
        }
        return coupon;
    }

    /** The discount in rupees. It can never be more than the item total (no Rs 0 or negative bills). */
    public BigDecimal discountFor(Coupon coupon, BigDecimal subtotal) {
        BigDecimal discount;
        if (coupon.getType() == CouponType.PERCENT) {
            discount = subtotal.multiply(coupon.getValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (coupon.getMaxDiscount() != null && discount.compareTo(coupon.getMaxDiscount()) > 0) {
                discount = coupon.getMaxDiscount();
            }
        } else {
            discount = coupon.getValue();
        }
        return discount.min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
