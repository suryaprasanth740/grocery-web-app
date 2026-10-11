package com.suryaprasanth.grocery.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** "Sell on NammaBasket" form: the owner's account plus the shop details. */
public class SellerRegisterRequest extends ShopDetailsRequest {

    @NotBlank(message = "Owner name is required")
    @Size(max = 100, message = "Owner name must be 100 characters or less")
    private String ownerName;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email")
    @Size(max = 150, message = "Email must be 150 characters or less")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Seller passwords must be 8 to 72 characters")
    private String password;

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
