package com.healthcare.activitytracker.repository.projection;

import com.healthcare.activitytracker.model.enums.ActivitySource;

/** Interface projection for per-source activity counts. */
public interface SourceCountProjection {
  ActivitySource getSource();

  Long getCount();
}
