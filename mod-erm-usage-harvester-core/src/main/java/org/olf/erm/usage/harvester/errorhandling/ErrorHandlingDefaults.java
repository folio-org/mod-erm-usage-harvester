package org.olf.erm.usage.harvester.errorhandling;

import org.folio.rest.jaxrs.model.ErrorHandling;
import org.folio.rest.jaxrs.model.RateOption;
import org.folio.rest.jaxrs.model.WaitOption;

/** Values that apply for every error handling option that has not been set. */
public final class ErrorHandlingDefaults {

  public static final int MAX_FAILED_ATTEMPTS = 5;
  public static final int CODE_1000_WAIT_MINUTES = 60;
  public static final int CODE_1010_WAIT_MINUTES = 60;
  public static final int CODE_1011_WAIT_MINUTES = 30;
  public static final int CODE_1020_NUMBER_OF_REQUESTS = 1;
  public static final int CODE_1020_TIMEFRAME_SECONDS = 1;

  /** Returns a new instance with every option set to its default. */
  public static ErrorHandling get() {
    return new ErrorHandling()
        .withMaxFailedAttempts(MAX_FAILED_ATTEMPTS)
        .withCode1000(new WaitOption().withWaitMinutes(CODE_1000_WAIT_MINUTES))
        .withCode1010(new WaitOption().withWaitMinutes(CODE_1010_WAIT_MINUTES))
        .withCode1011(new WaitOption().withWaitMinutes(CODE_1011_WAIT_MINUTES))
        .withCode1020(
            new RateOption()
                .withNumberOfRequests(CODE_1020_NUMBER_OF_REQUESTS)
                .withTimeframeSeconds(CODE_1020_TIMEFRAME_SECONDS));
  }

  private ErrorHandlingDefaults() {}
}
