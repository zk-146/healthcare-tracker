package com.healthcare.activitytracker.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResendVerificationRequest {
  @NotBlank(message = "Email is required")
  @Email(message = "Invalid email format")
  @Size(max = 255, message = "Email cannot exceed 255 characters")
  private String email;
}
