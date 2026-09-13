package com.healthcare.activitytracker.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeepSeekApiKeyRequest {

  @NotBlank(message = "API key must not be blank")
  @Size(max = 500, message = "API key is too long")
  private String apiKey;
}
