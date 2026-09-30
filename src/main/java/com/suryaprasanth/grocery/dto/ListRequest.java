package com.suryaprasanth.grocery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The shopping list text the customer pasted or typed. */
public class ListRequest {

    @NotBlank(message = "Type or paste your shopping list first")
    @Size(max = 2000, message = "The list is too long (max 2000 characters)")
    private String text;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
