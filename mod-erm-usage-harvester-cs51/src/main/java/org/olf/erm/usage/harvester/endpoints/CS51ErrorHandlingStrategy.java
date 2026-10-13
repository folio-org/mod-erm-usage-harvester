package org.olf.erm.usage.harvester.endpoints;

import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.FetchItem;

/**
 * Error handling strategy for {@link CS51Impl}. Unlike the default, a 2xx response consisting only
 * of COUNTER exceptions is treated like an invalid report: multi-month items are split and retried.
 */
final class CS51ErrorHandlingStrategy extends DefaultErrorHandlingStrategy {

  CS51ErrorHandlingStrategy(UsageDataProvider usageDataProvider) {
    super(usageDataProvider);
  }

  /**
   * Note: This override is necessary to preserve the existing behavior, since the {@link CS51Impl}
   * changed.
   */
  @Override
  protected ErrorHandlingResult handleBareSuccess(FetchItem fetchItem, CounterApiException e) {
    return handleInvalidReport(fetchItem, e);
  }
}
