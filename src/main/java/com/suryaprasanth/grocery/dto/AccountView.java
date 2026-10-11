package com.suryaprasanth.grocery.dto;

import com.suryaprasanth.grocery.model.Shop;
import com.suryaprasanth.grocery.model.User;

import java.time.LocalDateTime;

/** One account in the admin's account list. Never includes the password hash. */
public record AccountView(
        Long id,
        String name,
        String email,
        String phone,
        String role,
        String accountStatus,
        LocalDateTime createdAt,
        Long shopId,
        String shopName,
        String shopStatus
) {
    public static AccountView of(User u, Shop shop) {
        return new AccountView(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole(),
                u.getAccountStatus().name(), u.getCreatedAt(),
                shop == null ? null : shop.getId(),
                shop == null ? null : shop.getName(),
                shop == null ? null : shop.getStatus().name());
    }
}
