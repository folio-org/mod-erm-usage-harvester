package org.olf.erm.usage.harvester.endpoints;

import static org.olf.erm.usage.harvester.DateUtil.getYearMonthFromString;
import static org.olf.erm.usage.harvester.ExceptionUtil.getMessageOrToString;
import static org.olf.erm.usage.harvester.endpoints.ServiceEndpoint.createCounterReport;
import static org.olf.erm.usage.harvester.endpoints.TooManyRequestsException.TOO_MANY_REQUEST_ERROR_CODE;
import static org.olf.erm.usage.harvester.endpoints.TooManyRequestsException.TOO_MANY_REQUEST_STR;

import java.util.List;
import java.util.Locale;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.FetchItem;

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

    private static final int RETRY_COUNT_TOO_MANY_REQUESTS = 2;

    private final UsageDataProvider usageDataProvider;

    /**
     * Creates the strategy. Use {@link ErrorHandlingStrategy#create(UsageDataProvider)} to get an
     * instance; subclasses call this constructor. A future {@code CounterExceptionPolicy} would be
     * passed in here (see {@link #handleCounterApiException(FetchItem, int, CounterApiException)}).
     *
     * @param usageDataProvider the provider the failed reports are created for
     */
    protected DefaultErrorHandlingStrategy(UsageDataProvider usageDataProvider) {
      this.usageDataProvider = usageDataProvider;
    }

    /**
     * Dispatches the error to the matching handler by its type. Subclasses customize the behavior
     * by overriding these handlers.
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
     * Decides whether a {@link CounterApiException} is treated like HTTP 429: the status is 429, a
     * 1020 is among the exceptions, or a message contains "too many requests" (ignoring case).
     * Subclasses may narrow this down.
     */
    protected boolean isTooManyRequests(CounterApiException e) {
      return Integer.valueOf(429).equals(e.statusCode())
          || e.containsCode(TOO_MANY_REQUEST_ERROR_CODE)
          || e.exceptions().stream()
              .anyMatch(
                  x ->
                      x.message() != null
                          && x.message().toLowerCase(Locale.ROOT).contains(TOO_MANY_REQUEST_STR));
    }

    /**
     * Handles a body of only COUNTER exceptions with a 2xx status. Gives up by default; {@code
     * CS51ErrorHandlingStrategy} splits and retries instead.
     */
    protected ErrorHandlingResult handleBareSuccess(FetchItem fetchItem, CounterApiException e) {
      return handleUnknownError(fetchItem, e);
    }

    /**
     * Handles an invalid report, e.g. one with exceptions in its header. Multi-month items are
     * split into single months and retried; a single month gives up.
     */
    protected ErrorHandlingResult handleInvalidReport(
        final FetchItem fetchItem, final Throwable t) {
      final var expanded = fetchItem.expand();
      if (expanded.size() <= 1) {
        final var reports = createFailedReports(expanded, t, usageDataProvider);
        return new ErrorHandlingResult.GiveUp(reports);
      }
      return new ErrorHandlingResult.RetryNow(expanded);
    }

    /**
     * Handles a {@link CounterApiException}. For now this keeps the existing, rather unspecific
     * error handling: the exception codes only matter for the throttle check ({@link
     * #isTooManyRequests(CounterApiException)}), and otherwise the delivery situation decides.
     *
     * <p>In the future, this is the extension point for more targeted handling based on the
     * exception codes, see UIEUS-430, UIEUS-389, UIEUS-371, UIEUS-431 and UIEUS-499.
     *
     * <p>If we needed {@link ServiceEndpoint} implementation specific behavior, we could override
     * this method too.
     */
    private ErrorHandlingResult handleCounterApiException(
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

    private ErrorHandlingResult handleTooManyRequests(
        final FetchItem fetchItem, final int retryCount, final Throwable t) {
      if (retryCount < RETRY_COUNT_TOO_MANY_REQUESTS) {
        return new ErrorHandlingResult.Throttle(List.of(fetchItem), retryCount + 1);
      }
      final var reports = createFailedReports(fetchItem, t, usageDataProvider);
      return new ErrorHandlingResult.GiveUpSlowDown(reports);
    }

    private ErrorHandlingResult handleUnknownError(final FetchItem fetchItem, final Throwable t) {
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
                          null,
                          i.reportType(),
                          usageDataProvider,
                          getYearMonthFromString(i.begin()))
                      .withFailedReason(getMessageOrToString(t)))
          .toList();
    }
  }
}
