package com.suryaprasanth.grocery.dto;

import com.suryaprasanth.grocery.model.Fulfilment;
import jakarta.validation.constraints.Size;

import java.util.HashMap;
import java.util.Map;

/** Asks the server for the current bill (optionally with a coupon, delivery/pickup choices and PIN code). */
public class QuoteRequest {

    @Size(max = 30, message = "Coupon code is too long")
    private String couponCode;

    /** Delivery or pickup per shop id, e.g. {"12": "PICKUP"}. Shops not listed use delivery when they offer it. */
    private Map<Long, Fulfilment> fulfilment = new HashMap<>();

    /** Optional: when given, the bill also says which shops can't deliver there. */
    @Size(max = 6, message = "Enter a valid 6-digit PIN code")
    private String pincode;

    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public Map<Long, Fulfilment> getFulfilment() { return fulfilment; }
    public void setFulfilment(Map<Long, Fulfilment> fulfilment) { this.fulfilment = fulfilment == null ? new HashMap<>() : fulfilment; }
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
}
