package com.suryaprasanth.grocery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** An admin adding or renaming a product category. */
public class CategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(max = 60, message = "Category name must be 60 characters or less")
    private String name;

    @Size(max = 10, message = "Icon must be one emoji")
    private String icon;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
}
