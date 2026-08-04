package com.suryaprasanth.grocery.repository;

import com.suryaprasanth.grocery.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
