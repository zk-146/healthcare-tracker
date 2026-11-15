package com.healthcare.activitytracker.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyEmailRequest {

  /** The token from the emailed link. */
  @NotBlank(message = "Token is required")
  @Size(max = 200, message = "Token is not valid")
  private String token;
}
