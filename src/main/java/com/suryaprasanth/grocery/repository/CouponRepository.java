package com.suryaprasanth.grocery.repository;

import com.suryaprasanth.grocery.model.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCodeIgnoreCase(String code);

    List<Coupon> findByActiveTrueOrderByMinOrderAsc();
}
