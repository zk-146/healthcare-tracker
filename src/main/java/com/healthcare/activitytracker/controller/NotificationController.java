package com.healthcare.activitytracker.controller;

import com.healthcare.activitytracker.model.dto.NotificationResponse;
import com.healthcare.activitytracker.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for reading and managing in-app notifications.
 *
 * <p>All endpoints are scoped to the authenticated user; notifications from other users are never
 * exposed.
 */
@Tag(name = "Notifications", description = "Retrieve and manage in-app notifications")
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

  private final NotificationService notificationService;

  public NotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  /**
   * Returns a paginated list of notifications for the authenticated user, newest first. The {@code
   * unreadCount} field in each page response is not included here; use {@code GET
   * /notifications/unread-count} for a lightweight badge count.
   */
  @Operation(summary = "List notifications (newest first)")
  @GetMapping
  public ResponseEntity<Page<NotificationResponse>> list(
      Authentication auth, @PageableDefault(size = 20) Pageable pageable) {
    UUID userId = (UUID) auth.getPrincipal();
    return ResponseEntity.ok(notificationService.getNotifications(userId, pageable));
  }

  /** Returns the number of unread notifications — useful for badge counts in UIs. */
  @Operation(summary = "Count unread notifications")
  @GetMapping("/unread-count")
  public ResponseEntity<Long> unreadCount(Authentication auth) {
    UUID userId = (UUID) auth.getPrincipal();
    return ResponseEntity.ok(notificationService.countUnread(userId));
  }

  /** Marks a single notification as read. Returns 404 if the notification does not belong to the user. */
  @Operation(summary = "Mark a notification as read")
  @PostMapping("/{id}/read")
  public ResponseEntity<Void> markRead(Authentication auth, @PathVariable UUID id) {
    UUID userId = (UUID) auth.getPrincipal();
    notificationService.markRead(userId, id);
    return ResponseEntity.noContent().build();
  }

  /** Marks all notifications for the authenticated user as read. */
  @Operation(summary = "Mark all notifications as read")
  @PostMapping("/read-all")
  public ResponseEntity<Void> markAllRead(Authentication auth) {
    UUID userId = (UUID) auth.getPrincipal();
    notificationService.markAllRead(userId);
    return ResponseEntity.noContent().build();
  }
}
