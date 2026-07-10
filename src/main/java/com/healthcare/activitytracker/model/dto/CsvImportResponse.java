package com.healthcare.activitytracker.model.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** Summary of a bulk CSV import: how many rows were imported, skipped, or failed. */
@Getter
@Builder
public class CsvImportResponse {

  private final String fileName;
  private final int totalRows;
  private final int imported;
  private final int duplicatesSkipped;
  private final int failed;

  /** Row-level error messages, capped to a bounded prefix so the response stays small. */
  private final List<String> errors;
}
