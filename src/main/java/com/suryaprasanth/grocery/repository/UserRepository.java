package com.suryaprasanth.grocery.repository;

import com.suryaprasanth.grocery.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByEmailKey(String emailKey);
    boolean existsByEmailKey(String emailKey);
    List<User> findByEmailKeyIsNull();
}
