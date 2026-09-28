package com.suryaprasanth.grocery.repository;

import com.suryaprasanth.grocery.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByUserId(Long userId);

    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

    @Transactional
    void deleteByUserId(Long userId);

    /**
     * Deletes the whole cart in ONE SQL statement and returns how many rows were removed.
     * Used by checkout: if two checkouts run at the same time for the same user, only the
     * first one can delete the rows; the second one gets 0 and is rejected.
     */
    @Modifying
    @Query("delete from CartItem c where c.user.id = :userId")
    int deleteAllForUser(@Param("userId") Long userId);
}
