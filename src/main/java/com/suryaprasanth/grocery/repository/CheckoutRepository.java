package com.suryaprasanth.grocery.repository;

import com.suryaprasanth.grocery.model.Checkout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CheckoutRepository extends JpaRepository<Checkout, Long> {
    Optional<Checkout> findByUserIdAndRequestId(Long userId, String requestId);
}
