package com.suryaprasanth.grocery.repository;

import com.suryaprasanth.grocery.model.OrderItem;
import com.suryaprasanth.grocery.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /** Has this buyer received this product in at least one order? (Only then may they review it.) */
    @Query("select count(i) > 0 from OrderItem i where i.productId = :productId and i.order.user.id = :userId"
            + " and i.order.status = :status")
    boolean boughtWithStatus(@Param("productId") Long productId, @Param("userId") Long userId,
                             @Param("status") OrderStatus status);
}
