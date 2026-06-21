package com.healthcare.activitytracker.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProfileResponse {
  private UUID id;
  private String email;
  private String fullName;
  private LocalDate dateOfBirth;
  private String gender;
  private Double heightCm;
  private Double weightKg;
  private String timezone;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
