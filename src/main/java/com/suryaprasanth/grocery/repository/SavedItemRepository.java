package com.suryaprasanth.grocery.repository;

import com.suryaprasanth.grocery.model.SavedItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SavedItemRepository extends JpaRepository<SavedItem, Long> {

    @Query("select s from SavedItem s where s.user.id = :userId order by s.createdAt desc, s.id desc")
    List<SavedItem> forUser(@Param("userId") Long userId);

    @Query("select s from SavedItem s where s.user.id = :userId and s.product.id = :productId")
    Optional<SavedItem> find(@Param("userId") Long userId, @Param("productId") Long productId);

    @Query("select count(s) from SavedItem s where s.user.id = :userId")
    long countForUser(@Param("userId") Long userId);
}
