package com.healthcare.activitytracker.controller;

import com.healthcare.activitytracker.model.dto.CsvImportResponse;
import com.healthcare.activitytracker.service.FitbitCsvImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Activity Import", description = "Bulk-import activities from external data exports")
@RestController
@RequestMapping("/api/v1/activities/import")
public class ActivityImportController {

  private final FitbitCsvImportService fitbitCsvImportService;

  public ActivityImportController(FitbitCsvImportService fitbitCsvImportService) {
    this.fitbitCsvImportService = fitbitCsvImportService;
  }

  /**
   * Imports a Fitbit {@code dailyActivity_merged.csv} export (e.g. the Kaggle "FitBit Fitness
   * Tracker Data" dataset) as activity records for the authenticated user. Rows already imported
   * are skipped; malformed rows are skipped and reported rather than failing the whole upload.
   */
  @Operation(summary = "Import a Fitbit dailyActivity_merged.csv export")
  @PostMapping(value = "/fitbit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<CsvImportResponse> importFitbitDailyActivity(
      Authentication auth, @RequestParam("file") MultipartFile file) {
    UUID userId = (UUID) auth.getPrincipal();
    CsvImportResponse response = fitbitCsvImportService.importDailyActivity(userId, file);
    return ResponseEntity.ok(response);
  }
}
