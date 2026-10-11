package com.suryaprasanth.grocery.repository;

import com.suryaprasanth.grocery.model.StoredImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredImageRepository extends JpaRepository<StoredImage, Long> {
    boolean existsByIdAndShopId(Long id, Long shopId);
}
