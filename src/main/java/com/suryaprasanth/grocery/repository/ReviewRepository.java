package com.suryaprasanth.grocery.repository;

import com.suryaprasanth.grocery.model.Review;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query("select r from Review r where r.product.id = :productId and r.buyer.id = :buyerId")
    Optional<Review> findMine(@Param("productId") Long productId, @Param("buyerId") Long buyerId);

    @Query("select r from Review r where r.product.id = :productId and (r.hidden is null or r.hidden = false)"
            + " order by r.createdAt desc, r.id desc")
    List<Review> visibleForProduct(@Param("productId") Long productId, Pageable page);

    @Query("select r from Review r where r.shop.id = :shopId and (r.hidden is null or r.hidden = false)"
            + " order by r.createdAt desc, r.id desc")
    List<Review> visibleForShop(@Param("shopId") Long shopId, Pageable page);

    @Query("select r from Review r where r.buyer.id = :buyerId order by r.createdAt desc")
    List<Review> byBuyer(@Param("buyerId") Long buyerId);

    @Query("select r from Review r order by r.createdAt desc, r.id desc")
    List<Review> latest(Pageable page);
}
