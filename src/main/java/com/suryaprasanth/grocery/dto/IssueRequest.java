package com.suryaprasanth.grocery.dto;

import com.suryaprasanth.grocery.model.IssueReason;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class IssueRequest {

    @NotNull(message = "Choose the item")
    private Long orderItemId;

    @NotNull(message = "Enter the quantity")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 100, message = "Quantity is too large")
    private Integer quantity;

    @NotNull(message = "Choose a reason")
    private IssueReason reason;

    @Size(max = 300, message = "Note must be 300 characters or less")
    private String note;

    public Long getOrderItemId() { return orderItemId; }
    public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public IssueReason getReason() { return reason; }
    public void setReason(IssueReason reason) { this.reason = reason; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
