package org.olf.erm.usage.harvester.endpoints;

import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingStrategy.DefaultErrorHandlingStrategy;

import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.FetchItem;

/**
 * Error handling strategy for {@link CS51Impl}. It keeps the outcomes cs51 had before {@link
 * CounterApiException} was introduced, where they differ from {@link DefaultErrorHandlingStrategy}.
 *
 * <p>Deviations from the default (to be removed in a follow-up ticket):A bare exception body with a
 * success status is treated like an invalid report: multi-month items are split and retried.
 *
 * <p>The {@code failedReason} of a bare body with a success status, of a report header the typed
 * COUNTER 5.1 model rejects, and of a 429 with a COUNTER exception body names the exceptions
 * instead of the parser's or {@link TooManyRequestsException}'s message.
 */
final class CS51ErrorHandlingStrategy extends DefaultErrorHandlingStrategy {

  CS51ErrorHandlingStrategy(UsageDataProvider usageDataProvider) {
    super(usageDataProvider);
  }

  /** Deviation from the default: split and retry instead of giving up. */
  @Override
  protected ErrorHandlingResult handleBareSuccess(FetchItem fetchItem, CounterApiException e) {
    return handleInvalidReport(fetchItem, e);
  }
}
