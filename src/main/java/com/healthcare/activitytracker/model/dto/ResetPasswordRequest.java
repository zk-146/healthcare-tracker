package com.healthcare.activitytracker.model.dto;

import com.healthcare.activitytracker.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {

  /** The token from the emailed link. */
  @NotBlank(message = "Token is required")
  @Size(max = 200, message = "Token is not valid")
  private String token;

  @NotBlank(message = "New password is required")
  @StrongPassword
  private String newPassword;
}
