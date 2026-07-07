package com.healthcare.activitytracker.model.dto;

import com.healthcare.activitytracker.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PasswordChangeRequest {

  @NotBlank(message = "Current password is required")
  private String currentPassword;

  @NotBlank(message = "New password is required")
  @StrongPassword
  private String newPassword;
}
