package com.suryaprasanth.grocery.controller;

import com.suryaprasanth.grocery.model.Coupon;
import com.suryaprasanth.grocery.repository.CouponRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponRepository couponRepository;

    public CouponController(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    /** Offers the customer can use right now (shown on the cart page). */
    @GetMapping
    public List<Coupon> available() {
        LocalDateTime now = LocalDateTime.now();
        return couponRepository.findByActiveTrueOrderByMinOrderAsc().stream()
                .filter(c -> !c.isExpired(now))
                .toList();
    }
}
