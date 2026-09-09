package com.healthcare.activitytracker.util;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the {@code X-User-Timezone} request header (e.g. {@code America/New_York}) into a {@link
 * ZoneId}. Falls back to UTC when the header is absent, blank, or unrecognized.
 *
 * <p>Shared by every controller that needs the caller's own wall-clock context — summaries and
 * streak boundaries, and activity write validation (a submitted time must not be in the future
 * <em>for that user</em>, which a bare server-clock comparison cannot determine).
 */
public final class TimezoneResolver {

  private static final Logger log = LoggerFactory.getLogger(TimezoneResolver.class);

  private TimezoneResolver() {}

  public static ZoneId resolveZone(String timezone) {
    if (timezone == null || timezone.isBlank()) {
      return ZoneOffset.UTC;
    }
    try {
      return ZoneId.of(timezone);
    } catch (DateTimeException e) {
      log.warn("Unrecognized timezone '{}', defaulting to UTC", timezone);
      return ZoneOffset.UTC;
    }
  }
}
