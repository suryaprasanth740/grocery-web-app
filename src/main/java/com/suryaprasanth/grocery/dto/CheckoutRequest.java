package com.suryaprasanth.grocery.dto;

import com.suryaprasanth.grocery.model.PaymentMethod;
import com.suryaprasanth.grocery.model.SubstitutionPreference;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CheckoutRequest {

    @NotBlank(message = "Shipping address is required")
    @Size(min = 10, max = 300, message = "Address must be between 10 and 300 characters")
    private String shippingAddress;

    @NotBlank(message = "PIN code is required")
    @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Enter a valid 6-digit PIN code")
    private String pincode;

    @NotNull(message = "Choose a payment method")
    private PaymentMethod paymentMethod;

    @Size(max = 30, message = "Coupon code is too long")
    private String couponCode;

    /** What to do if an item is out of stock while packing. Defaults to refund. */
    private SubstitutionPreference substitutionPreference = SubstitutionPreference.REFUND_ITEM;

    /** Random id made by the checkout page. Same id twice = same order (double-click safe). */
    @NotBlank(message = "requestId is required")
    @Size(max = 64, message = "requestId is too long")
    private String requestId;

    /** The total the customer saw. If the real total is different, we stop and show the new bill. */
    @NotNull(message = "expectedTotal is required")
    private BigDecimal expectedTotal;

    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }

    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }

    public SubstitutionPreference getSubstitutionPreference() { return substitutionPreference; }
    public void setSubstitutionPreference(SubstitutionPreference substitutionPreference) {
        this.substitutionPreference = substitutionPreference;
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public BigDecimal getExpectedTotal() { return expectedTotal; }
    public void setExpectedTotal(BigDecimal expectedTotal) { this.expectedTotal = expectedTotal; }
}
