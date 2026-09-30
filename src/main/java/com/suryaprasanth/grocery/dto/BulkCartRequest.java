package com.suryaprasanth.grocery.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** "Add all to cart" from a shopping list, recipe or festival kit. */
public class BulkCartRequest {

    @NotEmpty(message = "Nothing to add")
    @Size(max = 40, message = "You can add at most 40 different items at once")
    private List<@Valid CartRequest> items;

    public List<CartRequest> getItems() { return items; }
    public void setItems(List<CartRequest> items) { this.items = items; }
}
