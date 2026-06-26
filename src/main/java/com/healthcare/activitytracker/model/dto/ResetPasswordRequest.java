package com.healthcare.activitytracker.model.dto;

import com.healthcare.activitytracker.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResetPasswordRequest {

  @NotBlank(message = "Token is required")
  private String token;

  @NotBlank(message = "Password is required")
  @StrongPassword
  private String newPassword;
}
