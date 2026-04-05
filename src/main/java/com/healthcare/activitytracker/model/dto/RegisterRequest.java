package com.healthcare.activitytracker.model.dto;

import com.healthcare.activitytracker.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

  @NotBlank(message = "Email is required")
  @Email(message = "Invalid email format")
  @Size(max = 255, message = "Email cannot exceed 255 characters")
  private String email;

  @NotBlank(message = "Password is required")
  @StrongPassword
  private String password;

  @NotBlank(message = "Full name is required")
  @Size(max = 150, message = "Full name cannot exceed 150 characters")
  private String fullName;
}
