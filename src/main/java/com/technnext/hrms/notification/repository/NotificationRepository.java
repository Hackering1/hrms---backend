package com.technnext.hrms.notification.repository;

import com.technnext.hrms.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndIsReadFalse(UUID userId);

    /**
     * FIX: Added queries that include broadcast (userId IS NULL) notifications
     *      so privileged users (managers/HR/admin) also see system-wide alerts
     *      (e.g. "new regularization request raised") in their notification feed.
     */
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId OR n.userId IS NULL " +
           "ORDER BY n.createdAt DESC")
    List<Notification> findForPrivilegedUser(@Param("userId") UUID userId);

    @Query("SELECT n FROM Notification n WHERE (n.userId = :userId OR n.userId IS NULL) " +
           "AND n.isRead = false ORDER BY n.createdAt DESC")
    List<Notification> findUnreadForPrivilegedUser(@Param("userId") UUID userId);

    @Query("SELECT COUNT(n) FROM Notification n WHERE (n.userId = :userId OR n.userId IS NULL) " +
           "AND n.isRead = false")
    long countUnreadForPrivilegedUser(@Param("userId") UUID userId);
}