package org.olf.erm.usage.harvester.endpoints;

import static org.olf.erm.usage.harvester.DateUtil.getYearMonthFromString;
import static org.olf.erm.usage.harvester.DateUtil.getYearMonths;
import static org.olf.erm.usage.harvester.ExceptionUtil.getApiExceptionFrom;
import static org.olf.erm.usage.harvester.ExceptionUtil.getMessageOrToString;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.YearMonth;
import java.util.*;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.Report;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ServiceEndpoint {

  Logger LOG = LoggerFactory.getLogger(ServiceEndpoint.class);

  /**
   * Fetches a report from a provider and returns a list containing a {@link CounterReport} for each
   * month in the requested range.
   *
   * <p>Use {@link ServiceEndpoint#createCounterReport(String, String, UsageDataProvider,
   * YearMonth)} for creating a {@link CounterReport}
   *
   * @param report requested report type
   * @param beginDate start date (e.g. "2018-01-01")
   * @param endDate end date (e.g. "2018-12-31")
   * @return Future with a List of {@link CounterReport}
   * @throws InvalidReportException if the fetched report contains any COUNTER exceptions
   * @throws InvalidServiceURLException if the service URL is invalid
   * @throws TooManyRequestsException to signal that too many requests are made
   * @throws UnsupportedReportTypeException if the requested report type is not supported
   */
  Future<List<CounterReport>> fetchReport(String report, String beginDate, String endDate);

  static CounterReport createCounterReport(
      String reportData, String reportName, UsageDataProvider provider, YearMonth yearMonth) {
    CounterReport cr = new CounterReport();
    cr.setId(UUID.randomUUID().toString());
    cr.setYearMonth(yearMonth.toString());
    cr.setReportName(reportName);
    cr.setRelease(provider.getHarvestingConfig().getReportRelease());
    cr.setProviderId(provider.getId());
    cr.setDownloadTime(Date.from(Instant.now()));
    if (reportData != null) {
      cr.setReport(Json.decodeValue(reportData, Report.class));
    } else {
      cr.setFailedAttempts(1);
    }
    return cr;
  }

  static List<ServiceEndpointProvider> getAvailableProviders() {
    ServiceLoader<ServiceEndpointProvider> loader =
        ServiceLoader.load(ServiceEndpointProvider.class);
    return Lists.newArrayList(loader.iterator());
  }

  static ServiceEndpoint create(UsageDataProvider provider, AggregatorSetting aggregator) {
    Objects.requireNonNull(provider);

    String serviceType;
    if (Objects.isNull(aggregator)) {
      if (Objects.nonNull(provider.getHarvestingConfig())
          && Objects.nonNull(provider.getHarvestingConfig().getSushiConfig())) {
        serviceType = provider.getHarvestingConfig().getSushiConfig().getServiceType();
      } else {
        serviceType = null;
      }
    } else {
      serviceType = aggregator.getServiceType();
    }

    if (Strings.isNullOrEmpty(serviceType)) {
      LOG.error("ServiceType is null or empty for providerId {}", provider.getId());
      return null;
    }

    ServiceLoader<ServiceEndpointProvider> loader =
        ServiceLoader.load(ServiceEndpointProvider.class);
    for (ServiceEndpointProvider p : loader) {
      if (p.getServiceType().equals(serviceType)) {
        return p.create(provider, aggregator);
      }
    }

    LOG.error("No implementation found for serviceType '{}'", serviceType);
    return null;
  }

  /**
   * Returns a proxy for the given URI using the system's default {@link ProxySelector}.
   *
   * @param uri the URI to get a proxy for
   * @return an Optional containing the first proxy with a non-null address, or empty if none found
   */
  default Optional<Proxy> getProxy(URI uri) {
    return ProxySelector.getDefault().select(uri).stream()
        .filter(p -> p.address() != null)
        .findFirst();
  }

  /**
   * Returns Vert.x {@link ProxyOptions} for the given URL using the system's default proxy
   * settings.
   *
   * @param url the URL to get proxy options for
   * @return an Optional containing ProxyOptions if a proxy is configured, or empty if no proxy is
   *     needed or the URL is null/invalid
   */
  default Optional<ProxyOptions> getProxyOptions(String url) {
    if (url == null) {
      return Optional.empty();
    }
    try {
      return getProxy(new URI(url))
          .map(
              p -> {
                InetSocketAddress addr = (InetSocketAddress) p.address();
                return new ProxyOptions()
                    .setHost(addr.getHostString())
                    .setPort(addr.getPort())
                    .setType(ProxyType.HTTP);
              });
    } catch (URISyntaxException e) {
      LOG.error("Error getting proxy for URL '{}': {}", url, e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * @return An error handling strategy
   */
  default ErrorHandlingStrategy getErrorHandlingStrategy(UsageDataProvider provider) {
    return new ErrorHandlingStrategy.DefaultErrorHandlingStrategy(provider);
  }

  /** An error handling strategy {@link ServiceEndpoint} consumers can use to handle exceptions. */
  @FunctionalInterface
  interface ErrorHandlingStrategy {

    /**
     * Handles the error on report fetching.
     *
     * @param fetchItem the fetch item to handle the error for
     * @param retryCount the number of retries already performed
     * @param t the {@link Throwable} to take care of
     * @return the error handling result
     */
    ErrorHandlingResult handleFetchError(FetchItem fetchItem, int retryCount, Throwable t);

    static ErrorHandlingStrategy create(UsageDataProvider provider) {
      return new DefaultErrorHandlingStrategy(provider);
    }

    private static List<CounterReport> createFailedReports(
        final FetchItem item, final Throwable t, final UsageDataProvider usageDataProvider) {
      return createFailedReports(expand(item), t, usageDataProvider);
    }

    private static List<CounterReport> createFailedReports(
        final List<FetchItem> items, final Throwable t, final UsageDataProvider usageDataProvider) {
      return items.stream()
          .map(
              i ->
                  createCounterReport(
                          null,
                          i.getReportType(),
                          usageDataProvider,
                          getYearMonthFromString(i.getBegin()))
                      .withFailedReason(getMessageOrToString(t))
                      .withApiException(getApiExceptionFrom(t)))
          .toList();
    }

    private static List<FetchItem> expand(FetchItem fetchItem) {
      final var months = getYearMonths(fetchItem.getBegin(), fetchItem.getEnd());

      return months.stream()
          .map(
              ym ->
                  new FetchItem(
                      fetchItem.getReportType(),
                      ym.atDay(1).toString(),
                      ym.atEndOfMonth().toString()))
          .toList();
    }

    /** The default error handling strategy mirroring the current behavior. */
    class DefaultErrorHandlingStrategy implements ErrorHandlingStrategy {

      private static final Logger LOGGER =
          LoggerFactory.getLogger(DefaultErrorHandlingStrategy.class);

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
          case InvalidReportException e -> handleInvalidReport(fetchItem, retryCount, e);
          default -> handleUnknownError(fetchItem, retryCount, t);
        };
      }

      private ErrorHandlingResult handleTooManyRequests(
          final FetchItem fetchItem, final int retryCount, final TooManyRequestsException e) {
        if (retryCount < RETRY_COUNT_TOO_MANY_REQUESTS) {
          return new ErrorHandlingResult(
              Collections.emptyList(), List.of(fetchItem), retryCount + 1, true);
        }
        final var reports = createFailedReports(fetchItem, e, usageDataProvider);
        return new ErrorHandlingResult(reports, Collections.emptyList(), 0, true);
      }

      private ErrorHandlingResult handleInvalidReport(
          final FetchItem fetchItem, final int retryCount, final InvalidReportException e) {
        final var expanded = expand(fetchItem);
        if (expanded.size() <= 1) {
          final var reports = createFailedReports(expanded, e, usageDataProvider);
          return new ErrorHandlingResult(reports, Collections.emptyList(), 0, false);
        }
        return new ErrorHandlingResult(Collections.emptyList(), expanded, 0, false);
      }

      private ErrorHandlingResult handleUnknownError(
          final FetchItem fetchItem, final int retryCount, final Throwable t) {
        final var reports = createFailedReports(fetchItem, t, usageDataProvider);
        return new ErrorHandlingResult(reports, Collections.emptyList(), 0, false);
      }
    }
  }

  /**
   * An error handling result, indicating how the consumer should behave
   *
   * @param reportsToUpload the reports to upload
   * @param itemsToRetry the fetch items to retry fetching
   * @param retryCount the number of retries for to re-queued items
   * @param disableConcurrency whether to disable the concurrency or not
   */
  record ErrorHandlingResult(
      List<CounterReport> reportsToUpload,
      List<FetchItem> itemsToRetry,
      int retryCount,
      boolean disableConcurrency) {}
}
