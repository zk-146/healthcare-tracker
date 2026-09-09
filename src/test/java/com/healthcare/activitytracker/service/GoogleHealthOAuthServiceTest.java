package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.activitytracker.config.GoogleHealthProperties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

/** Covers the CSRF {@code state} lifecycle: single use, expiry, and eviction. */
class GoogleHealthOAuthServiceTest {

  private final AtomicLong clock = new AtomicLong(1_000_000L);
  private GoogleHealthOAuthService service;

  @BeforeEach
  void setUp() {
    service =
        new GoogleHealthOAuthService(new GoogleHealthProperties(), new ObjectMapper(), clock::get);
  }

  private String issueStateFor(UUID userId) {
    String url = service.buildAuthorizationUrl(userId);
    String state =
        UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");
    assertThat(state).isNotBlank();
    return state;
  }

  @Test
  void stateRoundTripsToTheIssuingUser() {
    UUID userId = UUID.randomUUID();
    assertThat(service.consumeStateToUserId(issueStateFor(userId))).isEqualTo(userId);
  }

  @Test
  void stateCannotBeReplayed() {
    String state = issueStateFor(UUID.randomUUID());
    service.consumeStateToUserId(state);

    assertThatThrownBy(() -> service.consumeStateToUserId(state))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsUnknownAndNullState() {
    assertThatThrownBy(() -> service.consumeStateToUserId("never-issued"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> service.consumeStateToUserId(null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsStateOlderThanTheTtlEvenBeforeTheSweepRuns() {
    String state = issueStateFor(UUID.randomUUID());
    clock.addAndGet(GoogleHealthOAuthService.STATE_TTL_MS + 1);

    assertThatThrownBy(() -> service.consumeStateToUserId(state))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void evictionDropsAbandonedStates() {
    String abandoned = issueStateFor(UUID.randomUUID());
    clock.addAndGet(GoogleHealthOAuthService.STATE_TTL_MS + 1);

    UUID freshUser = UUID.randomUUID();
    String fresh = issueStateFor(freshUser);
    service.evictExpiredStates();

    // The abandoned entry is gone; the one still inside its window survives the sweep.
    assertThatThrownBy(() -> service.consumeStateToUserId(abandoned))
        .isInstanceOf(IllegalStateException.class);
    assertThat(service.consumeStateToUserId(fresh)).isEqualTo(freshUser);
  }
}
