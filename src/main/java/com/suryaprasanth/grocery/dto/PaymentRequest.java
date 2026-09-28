package com.suryaprasanth.grocery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Result of the DEMO UPI payment screen. In a real app this would come from the payment
 * gateway (Razorpay / PhonePe) as a signed webhook, never from the browser.
 */
public class PaymentRequest {

    @NotBlank(message = "outcome is required")
    @Pattern(regexp = "SUCCESS|FAILED", message = "outcome must be SUCCESS or FAILED")
    private String outcome;

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
}
