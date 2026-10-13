package org.olf.erm.usage.harvester.endpoints;

import static org.olf.erm.usage.harvester.DateUtil.getYearMonthFromString;
import static org.olf.erm.usage.harvester.ExceptionUtil.getMessageOrToString;
import static org.olf.erm.usage.harvester.endpoints.CounterApiException.CounterException;
import static org.olf.erm.usage.harvester.endpoints.CounterApiException.CounterExceptionPolicy;
import static org.olf.erm.usage.harvester.endpoints.ServiceEndpoint.createCounterReport;
import static org.olf.erm.usage.harvester.endpoints.TooManyRequestsException.TOO_MANY_REQUEST_ERROR_CODE;
import static org.olf.erm.usage.harvester.endpoints.TooManyRequestsException.TOO_MANY_REQUEST_STR;

import java.util.List;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.FetchItem;

/**
 * The default error handling strategy in case a specific {@link ServiceEndpoint} implementation
 * doesn't ship with its own {@link ErrorHandlingStrategy}.
 */
public class DefaultErrorHandlingStrategy implements ErrorHandlingStrategy {

  private static final int RETRY_COUNT_TOO_MANY_REQUESTS = 2;

  private final UsageDataProvider usageDataProvider;

  /**
   * Creates the strategy. Use {@link ErrorHandlingStrategy#create(UsageDataProvider)} to get an
   * instance; subclasses call this constructor. Note: We could pass in {@link
   * CounterExceptionPolicy} here and make {@link
   * DefaultErrorHandlingStrategy#handleCounterApiException(FetchItem, int, CounterApiException)}
   * make use of it. So it becomes configurable.
   *
   * @param usageDataProvider the provider the failed reports are created for
   */
  protected DefaultErrorHandlingStrategy(UsageDataProvider usageDataProvider) {
    this.usageDataProvider = usageDataProvider;
  }

  /**
   * Dispatches the error to the matching handler by its type. Subclasses customize the behavior by
   * overriding these handlers.
   */
  @Override
  public ErrorHandlingResult handleFetchError(
      final FetchItem fetchItem, final int retryCount, final Throwable t) {
    return switch (t) {
      case CounterApiException e -> handleCounterApiException(fetchItem, retryCount, e);
      case TooManyRequestsException e -> handleTooManyRequests(fetchItem, retryCount, e);
      case InvalidReportException e -> handleInvalidReport(fetchItem, e);
      default -> handleUnknownError(fetchItem, t);
    };
  }

  /**
   * Note: If we wanted more subtle behavioral differences depending on the {@link
   * CounterApiException}'s content, this is where we could extend, e.g. by handling certain
   * different {@link CounterException#code()}'s differently or considering the {@link
   * CounterExceptionPolicy} we passed in.
   */
  protected ErrorHandlingResult handleCounterApiException(
      final FetchItem fetchItem, final int retryCount, final CounterApiException e) {
    if (isTooManyRequests(e)) {
      return handleTooManyRequests(fetchItem, retryCount, e);
    }
    return switch (e.delivery()) {
      case REPORT_HEADER -> handleInvalidReport(fetchItem, e);
      case BARE_ERROR_STATUS -> handleUnknownError(fetchItem, e);
      case BARE_SUCCESS_STATUS -> handleBareSuccess(fetchItem, e);
    };
  }

  private static boolean isTooManyRequests(CounterApiException e) {
    return Integer.valueOf(429).equals(e.statusCode())
        || e.containsCode(TOO_MANY_REQUEST_ERROR_CODE)
        || e.exceptions().stream()
            .anyMatch(x -> x.message() != null && x.message().contains(TOO_MANY_REQUEST_STR));
  }

  protected ErrorHandlingResult handleBareSuccess(FetchItem fetchItem, CounterApiException e) {
    return handleUnknownError(fetchItem, e);
  }

  protected ErrorHandlingResult handleTooManyRequests(
      final FetchItem fetchItem, final int retryCount, final Throwable t) {
    if (retryCount < RETRY_COUNT_TOO_MANY_REQUESTS) {
      return new ErrorHandlingResult.Throttle(List.of(fetchItem), retryCount + 1);
    }
    final var reports = createFailedReports(fetchItem, t, usageDataProvider);
    return new ErrorHandlingResult.GiveUpSlowDown(reports);
  }

  protected ErrorHandlingResult handleInvalidReport(final FetchItem fetchItem, final Throwable t) {
    final var expanded = fetchItem.expand();
    if (expanded.size() <= 1) {
      final var reports = createFailedReports(expanded, t, usageDataProvider);
      return new ErrorHandlingResult.GiveUp(reports);
    }
    return new ErrorHandlingResult.RetryNow(expanded);
  }

  protected ErrorHandlingResult handleUnknownError(final FetchItem fetchItem, final Throwable t) {
    final var reports = createFailedReports(fetchItem, t, usageDataProvider);
    return new ErrorHandlingResult.GiveUp(reports);
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
                        null, i.reportType(), usageDataProvider, getYearMonthFromString(i.begin()))
                    .withFailedReason(getMessageOrToString(t)))
        .toList();
  }
}
