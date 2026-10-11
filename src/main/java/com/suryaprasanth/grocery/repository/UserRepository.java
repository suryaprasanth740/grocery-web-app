package com.suryaprasanth.grocery.repository;

import com.suryaprasanth.grocery.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByEmailKey(String emailKey);
    boolean existsByEmailKey(String emailKey);
    List<User> findByEmailKeyIsNull();
    Page<User> findByRole(String role, Pageable pageable);
    long countByRole(String role);

    /** One-time rename: FreshCart called customers "USER"; NammaBasket calls them "BUYER". */
    @Transactional
    @Modifying
    @Query(value = "update users set role = 'BUYER' where role = 'USER'", nativeQuery = true)
    int renameLegacyCustomerRole();
}
