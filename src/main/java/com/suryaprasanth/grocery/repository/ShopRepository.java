package com.suryaprasanth.grocery.repository;

import com.suryaprasanth.grocery.model.Shop;
import com.suryaprasanth.grocery.model.ShopStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    @EntityGraph(attributePaths = "owner")
    Optional<Shop> findByOwnerId(Long ownerId);

    boolean existsBySlug(String slug);

    @EntityGraph(attributePaths = "owner")
    List<Shop> findByStatusOrderByCreatedAtAsc(ShopStatus status);

    @EntityGraph(attributePaths = "owner")
    List<Shop> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "owner")
    Optional<Shop> findWithOwnerById(Long id);

    long countByStatus(ShopStatus status);

    @EntityGraph(attributePaths = "owner")
    Optional<Shop> findBySlug(String slug);
}
