package com.healthcare.activitytracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.activitytracker.config.GoogleHealthProperties;
import com.healthcare.activitytracker.model.integration.ImportedWorkout;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Thin REST client that reads workout/exercise records from the Google Health API and converts them
 * into provider-neutral {@link ImportedWorkout} objects.
 *
 * <p><b>Wire format:</b> all assumptions about the Google Health JSON layout are confined to the
 * constants and {@link #toWorkout(JsonNode)} method below. The exact endpoint path and field names
 * should be confirmed against the live Google Health API specification once the Google Cloud
 * project is set up — the surrounding OAuth, scheduling, mapping and persistence code does not
 * depend on those details. Parsing is deliberately defensive so an unexpected/missing field yields
 * a null value rather than failing the whole sync.
 */
@Service
public class GoogleHealthClient {

  private static final Logger log = LoggerFactory.getLogger(GoogleHealthClient.class);

  // --- Wire-format constants (confirm against live Google Health API spec) ---
  private static final String EXERCISE_PATH = "/v4/exercise";
  private static final String FIELD_RECORDS = "exerciseRecords";
  private static final String FIELD_ID = "name";
  private static final String FIELD_TYPE = "exerciseType";
  private static final String FIELD_START = "startTime";
  private static final String FIELD_END = "endTime";
  private static final String FIELD_DISTANCE_M = "distanceMeters";
  private static final String FIELD_CALORIES = "totalCalories";
  private static final String FIELD_STEPS = "steps";
  private static final String FIELD_HEART_RATE_AVG = "averageHeartRate";
  // --------------------------------------------------------------------------

  private final GoogleHealthProperties properties;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  public GoogleHealthClient(GoogleHealthProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.restClient = RestClient.create();
  }

  /**
   * Fetches workouts that started at or after {@code since}.
   *
   * @param accessToken a valid OAuth access token
   * @param since lower bound on workout start time
   * @return the imported workouts (empty if none / on a parse-level issue)
   */
  public List<ImportedWorkout> fetchWorkoutsSince(String accessToken, LocalDateTime since) {
    String uri =
        UriComponentsBuilder.fromUriString(properties.getApiBaseUrl() + EXERCISE_PATH)
            .queryParam("startTime", since.atOffset(ZoneOffset.UTC).toString())
            .build()
            .toUriString();

    String response =
        restClient
            .get()
            .uri(uri)
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .body(String.class);

    List<ImportedWorkout> workouts = new ArrayList<>();
    try {
      JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
      JsonNode records = root.path(FIELD_RECORDS);
      if (records.isArray()) {
        for (JsonNode record : records) {
          ImportedWorkout workout = toWorkout(record);
          if (workout != null) {
            workouts.add(workout);
          }
        }
      }
    } catch (Exception e) {
      log.error("Failed to parse Google Health response", e);
    }
    return workouts;
  }

  private ImportedWorkout toWorkout(JsonNode record) {
    LocalDateTime startedAt = parseTime(record.path(FIELD_START).asText(null));
    LocalDateTime endedAt = parseTime(record.path(FIELD_END).asText(null));
    if (startedAt == null) {
      log.warn("Skipping exercise record without a start time: {}", record.path(FIELD_ID).asText());
      return null;
    }

    Integer durationMinutes = null;
    if (endedAt != null) {
      durationMinutes = (int) Duration.between(startedAt, endedAt).toMinutes();
    }

    return ImportedWorkout.builder()
        .externalId(textOrNull(record, FIELD_ID))
        .rawType(textOrNull(record, FIELD_TYPE))
        .startedAt(startedAt)
        .endedAt(endedAt)
        .durationMinutes(durationMinutes)
        .distanceKm(metersToKm(doubleOrNull(record, FIELD_DISTANCE_M)))
        .caloriesBurned(doubleOrNull(record, FIELD_CALORIES))
        .heartRateAvg(intOrNull(record, FIELD_HEART_RATE_AVG))
        .steps(intOrNull(record, FIELD_STEPS))
        .build();
  }

  private static LocalDateTime parseTime(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return OffsetDateTime.parse(value).atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    } catch (DateTimeParseException e) {
      try {
        return LocalDateTime.parse(value);
      } catch (DateTimeParseException ex) {
        return null;
      }
    }
  }

  private static Double metersToKm(Double meters) {
    return meters == null ? null : meters / 1000.0;
  }

  private static String textOrNull(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? null : value.asText();
  }

  private static Double doubleOrNull(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() || !value.isNumber() ? null : value.asDouble();
  }

  private static Integer intOrNull(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() || !value.isNumber() ? null : value.asInt();
  }
}
