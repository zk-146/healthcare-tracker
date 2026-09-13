package com.healthcare.activitytracker.controller;

import com.healthcare.activitytracker.model.dto.DeepSeekApiKeyRequest;
import com.healthcare.activitytracker.model.dto.DeepSeekStatusResponse;
import com.healthcare.activitytracker.service.DeepSeekConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for the owner's own DeepSeek API key (Profile &gt; AI settings), used instead of the
 * shared local Ollama instance when {@code app.ai.provider=deepseek}.
 */
@Tag(
    name = "DeepSeek Integration",
    description = "Configure the current user's own DeepSeek API key")
@RestController
@RequestMapping("/api/v1/integrations/deepseek")
public class DeepSeekIntegrationController {

  private final DeepSeekConnectionService connectionService;

  /**
   * Whether {@code DeepSeekClient} is the active {@code AiTextClient}. Mirrors its exact,
   * case-sensitive {@code @ConditionalOnProperty} match, so a typo that falls through to {@code
   * NoopAiTextClient} is reported as inactive too.
   */
  private final boolean active;

  public DeepSeekIntegrationController(
      DeepSeekConnectionService connectionService,
      @Value("${app.ai.provider:ollama}") String aiProvider) {
    this.connectionService = connectionService;
    this.active = "deepseek".equals(aiProvider);
  }

  /** Reports whether a key is on file and whether the server uses it. Never returns the key. */
  @Operation(summary = "Get DeepSeek integration status")
  @GetMapping("/status")
  public ResponseEntity<DeepSeekStatusResponse> status(Authentication auth) {
    UUID userId = (UUID) auth.getPrincipal();
    boolean connected = connectionService.isConnected(userId);
    return ResponseEntity.ok(
        DeepSeekStatusResponse.builder().connected(connected).active(active).build());
  }

  /** Saves (or replaces) the current user's DeepSeek API key. */
  @Operation(summary = "Save the current user's DeepSeek API key")
  @PutMapping
  public ResponseEntity<DeepSeekStatusResponse> save(
      Authentication auth, @Valid @RequestBody DeepSeekApiKeyRequest request) {
    UUID userId = (UUID) auth.getPrincipal();
    connectionService.saveApiKey(userId, request.getApiKey());
    return ResponseEntity.ok(
        DeepSeekStatusResponse.builder().connected(true).active(active).build());
  }

  /** Removes the current user's DeepSeek API key. */
  @Operation(summary = "Remove the current user's DeepSeek API key")
  @DeleteMapping
  public ResponseEntity<Void> disconnect(Authentication auth) {
    UUID userId = (UUID) auth.getPrincipal();
    connectionService.disconnect(userId);
    return ResponseEntity.noContent().build();
  }
}
