package org.olf.erm.usage.harvester.endpoints;

/** Shared constants for error handling in service endpoint implementations. */
public final class ErrorHandlingConstants {

  /**
   * Maximum length for error response bodies when included in error messages or logs. Longer
   * response bodies should be abbreviated.
   */
  public static final int MAX_ERROR_BODY_LENGTH = 2000;

  private ErrorHandlingConstants() {
    // Prevent instantiation
  }
}
