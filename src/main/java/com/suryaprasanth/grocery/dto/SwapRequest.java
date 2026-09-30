package com.suryaprasanth.grocery.dto;

import jakarta.validation.constraints.NotNull;

/** Replace one product in the cart with another (e.g. a cheaper one). */
public class SwapRequest {

    @NotNull(message = "fromProductId is required")
    private Long fromProductId;

    @NotNull(message = "toProductId is required")
    private Long toProductId;

    public Long getFromProductId() { return fromProductId; }
    public void setFromProductId(Long fromProductId) { this.fromProductId = fromProductId; }
    public Long getToProductId() { return toProductId; }
    public void setToProductId(Long toProductId) { this.toProductId = toProductId; }
}
