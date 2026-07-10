package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.exception.CsvImportException;
import com.healthcare.activitytracker.model.dto.CsvImportResponse;
import com.healthcare.activitytracker.model.enums.ActivitySource;
import com.healthcare.activitytracker.model.integration.ImportedWorkout;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Imports Fitbit {@code dailyActivity_merged.csv} exports — the format used by the Kaggle "FitBit
 * Fitness Tracker Data" dataset — into the authenticated user's activity history.
 *
 * <p>Each row is a single day's summary for one Fitbit participant, not a labelled workout, so
 * every imported row is tagged {@code ActivityType.WALKING} (steps/distance being the dominant
 * signal in the file) and {@code ActivitySource.CSV_IMPORT}. Rows are de-duplicated by combining
 * the CSV's {@code Id} and {@code ActivityDate} columns into the activity's {@code externalId}, so
 * re-uploading the same file (or an overlapping one) is a no-op for rows already imported.
 */
@Service
public class FitbitCsvImportService {

  private static final Logger log = LoggerFactory.getLogger(FitbitCsvImportService.class);

  private static final DateTimeFormatter US_DATE =
      DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US);

  private static final Set<String> REQUIRED_COLUMNS =
      Set.of(
          "Id",
          "ActivityDate",
          "TotalSteps",
          "TotalDistance",
          "VeryActiveMinutes",
          "FairlyActiveMinutes",
          "LightlyActiveMinutes",
          "Calories");

  private static final String DEVICE_LABEL = "fitbit-csv-import";

  /** Row-level errors are echoed back in the response, capped so it can't grow unbounded. */
  private static final int MAX_REPORTED_ERRORS = 50;

  private final ActivityService activityService;

  public FitbitCsvImportService(ActivityService activityService) {
    this.activityService = activityService;
  }

  /**
   * Parses the uploaded CSV and imports each row as an activity for {@code userId}.
   *
   * @throws CsvImportException if the file is empty, unreadable, or missing required columns
   */
  public CsvImportResponse importDailyActivity(UUID userId, MultipartFile file) {
    if (file.isEmpty()) {
      throw new CsvImportException("Uploaded file is empty");
    }

    CSVFormat format =
        CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreHeaderCase(true)
            .setTrim(true)
            .build();

    int totalRows = 0;
    int imported = 0;
    int duplicates = 0;
    List<String> errors = new ArrayList<>();

    try (InputStreamReader reader =
            new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
        CSVParser parser = format.parse(reader)) {

      requireColumns(parser);

      for (CSVRecord record : parser) {
        totalRows++;
        try {
          ImportedWorkout workout = toImportedWorkout(record);
          boolean created =
              activityService.importWorkout(
                  userId, workout, DEVICE_LABEL, ActivitySource.CSV_IMPORT);
          if (created) {
            imported++;
          } else {
            duplicates++;
          }
        } catch (RuntimeException ex) {
          if (errors.size() < MAX_REPORTED_ERRORS) {
            errors.add("Row " + record.getRecordNumber() + ": " + ex.getMessage());
          }
          log.warn(
              "Skipping unparseable Fitbit CSV row {}: {}",
              record.getRecordNumber(),
              ex.getMessage());
        }
      }
    } catch (IOException ex) {
      throw new CsvImportException("Failed to read CSV file: " + ex.getMessage());
    }

    log.info(
        "Fitbit CSV import complete for user {}: {} rows, {} imported, {} duplicates, {} failed",
        userId,
        totalRows,
        imported,
        duplicates,
        totalRows - imported - duplicates);

    return CsvImportResponse.builder()
        .fileName(file.getOriginalFilename())
        .totalRows(totalRows)
        .imported(imported)
        .duplicatesSkipped(duplicates)
        .failed(totalRows - imported - duplicates)
        .errors(errors)
        .build();
  }

  private void requireColumns(CSVParser parser) {
    List<String> headers = parser.getHeaderNames();
    for (String required : REQUIRED_COLUMNS) {
      if (headers.stream().noneMatch(required::equalsIgnoreCase)) {
        throw new CsvImportException(
            "Missing required column '"
                + required
                + "' — expected a Fitbit dailyActivity_merged.csv export");
      }
    }
  }

  private ImportedWorkout toImportedWorkout(CSVRecord record) {
    String id = requireValue(record, "Id");
    String rawDate = requireValue(record, "ActivityDate");
    LocalDate activityDate = parseDate(rawDate);

    int veryActiveMinutes = parseInt(record, "VeryActiveMinutes");
    int fairlyActiveMinutes = parseInt(record, "FairlyActiveMinutes");
    int lightlyActiveMinutes = parseInt(record, "LightlyActiveMinutes");

    return ImportedWorkout.builder()
        .externalId("fitbit-csv:" + id + ":" + rawDate)
        .rawType("WALK")
        .startedAt(activityDate.atStartOfDay())
        .endedAt(activityDate.atTime(23, 59, 59))
        .durationMinutes(veryActiveMinutes + fairlyActiveMinutes + lightlyActiveMinutes)
        .distanceKm(parseDouble(record, "TotalDistance"))
        .caloriesBurned(parseDouble(record, "Calories"))
        .steps(parseInt(record, "TotalSteps"))
        .build();
  }

  private LocalDate parseDate(String raw) {
    try {
      return LocalDate.parse(raw, US_DATE);
    } catch (DateTimeParseException usFormatFailed) {
      try {
        return LocalDate.parse(raw);
      } catch (DateTimeParseException isoFormatFailed) {
        throw new IllegalArgumentException("Unrecognised ActivityDate value: " + raw);
      }
    }
  }

  private String requireValue(CSVRecord record, String column) {
    String value = record.get(column);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing value for " + column);
    }
    return value.trim();
  }

  private int parseInt(CSVRecord record, String column) {
    String value = record.get(column);
    if (value == null || value.isBlank()) {
      return 0;
    }
    return Integer.parseInt(value.trim());
  }

  private double parseDouble(CSVRecord record, String column) {
    String value = record.get(column);
    if (value == null || value.isBlank()) {
      return 0.0;
    }
    return Double.parseDouble(value.trim());
  }
}
