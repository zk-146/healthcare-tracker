package com.healthcare.activitytracker.model.enums;

/**
 * Categories of security- and data-relevant events captured in the compliance audit log. Stored as
 * the {@code event_type} column value in the {@code audit_log} table.
 */
public enum AuditEventType {
  LOGIN_SUCCESS,
  LOGIN_FAILURE,
  LOGOUT,
  REGISTER,
  PROFILE_VIEW,
  PROFILE_UPDATE,
  ACTIVITY_CREATE,
  ACTIVITY_UPDATE,
  ACTIVITY_DELETE,
  DATA_EXPORT,
  ACCOUNT_DELETE,
  EMAIL_VERIFIED,
  PASSWORD_RESET_REQUEST,
  PASSWORD_RESET
}
