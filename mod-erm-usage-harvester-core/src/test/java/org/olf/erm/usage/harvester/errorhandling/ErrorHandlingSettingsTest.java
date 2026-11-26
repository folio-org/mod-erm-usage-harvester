package org.olf.erm.usage.harvester.errorhandling;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.rest.jaxrs.model.ErrorHandling;
import org.junit.jupiter.api.Test;

/** Test class for {@link ErrorHandlingSettings} */
class ErrorHandlingSettingsTest {

  @Test
  void testOptionNotSetGetsDefault() {
    assertThat(ErrorHandlingSettings.of(new ErrorHandling()).maxFailedAttempts())
        .isEqualTo(ErrorHandlingDefaults.MAX_FAILED_ATTEMPTS);
  }

  @Test
  void testOptionSetIsKept() {
    assertThat(
            ErrorHandlingSettings.of(new ErrorHandling().withMaxFailedAttempts(7))
                .maxFailedAttempts())
        .isEqualTo(7);
  }
}
