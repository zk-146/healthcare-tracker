package com.healthcare.activitytracker.model.dto;

import com.healthcare.activitytracker.model.enums.NotificationType;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** API response DTO for a single in-app notification. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NotificationResponse {

  private UUID id;
  private NotificationType type;
  private String title;
  private String body;
  private boolean read;
  private LocalDateTime createdAt;
}
