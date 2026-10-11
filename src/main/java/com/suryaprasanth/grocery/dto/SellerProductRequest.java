package com.suryaprasanth.grocery.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** A seller adding or editing one of their products. Same fields as the admin form, plus brand, MRP and on/off. */
public class SellerProductRequest extends ProductRequest {

    @Size(max = 60, message = "Brand must be 60 characters or less")
    private String brand;

    @DecimalMin(value = "1.00", message = "MRP must be at least Rs 1")
    @DecimalMax(value = "100000.00", message = "MRP must be at most Rs 1,00,000")
    @Digits(integer = 6, fraction = 2, message = "MRP can have at most 2 decimal places")
    private BigDecimal mrp;

    private boolean active = true;

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public BigDecimal getMrp() { return mrp; }
    public void setMrp(BigDecimal mrp) { this.mrp = mrp; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
