package com.suryaprasanth.grocery.repository;

import com.suryaprasanth.grocery.model.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("select n from Notification n where n.user.id = :userId order by n.createdAt desc, n.id desc")
    List<Notification> latestFor(@Param("userId") Long userId, Pageable page);

    @Query("select count(n) from Notification n where n.user.id = :userId and (n.read is null or n.read = false)")
    long countUnread(@Param("userId") Long userId);

    @Query("select n from Notification n where n.id = :id and n.user.id = :userId")
    Optional<Notification> findOwned(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying
    @Query("update Notification n set n.read = true where n.user.id = :userId and (n.read is null or n.read = false)")
    int markAllRead(@Param("userId") Long userId);
}
