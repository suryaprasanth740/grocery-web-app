package com.suryaprasanth.grocery.dto;

import com.suryaprasanth.grocery.model.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A buyer reporting a shop (productId empty) or one of its products. */
public class ReportRequest {

    @NotNull(message = "Choose the shop you're reporting")
    private Long shopId;

    private Long productId;

    @NotNull(message = "Choose a reason")
    private ReportReason reason;

    @Size(max = 500, message = "Details must be 500 characters or less")
    private String details;

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public ReportReason getReason() { return reason; }
    public void setReason(ReportReason reason) { this.reason = reason; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
