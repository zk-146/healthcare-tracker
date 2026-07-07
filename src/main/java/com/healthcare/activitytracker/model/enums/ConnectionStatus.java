package com.healthcare.activitytracker.model.enums;

/** Lifecycle state of an external integration connection (e.g. Google Health). */
public enum ConnectionStatus {
  /** Tokens are valid; the scheduled sync may run. */
  CONNECTED,

  /**
   * The refresh token has been revoked or expired (in Testing mode this happens roughly every 7
   * days). The owner must re-authorize before syncing can resume.
   */
  NEEDS_RECONNECT
}
