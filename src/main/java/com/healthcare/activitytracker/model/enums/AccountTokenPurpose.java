package com.healthcare.activitytracker.model.enums;

/** What an emailed single-use link is allowed to do. A token is only ever valid for its own. */
public enum AccountTokenPurpose {
  PASSWORD_RESET,
  EMAIL_VERIFICATION
}
