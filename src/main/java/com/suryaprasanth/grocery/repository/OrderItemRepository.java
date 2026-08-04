package com.suryaprasanth.grocery.repository;

import com.suryaprasanth.grocery.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
