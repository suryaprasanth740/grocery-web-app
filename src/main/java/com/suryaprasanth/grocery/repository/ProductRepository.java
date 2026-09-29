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
}
