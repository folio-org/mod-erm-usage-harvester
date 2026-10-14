package org.olf.erm.usage.harvester.endpoints;

import static org.olf.erm.usage.harvester.endpoints.CounterApiException.CounterExceptionDelivery.REPORT_HEADER;
import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingStrategy.DefaultErrorHandlingStrategy;

import org.folio.rest.jaxrs.model.UsageDataProvider;

/**
 * Error handling strategy for {@link CS50Impl}. It keeps the outcomes cs50 had before {@link
 * CounterApiException} was introduced, where they differ from {@link DefaultErrorHandlingStrategy}.
 *
 * <p>Deviations from the default (to be removed in a follow-up ticket):
 *
 * <ul>
 *   <li>A 1020 (or "too many requests") in a bare exception body is not throttled. Only HTTP 429
 *       is. A bare body with a success status gives up, as does one with an error status.
 * </ul>
 *
 * <p>Known difference from 5.2.x: a report header entry with a null {@code Message} (or with a null
 * {@code Code} and a non-matching {@code Message}) made the old check fail with a {@link
 * NullPointerException}, so the item gave up. Now it is handled like any other header exception.
 * Also, the "too many requests" alias in a report header now ignores case, so e.g. "Too Many
 * Requests" throttles instead of splitting and retrying.
 *
 * <p>The {@code failedReason} of a 429 with a COUNTER exception body names the body instead of
 * {@link TooManyRequestsException}'s message.
 */
final class CS50ErrorHandlingStrategy extends DefaultErrorHandlingStrategy {

  CS50ErrorHandlingStrategy(UsageDataProvider usageDataProvider) {
    super(usageDataProvider);
  }

  /** Deviation from the default: in a bare exception body, only HTTP 429 counts. */
  @Override
  protected boolean isTooManyRequests(CounterApiException e) {
    if (e.delivery() == REPORT_HEADER) {
      return super.isTooManyRequests(e);
    }
    return Integer.valueOf(429).equals(e.statusCode());
  }
}
