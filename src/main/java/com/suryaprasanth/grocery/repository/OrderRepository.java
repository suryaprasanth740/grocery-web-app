package com.suryaprasanth.grocery.repository;

import com.suryaprasanth.grocery.model.Order;
import com.suryaprasanth.grocery.model.OrderStatus;
import com.suryaprasanth.grocery.model.Shop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Order> findAllByOrderByCreatedAtDesc();

    /** Old FreshCart orders kept their request id on the order itself. */
    Optional<Order> findByUserIdAndRequestId(Long userId, String requestId);

    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime before);

    // ---------- Coupon use: one checkout counts once, however many shops it was split into ----------

    @Query("select count(distinct o.checkout.id) from Order o where o.user.id = :userId and o.checkout is not null"
            + " and upper(o.couponCode) = upper(:code) and o.status not in :ignored")
    long countCheckoutsUsingCoupon(@Param("userId") Long userId, @Param("code") String code,
                                   @Param("ignored") Collection<OrderStatus> ignored);

    @Query("select count(o) from Order o where o.user.id = :userId and o.checkout is null"
            + " and upper(o.couponCode) = upper(:code) and o.status not in :ignored")
    long countOldOrdersUsingCoupon(@Param("userId") Long userId, @Param("code") String code,
                                   @Param("ignored") Collection<OrderStatus> ignored);

    // ---------- Seller side: only ever this shop's orders ----------

    @Query(value = "select o from Order o where o.shop.id = :shopId and o.status in :statuses",
            countQuery = "select count(o) from Order o where o.shop.id = :shopId and o.status in :statuses")
    Page<Order> findForShop(@Param("shopId") Long shopId, @Param("statuses") Collection<OrderStatus> statuses,
                            Pageable pageable);

    @Query("select o from Order o where o.id = :id and o.shop.id = :shopId")
    Optional<Order> findForShop(@Param("id") Long id, @Param("shopId") Long shopId);

    @Query("select count(o) from Order o where o.shop.id = :shopId and o.status in :statuses")
    long countForShop(@Param("shopId") Long shopId, @Param("statuses") Collection<OrderStatus> statuses);

    @Query("select o from Order o where o.shop.id = :shopId and o.createdAt >= :since and o.status in :statuses order by o.createdAt desc")
    List<Order> findForShopSince(@Param("shopId") Long shopId, @Param("since") LocalDateTime since,
                                 @Param("statuses") Collection<OrderStatus> statuses);

    // ---------- One-time upgrades of FreshCart rows ----------

    @Transactional
    @Modifying
    @Query("update Order o set o.shop = :shop where o.shop is null")
    int assignShopWhereMissing(@Param("shop") Shop shop);

    @Transactional
    @Modifying
    @Query(value = "update orders set status = 'PREPARING' where status = 'PACKED'", nativeQuery = true)
    int renamePackedToPreparing();

    // ---------- Admin: all shops, optional filters ----------

    @Query(value = "select o from Order o where (:status is null or o.status = :status) and (:shopId is null or o.shop.id = :shopId)",
            countQuery = "select count(o) from Order o where (:status is null or o.status = :status) and (:shopId is null or o.shop.id = :shopId)")
    Page<Order> searchForAdmin(@Param("status") OrderStatus status, @Param("shopId") Long shopId, Pageable pageable);

    @Query("select o from Order o where o.createdAt >= :since and o.status in :statuses")
    List<Order> findPlacedSince(@Param("since") LocalDateTime since, @Param("statuses") Collection<OrderStatus> statuses);
}
