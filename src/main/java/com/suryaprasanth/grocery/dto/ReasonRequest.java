package com.suryaprasanth.grocery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A reason the person on the other side will read, e.g. why a shop application was rejected. */
public class ReasonRequest {

    @NotBlank(message = "Give a reason. The shop owner will see it.")
    @Size(min = 5, max = 300, message = "Reason must be 5 to 300 characters")
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
