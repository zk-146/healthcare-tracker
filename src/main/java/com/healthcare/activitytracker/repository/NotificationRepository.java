package com.healthcare.activitytracker.repository;

import com.healthcare.activitytracker.model.entity.Notification;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Data access for {@link Notification} records. */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  /** Returns all notifications for a user, newest first. */
  Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  /** Finds a notification by ID scoped to a specific user. */
  Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

  /** Counts unread notifications for a user. */
  long countByUserIdAndReadFalse(UUID userId);

  /** Bulk-marks every unread notification for a user as read. */
  @Modifying
  @Query("UPDATE Notification n SET n.read = true WHERE n.user.id = :userId AND n.read = false")
  void markAllReadForUser(@Param("userId") UUID userId);
}
