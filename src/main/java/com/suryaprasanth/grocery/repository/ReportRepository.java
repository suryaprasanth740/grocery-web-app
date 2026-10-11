package com.suryaprasanth.grocery.repository;

import com.suryaprasanth.grocery.model.Report;
import com.suryaprasanth.grocery.model.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByStatusOrderByCreatedAtAsc(ReportStatus status);

    List<Report> findAllByOrderByCreatedAtDesc();

    long countByStatus(ReportStatus status);

    /** How many open reports this buyer already has about exactly this shop/product (0 or 1). */
    @Query("select count(r) from Report r left join r.product p where r.reporter.id = :userId and r.shop.id = :shopId"
            + " and ((:productId is null and p is null) or p.id = :productId) and r.status = :status")
    long countOpenReports(@Param("userId") Long userId, @Param("shopId") Long shopId,
                          @Param("productId") Long productId, @Param("status") ReportStatus status);

    @Query("select count(r) from Report r where r.shop.id = :shopId")
    long countForShop(@Param("shopId") Long shopId);
}
