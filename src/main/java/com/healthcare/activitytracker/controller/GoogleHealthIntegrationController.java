package com.healthcare.activitytracker.controller;

import com.healthcare.activitytracker.config.GoogleHealthProperties;
import com.healthcare.activitytracker.model.dto.GoogleHealthStatusResponse;
import com.healthcare.activitytracker.model.entity.GoogleHealthConnection;
import com.healthcare.activitytracker.service.GoogleHealthConnectionService;
import com.healthcare.activitytracker.service.GoogleHealthOAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for linking the owner's Fitbit (Charge 6) account via the Google Health API.
 *
 * <p>Flow: {@code GET /connect} returns the Google consent URL → the owner approves in a browser →
 * Google redirects to {@code GET /callback}, which stores the tokens. The callback is the only
 * unauthenticated endpoint here (Google's redirect carries no JWT); it is protected instead by the
 * one-time CSRF {@code state}. Imports thereafter happen on the scheduled background sync.
 */
@Tag(
    name = "Google Health Integration",
    description = "Link a Fitbit account via the Google Health API")
@RestController
@RequestMapping("/api/v1/integrations/google-health")
public class GoogleHealthIntegrationController {

  private final GoogleHealthOAuthService oauthService;
  private final GoogleHealthConnectionService connectionService;
  private final GoogleHealthProperties properties;

  public GoogleHealthIntegrationController(
      GoogleHealthOAuthService oauthService,
      GoogleHealthConnectionService connectionService,
      GoogleHealthProperties properties) {
    this.oauthService = oauthService;
    this.connectionService = connectionService;
    this.properties = properties;
  }

  /** Returns the Google consent URL the owner should open to authorize access. */
  @Operation(summary = "Begin linking: get the Google authorization URL")
  @GetMapping("/connect")
  public ResponseEntity<Map<String, String>> connect(Authentication auth) {
    requireEnabled();
    UUID userId = (UUID) auth.getPrincipal();
    String url = oauthService.buildAuthorizationUrl(userId);
    return ResponseEntity.ok(Map.of("authorizationUrl", url));
  }

  /** OAuth redirect target. Unauthenticated; secured by the one-time {@code state}. */
  @Operation(summary = "OAuth callback (redirected to by Google)")
  @GetMapping("/callback")
  public ResponseEntity<String> callback(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String error) {
    requireEnabled();
    if (error != null) {
      return ResponseEntity.badRequest()
          .contentType(MediaType.TEXT_PLAIN)
          .body("Authorization failed: " + error);
    }
    connectionService.completeConnection(code, state);
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_PLAIN)
        .body("Fitbit (Google Health) connected. You can close this window.");
  }

  /** Reports whether the integration is linked and when it last synced. */
  @Operation(summary = "Get integration status")
  @GetMapping("/status")
  public ResponseEntity<GoogleHealthStatusResponse> status(Authentication auth) {
    UUID userId = (UUID) auth.getPrincipal();
    Optional<GoogleHealthConnection> connection = connectionService.findConnection(userId);
    GoogleHealthStatusResponse body =
        connection
            .map(
                c ->
                    GoogleHealthStatusResponse.builder()
                        .connected(true)
                        .status(c.getStatus())
                        .lastSyncedAt(c.getLastSyncedAt())
                        .build())
            .orElseGet(() -> GoogleHealthStatusResponse.builder().connected(false).build());
    return ResponseEntity.ok(body);
  }

  /** Unlinks the integration, deleting stored tokens. */
  @Operation(summary = "Disconnect the integration")
  @DeleteMapping
  public ResponseEntity<Void> disconnect(Authentication auth) {
    UUID userId = (UUID) auth.getPrincipal();
    connectionService.disconnect(userId);
    return ResponseEntity.noContent().build();
  }

  private void requireEnabled() {
    if (!properties.isEnabled()) {
      throw new org.springframework.web.server.ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Google Health integration is disabled");
    }
  }
}
