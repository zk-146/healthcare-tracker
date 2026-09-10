package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.activitytracker.config.GoogleHealthProperties;
import com.healthcare.activitytracker.model.integration.ImportedWorkout;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The wire-format mapping in {@link GoogleHealthClient} (record -> {@link ImportedWorkout},
 * timestamp parsing) is the part with real branching logic; {@code fetchWorkoutsSince} itself is a
 * thin HTTP call with no test double available in this project, so it is exercised indirectly here
 * via its private helpers through reflection.
 */
class GoogleHealthClientTest {

  private GoogleHealthClient client;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    client = new GoogleHealthClient(new GoogleHealthProperties(), objectMapper);
  }

  private ImportedWorkout invokeToWorkout(String json) throws Exception {
    JsonNode node = objectMapper.readTree(json);
    Method method = GoogleHealthClient.class.getDeclaredMethod("toWorkout", JsonNode.class);
    method.setAccessible(true);
    return (ImportedWorkout) method.invoke(client, node);
  }

  private LocalDateTime invokeParseTime(String value) throws Exception {
    Method method = GoogleHealthClient.class.getDeclaredMethod("parseTime", String.class);
    method.setAccessible(true);
    return (LocalDateTime) method.invoke(null, value);
  }

  @Test
  void toWorkout_mapsFullRecordWithAllFields() throws Exception {
    String json =
        """
        {
          "name": "exercise-1",
          "exerciseType": "RUNNING",
          "startTime": "2026-01-01T08:00:00Z",
          "endTime": "2026-01-01T08:30:00Z",
          "distanceMeters": 5000.0,
          "totalCalories": 350.5,
          "steps": 6000,
          "averageHeartRate": 145
        }
        """;

    ImportedWorkout workout = invokeToWorkout(json);

    assertThat(workout).isNotNull();
    assertThat(workout.getExternalId()).isEqualTo("exercise-1");
    assertThat(workout.getRawType()).isEqualTo("RUNNING");
    assertThat(workout.getStartedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 8, 0, 0));
    assertThat(workout.getEndedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 8, 30, 0));
    assertThat(workout.getDurationMinutes()).isEqualTo(30);
    assertThat(workout.getDistanceKm()).isEqualTo(5.0);
    assertThat(workout.getCaloriesBurned()).isEqualTo(350.5);
    assertThat(workout.getSteps()).isEqualTo(6000);
    assertThat(workout.getHeartRateAvg()).isEqualTo(145);
  }

  @Test
  void toWorkout_returnsNullWhenStartTimeMissing() throws Exception {
    String json = "{\"name\": \"exercise-2\", \"exerciseType\": \"WALKING\"}";

    ImportedWorkout workout = invokeToWorkout(json);

    assertThat(workout).isNull();
  }

  @Test
  void toWorkout_leavesDurationNullWhenEndTimeMissing() throws Exception {
    String json = "{\"name\": \"exercise-3\", \"startTime\": \"2026-01-01T08:00:00Z\"}";

    ImportedWorkout workout = invokeToWorkout(json);

    assertThat(workout).isNotNull();
    assertThat(workout.getEndedAt()).isNull();
    assertThat(workout.getDurationMinutes()).isNull();
  }

  @Test
  void toWorkout_leavesOptionalNumericFieldsNullWhenAbsent() throws Exception {
    String json = "{\"name\": \"exercise-4\", \"startTime\": \"2026-01-01T08:00:00Z\"}";

    ImportedWorkout workout = invokeToWorkout(json);

    assertThat(workout.getDistanceKm()).isNull();
    assertThat(workout.getCaloriesBurned()).isNull();
    assertThat(workout.getHeartRateAvg()).isNull();
    assertThat(workout.getSteps()).isNull();
  }

  @Test
  void toWorkout_ignoresNonNumericValueForNumericField() throws Exception {
    String json =
        "{\"name\": \"exercise-5\", \"startTime\": \"2026-01-01T08:00:00Z\", \"steps\": \"not-a-number\"}";

    ImportedWorkout workout = invokeToWorkout(json);

    assertThat(workout.getSteps()).isNull();
  }

  @Test
  void parseTime_parsesOffsetDateTimeAndNormalisesToUtc() throws Exception {
    LocalDateTime result = invokeParseTime("2026-01-01T10:00:00-02:00");

    assertThat(result).isEqualTo(LocalDateTime.of(2026, 1, 1, 12, 0, 0));
  }

  @Test
  void parseTime_fallsBackToLocalDateTimeFormat() throws Exception {
    LocalDateTime result = invokeParseTime("2026-01-01T10:00:00");

    assertThat(result).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0, 0));
  }

  @Test
  void parseTime_returnsNullForNullOrBlank() throws Exception {
    assertThat(invokeParseTime(null)).isNull();
    assertThat(invokeParseTime("   ")).isNull();
  }

  @Test
  void parseTime_returnsNullForUnparsableValue() throws Exception {
    assertThat(invokeParseTime("not-a-timestamp")).isNull();
  }
}
