package org.olf.erm.usage.harvester.endpoints;

import static java.util.function.Predicate.not;
import static org.olf.erm.usage.harvester.DateUtil.getYearMonthFromString;
import static org.olf.erm.usage.harvester.ExceptionUtil.getMessageOrToString;
import static org.olf.erm.usage.harvester.endpoints.CounterApiException.CounterException;
import static org.olf.erm.usage.harvester.endpoints.ServiceEndpoint.createCounterReport;
import static org.olf.erm.usage.harvester.endpoints.TooManyRequestsException.TOO_MANY_REQUEST_ERROR_CODE;
import static org.olf.erm.usage.harvester.endpoints.TooManyRequestsException.TOO_MANY_REQUEST_STR;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.folio.rest.jaxrs.model.ApiException;
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

    private DefaultErrorHandlingStrategy(UsageDataProvider usageDataProvider) {
      this.usageDataProvider = usageDataProvider;
    }

    /** Dispatches the error to the matching handler by its type. */
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
     * #isTooManyRequests(CounterApiException)}); otherwise it is handled like an invalid report.
     *
     * <p>In the future, this is the extension point for more targeted handling based on the
     * exception codes, see UIEUS-430, UIEUS-389, UIEUS-371, UIEUS-431 and UIEUS-499.
     */
    private ErrorHandlingResult handleCounterApiException(
        final FetchItem fetchItem, final int retryCount, final CounterApiException e) {
      if (isTooManyRequests(e)) {
        return handleTooManyRequests(fetchItem, retryCount, e);
      }

      return handleInvalidReport(fetchItem, e);
    }

    private boolean isTooManyRequests(CounterApiException e) {
      return Integer.valueOf(429).equals(e.statusCode())
          || e.containsCode(TOO_MANY_REQUEST_ERROR_CODE)
          || e.exceptions().stream()
              .anyMatch(
                  x ->
                      x.message() != null
                          && x.message().toLowerCase(Locale.ROOT).contains(TOO_MANY_REQUEST_STR));
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
                      .withFailedReason(getMessageOrToString(t))
                      .withApiExceptions(toApiExceptions(t).orElse(null)))
          .toList();
    }

    /**
     * @return an optional list of {@link ApiException}, all of which at least provide an error code
     */
    private static Optional<List<ApiException>> toApiExceptions(final Throwable t) {
      if (t instanceof CounterApiException counterApiException) {
        return Optional.of(
                counterApiException.exceptions().stream()
                    .flatMap(exception -> toApiException(exception).stream())
                    .toList())
            .filter(not(List::isEmpty));
      }

      return Optional.empty();
    }

    /**
     * @return an optional so we can skip exceptions without error code
     */
    private static Optional<ApiException> toApiException(final CounterException e) {
      if (e.code() == null) {
        return Optional.empty();
      }

      return Optional.of(
          new ApiException()
              .withCode(e.code())
              .withMessage(toNonBlankString(e.message()).orElse(null))
              .withData(toNonBlankString(e.data()).orElse(null))
              .withHelpUrl(toUri(e.helpUrl()).orElse(null)));
    }

    private static Optional<String> toNonBlankString(final String text) {
      return Optional.ofNullable(text).filter(not(String::isBlank));
    }

    private static Optional<URI> toUri(final String helpUrl) {
      if (helpUrl == null || helpUrl.isBlank()) {
        return Optional.empty();
      }

      try {
        return Optional.of(new URI(helpUrl));
      } catch (URISyntaxException e) {
        return Optional.empty();
      }
    }
  }
}
