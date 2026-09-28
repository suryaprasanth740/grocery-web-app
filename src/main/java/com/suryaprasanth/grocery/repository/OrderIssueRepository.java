package com.suryaprasanth.grocery.repository;

import com.suryaprasanth.grocery.model.OrderIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderIssueRepository extends JpaRepository<OrderIssue, Long> {

    List<OrderIssue> findAllByOrderByCreatedAtDesc();
}
