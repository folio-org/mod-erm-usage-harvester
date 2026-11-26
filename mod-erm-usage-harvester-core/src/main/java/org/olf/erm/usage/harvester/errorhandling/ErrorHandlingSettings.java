package org.olf.erm.usage.harvester.errorhandling;

import java.util.Objects;
import org.folio.rest.jaxrs.model.ErrorHandling;

/**
 * The error handling settings of a harvest run, with every option that is not set replaced by its
 * default.
 *
 * @param maxFailedAttempts months whose failed report has reached this number of failed attempts
 *     are no longer fetched
 */
public record ErrorHandlingSettings(int maxFailedAttempts) {

  /**
   * Resolves the given, possibly partial, settings against {@link ErrorHandlingDefaults}.
   *
   * @param errorHandling the stored settings; options that are not set get their default
   * @return the resolved settings
   */
  public static ErrorHandlingSettings of(final ErrorHandling errorHandling) {
    final var maxFailedAttempts =
        Objects.requireNonNullElse(
            errorHandling.getMaxFailedAttempts(), ErrorHandlingDefaults.MAX_FAILED_ATTEMPTS);

    // This can easily be extended, once we need to read more than one setting
    return new ErrorHandlingSettings(maxFailedAttempts);
  }
}
