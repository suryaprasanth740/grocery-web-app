package com.suryaprasanth.grocery.repository;

import com.suryaprasanth.grocery.model.Order;
import com.suryaprasanth.grocery.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Order> findAllByOrderByCreatedAtDesc();

    Optional<Order> findByUserIdAndRequestId(Long userId, String requestId);

    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime before);

    /** How many live (not cancelled / failed) orders this user placed with a coupon. */
    long countByUserIdAndCouponCodeIgnoreCaseAndStatusNotIn(Long userId, String couponCode,
                                                            Collection<OrderStatus> ignored);
}
