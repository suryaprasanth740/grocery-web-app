package com.suryaprasanth.grocery.dto;

import jakarta.validation.constraints.Size;

/** Asks the server for the current bill (optionally with a coupon). */
public class QuoteRequest {

    @Size(max = 30, message = "Coupon code is too long")
    private String couponCode;

    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
}
