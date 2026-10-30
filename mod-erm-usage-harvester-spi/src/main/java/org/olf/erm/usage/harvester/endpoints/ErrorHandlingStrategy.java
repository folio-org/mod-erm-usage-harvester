package org.olf.erm.usage.harvester.endpoints;

import static org.olf.erm.usage.harvester.DateUtil.getYearMonthFromString;
import static org.olf.erm.usage.harvester.ExceptionUtil.getMessageOrToString;
import static org.olf.erm.usage.harvester.endpoints.Code1020Exception.ERROR_CODE_1020;
import static org.olf.erm.usage.harvester.endpoints.Code1020Exception.ERROR_CODE_1020_STR;
import static org.olf.erm.usage.harvester.endpoints.ServiceEndpoint.createCounterReport;

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

    private static final int RETRY_COUNT_CODE_1020 = 2;

    private final UsageDataProvider usageDataProvider;

    private DefaultErrorHandlingStrategy(UsageDataProvider usageDataProvider) {
      this.usageDataProvider = usageDataProvider;
    }

    /** Dispatches the error to the matching handler by its type. */
    @Override
    public ErrorHandlingResult handleFetchError(
        final FetchItem fetchItem, final int retryCount, final Throwable t) {
      return switch (t) {
        case CounterApiException e -> handleCounterApiException(fetchItem, retryCount, e);
        case Code1020Exception e -> handleCode1020Exception(fetchItem, retryCount, e);
        case InvalidReportException e -> handleInvalidReport(fetchItem, e);
        default -> handleUnknownError(fetchItem, t);
      };
    }

    /**
     * Handles an invalid report, e.g. one with exceptions in its header. Multi-month items are
     * split into single months and retried; a single month gives up.
     */
    private ErrorHandlingResult handleInvalidReport(final FetchItem fetchItem, final Throwable t) {
      final var expanded = fetchItem.expand();
      if (expanded.size() <= 1) {
        final var reports = createFailedReports(expanded, t, usageDataProvider);
        return new ErrorHandlingResult.GiveUp(reports);
      }

      return new ErrorHandlingResult.RetryNow(expanded);
    }

    /**
     * Handles a {@link CounterApiException} the same way in every delivery situation. For now the
     * exception codes only matter for the throttle check ({@link
     * #isCode1020(CounterApiException)}); otherwise it is handled like an invalid report.
     *
     * <p>In the future, this is the extension point for more targeted handling based on the
     * exception codes, see UIEUS-430, UIEUS-389, UIEUS-371, UIEUS-431 and UIEUS-499.
     */
    private ErrorHandlingResult handleCounterApiException(
        final FetchItem fetchItem, final int retryCount, final CounterApiException e) {
      if (isCode1020(e)) {
        return handleCode1020Exception(fetchItem, retryCount, e);
      }

      return handleInvalidReport(fetchItem, e);
    }

    private boolean isCode1020(CounterApiException e) {
      return Integer.valueOf(429).equals(e.statusCode())
          || e.containsCode(ERROR_CODE_1020)
          || e.exceptions().stream()
              .anyMatch(
                  x ->
                      x.message() != null
                          && x.message().toLowerCase(Locale.ROOT).contains(ERROR_CODE_1020_STR));
    }

    private ErrorHandlingResult handleCode1020Exception(
        final FetchItem fetchItem, final int retryCount, final Throwable t) {
      if (retryCount < RETRY_COUNT_CODE_1020) {
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
