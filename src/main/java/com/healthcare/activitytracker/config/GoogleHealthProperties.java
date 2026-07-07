package com.healthcare.activitytracker.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the Google Health API integration (Fitbit Charge 6 import).
 *
 * <p>Bound from {@code app.integrations.google-health.*}. The integration is disabled by default so
 * the application (and the test suite) runs without any Google credentials; set {@code
 * enabled=true} and supply the client credentials to activate it.
 */
@Component
@ConfigurationProperties(prefix = "app.integrations.google-health")
public class GoogleHealthProperties {

  /** Master switch. When false, the connect endpoints and scheduled sync are inert. */
  private boolean enabled = false;

  /** OAuth client id from the Google Cloud project. */
  private String clientId;

  /** OAuth client secret from the Google Cloud project. */
  private String clientSecret;

  /** Redirect URI registered on the OAuth client; must match exactly. */
  private String redirectUri = "http://localhost:8080/api/v1/integrations/google-health/callback";

  /** Google's OAuth 2.0 authorization endpoint. */
  private String authUri = "https://accounts.google.com/o/oauth2/v2/auth";

  /** Google's OAuth 2.0 token endpoint. */
  private String tokenUri = "https://oauth2.googleapis.com/token";

  /** Base URL of the Google Health API. */
  private String apiBaseUrl = "https://health.googleapis.com";

  /** Scopes requested at consent time. */
  private List<String> scopes =
      List.of(
          "https://www.googleapis.com/auth/health.exercise.read",
          "https://www.googleapis.com/auth/health.heartrate.read",
          "https://www.googleapis.com/auth/health.location.read");

  /** Passphrase used to derive the AES key that encrypts stored OAuth tokens. */
  private String tokenEncryptionKey = "change-me-token-encryption-key";

  /** Stored in {@code activities.device_id} for imported workouts. */
  private String deviceLabel = "fitbit-charge-6";

  /** How far back to look on the very first sync, when there is no watermark yet. */
  private int initialBackfillDays = 30;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(String clientSecret) {
    this.clientSecret = clientSecret;
  }

  public String getRedirectUri() {
    return redirectUri;
  }

  public void setRedirectUri(String redirectUri) {
    this.redirectUri = redirectUri;
  }

  public String getAuthUri() {
    return authUri;
  }

  public void setAuthUri(String authUri) {
    this.authUri = authUri;
  }

  public String getTokenUri() {
    return tokenUri;
  }

  public void setTokenUri(String tokenUri) {
    this.tokenUri = tokenUri;
  }

  public String getApiBaseUrl() {
    return apiBaseUrl;
  }

  public void setApiBaseUrl(String apiBaseUrl) {
    this.apiBaseUrl = apiBaseUrl;
  }

  public List<String> getScopes() {
    return scopes;
  }

  public void setScopes(List<String> scopes) {
    this.scopes = scopes;
  }

  public String getTokenEncryptionKey() {
    return tokenEncryptionKey;
  }

  public void setTokenEncryptionKey(String tokenEncryptionKey) {
    this.tokenEncryptionKey = tokenEncryptionKey;
  }

  public String getDeviceLabel() {
    return deviceLabel;
  }

  public void setDeviceLabel(String deviceLabel) {
    this.deviceLabel = deviceLabel;
  }

  public int getInitialBackfillDays() {
    return initialBackfillDays;
  }

  public void setInitialBackfillDays(int initialBackfillDays) {
    this.initialBackfillDays = initialBackfillDays;
  }
}
