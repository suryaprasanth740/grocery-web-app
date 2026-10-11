package com.suryaprasanth.grocery.repository;

import com.suryaprasanth.grocery.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // "OrderByIdAsc": PostgreSQL does not keep insert order, so always sort (same list order every time).
    List<Product> findByCategoryIdOrderByIdAsc(Long categoryId);

    List<Product> findByNameContainingIgnoreCaseOrderByIdAsc(String keyword);

    boolean existsByNameIgnoreCase(String name);

    /**
     * Stock locking: reduce stock ONLY if enough is left, in a single atomic SQL UPDATE.
     * Returns 1 if the stock was reserved, 0 if not enough stock (someone else bought it).
     * Two customers buying the last item at the same moment can never both succeed.
     */
    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.stock = p.stock - :qty where p.id = :id and p.stock >= :qty")
    int reserveStock(@Param("id") Long id, @Param("qty") int qty);

    /** Puts stock back when an order is cancelled or its payment fails. */
    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.stock = p.stock + :qty where p.id = :id")
    int releaseStock(@Param("id") Long id, @Param("qty") int qty);

    /** Moves products made before shops existed into the given shop. */
    @org.springframework.transaction.annotation.Transactional
    @Modifying
    @Query("update Product p set p.shop = :shop where p.shop is null")
    int assignShopWhereMissing(@Param("shop") com.suryaprasanth.grocery.model.Shop shop);

    // ---------- What buyers may see: product switched on, shop approved, owner not suspended ----------

    String BUYABLE = "(p.active is null or p.active = true) and s.status = com.suryaprasanth.grocery.model.ShopStatus.APPROVED"
            + " and (o.accountStatus is null or o.accountStatus = 'ACTIVE')";

    @Query("select p from Product p join fetch p.shop s join s.owner o where " + BUYABLE + " order by p.id")
    List<Product> findBuyable();

    @Query("select p from Product p join fetch p.shop s join s.owner o where " + BUYABLE
            + " and s.id = :shopId order by p.id")
    List<Product> findBuyableByShop(@Param("shopId") Long shopId);

    /** Buyer search with optional filters. Any filter left null is ignored. */
    @Query(value = "select p from Product p join p.shop s join s.owner o where " + BUYABLE
            + " and (:shopId is null or s.id = :shopId)"
            + " and (:categoryId is null or p.category.id = :categoryId)"
            + " and (:q is null or lower(p.name) like :q or lower(p.brand) like :q)",
            countQuery = "select count(p) from Product p join p.shop s join s.owner o where " + BUYABLE
            + " and (:shopId is null or s.id = :shopId)"
            + " and (:categoryId is null or p.category.id = :categoryId)"
            + " and (:q is null or lower(p.name) like :q or lower(p.brand) like :q)")
    org.springframework.data.domain.Page<Product> searchBuyable(@Param("q") String likePattern,
                                                                @Param("categoryId") Long categoryId,
                                                                @Param("shopId") Long shopId,
                                                                org.springframework.data.domain.Pageable pageable);

    // ---------- Seller's own products ----------

    @Query(value = "select p from Product p where p.shop.id = :shopId"
            + " and (:q is null or lower(p.name) like :q or lower(p.brand) like :q)"
            + " and (:categoryId is null or p.category.id = :categoryId)",
            countQuery = "select count(p) from Product p where p.shop.id = :shopId"
            + " and (:q is null or lower(p.name) like :q or lower(p.brand) like :q)"
            + " and (:categoryId is null or p.category.id = :categoryId)")
    org.springframework.data.domain.Page<Product> searchByShop(@Param("shopId") Long shopId,
                                                               @Param("q") String likePattern,
                                                               @Param("categoryId") Long categoryId,
                                                               org.springframework.data.domain.Pageable pageable);

    java.util.Optional<Product> findByIdAndShopId(Long id, Long shopId);

    @Query("select count(p) from Product p where p.shop.id = :shopId and (p.active is null or p.active = true) and p.stock = 0")
    long countOutOfStock(@Param("shopId") Long shopId);

    @Query("select p from Product p where p.shop.id = :shopId and (p.active is null or p.active = true) and p.stock <= :limit order by p.stock, p.name")
    List<Product> findLowStock(@Param("shopId") Long shopId, @Param("limit") int limit);
}
