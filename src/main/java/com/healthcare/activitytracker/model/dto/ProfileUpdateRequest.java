package com.healthcare.activitytracker.model.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import lombok.Data;

@Data
public class ProfileUpdateRequest {

  @Size(min = 1, max = 150, message = "Full name must be between 1 and 150 characters")
  private String fullName;

  /**
   * Must be a past date — future birth dates indicate a data-entry error. Note: allows dates as far
   * back as Java's LocalDate.MIN; if stricter bounds are required they can be added here.
   */
  @Past(message = "Date of birth must be in the past")
  private LocalDate dateOfBirth;

  @Size(max = 20, message = "Gender must not exceed 20 characters")
  private String gender;

  /** Realistic human height range: 0.1 cm – 300 cm */
  @DecimalMin(value = "0.1", message = "Height must be at least 0.1 cm")
  @DecimalMax(value = "300.0", message = "Height cannot exceed 300 cm")
  private Double heightCm;

  /** Realistic human weight range: 0.1 kg – 700 kg */
  @DecimalMin(value = "0.1", message = "Weight must be at least 0.1 kg")
  @DecimalMax(value = "700.0", message = "Weight cannot exceed 700 kg")
  private Double weightKg;
}
