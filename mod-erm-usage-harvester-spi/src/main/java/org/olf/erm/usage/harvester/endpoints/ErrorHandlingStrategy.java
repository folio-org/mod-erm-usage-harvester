package org.olf.erm.usage.harvester.endpoints;

import static org.olf.erm.usage.harvester.DateUtil.getYearMonthFromString;
import static org.olf.erm.usage.harvester.ExceptionUtil.getMessageOrToString;
import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingResult.GiveUp;
import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingResult.GiveUpSlowDown;
import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingResult.RetryNow;
import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingResult.Throttle;
import static org.olf.erm.usage.harvester.endpoints.ServiceEndpoint.createCounterReport;

import java.util.List;
import org.folio.rest.jaxrs.model.ApiException;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.endpoints.exceptions.ApiExceptionProvider;
import org.olf.erm.usage.harvester.endpoints.exceptions.InvalidReportException;
import org.olf.erm.usage.harvester.endpoints.exceptions.TooManyRequestsException;

/** An error handling strategy {@link ServiceEndpoint} consumers can use to handle exceptions. */
@FunctionalInterface
public interface ErrorHandlingStrategy {

  /**
   * Handles the error on report fetching.
   *
   * @param fetchItem the fetch item to handle the error for
   * @param retryCount the number of retries already performed
   * @param t the {@link Throwable} to take care of
   * @return the error handling result
   */
  ErrorHandlingResult handleFetchError(FetchItem fetchItem, int retryCount, Throwable t);

  // apply configurations
  // 1000, 1020, 1011, 1010 specific

  // configuration:
  // * (<provider id 1>, <error code>) -> do something
  // * (<provider id 2>, <error code>) -> do something

  /**
   * @return The current default error handling strategy. This will be removed as soon as {@link
   *     ServiceEndpoint} implementation specific error handling strategies are in place.
   */
  static ErrorHandlingStrategy create(UsageDataProvider provider) {
    return new DefaultErrorHandlingStrategy(provider);
  }

  /**
   * The default error handling strategy in case a specific {@link ServiceEndpoint} implementation
   * doesn't ship with its own {@link ErrorHandlingStrategy}.
   */
  class DefaultErrorHandlingStrategy implements ErrorHandlingStrategy {

    private static final int DEFAULT_ERROR_CODE = 1;

    private static final int RETRY_COUNT_TOO_MANY_REQUESTS = 2;

    private final UsageDataProvider usageDataProvider;

    private DefaultErrorHandlingStrategy(UsageDataProvider usageDataProvider) {
      this.usageDataProvider = usageDataProvider;
    }

    @Override
    public ErrorHandlingResult handleFetchError(
        final FetchItem fetchItem, final int retryCount, final Throwable t) {
      return switch (t) {
        case TooManyRequestsException e -> handleTooManyRequests(fetchItem, retryCount, e);
        case InvalidReportException e -> handleInvalidReport(fetchItem, e);
        default -> handleUnknownError(fetchItem, t);
      };
    }

    private ErrorHandlingResult handleTooManyRequests(
        final FetchItem fetchItem, final int retryCount, final TooManyRequestsException e) {
      if (retryCount < RETRY_COUNT_TOO_MANY_REQUESTS) {
        return new Throttle(List.of(fetchItem), retryCount + 1);
      }
      final var reports = createFailedReports(fetchItem, e, usageDataProvider);
      return new GiveUpSlowDown(reports);
    }

    private ErrorHandlingResult handleInvalidReport(
        final FetchItem fetchItem, final InvalidReportException e) {
      final var expanded = fetchItem.expand();
      if (expanded.size() <= 1) {
        final var reports = createFailedReports(expanded, e, usageDataProvider);
        return new GiveUp(reports);
      }
      return new RetryNow(expanded);
    }

    private ErrorHandlingResult handleUnknownError(final FetchItem fetchItem, final Throwable t) {
      final var reports = createFailedReports(fetchItem, t, usageDataProvider);
      return new GiveUp(reports);
    }

    private static List<CounterReport> createFailedReports(
        final FetchItem item, final Throwable t, final UsageDataProvider usageDataProvider) {
      return createFailedReports(item.expand(), t, usageDataProvider);
    }

    private static List<CounterReport> createFailedReports(
        final List<FetchItem> items, final Throwable t, final UsageDataProvider usageDataProvider) {
      return items.stream()
          .map(
              i ->
                  createCounterReport(
                          null,
                          i.reportType(),
                          usageDataProvider,
                          getYearMonthFromString(i.begin()))
                      .withFailedReason(getMessageOrToString(t))
                      .withApiException(toApiException(t)))
          .toList();
    }

    /**
     * For this to work, all internally created exceptions must implement {@link
     * ApiExceptionProvider}.
     */
    private static ApiException toApiException(final Throwable t) {
      if (t instanceof ApiExceptionProvider provider) {
        return provider.toApiException();
      }

      return new ApiException().withCode(DEFAULT_ERROR_CODE).withMessage(t.getMessage());
    }
  }
}
