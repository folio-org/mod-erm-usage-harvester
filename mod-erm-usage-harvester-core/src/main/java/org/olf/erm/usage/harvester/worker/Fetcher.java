package org.olf.erm.usage.harvester.worker;

import static org.olf.erm.usage.harvester.worker.WorkerController.QueueItem;

import io.vertx.core.Future;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.DateUtil;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClient;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A helper that encapsulates the necessary dependencies and methods for fetching reports from a
 * usage data provider. It provides methods to retrieve a list of items to fetch and to fetch
 * individual reports, handling failures and logging as needed.
 */
final class Fetcher {

  private static final Logger LOGGER = LoggerFactory.getLogger(Fetcher.class);

  private static final int MAX_RANGE = 12;

  private final LoggerContext logCtx;

  private final ExtCounterReportsClient counterReportsClient;

  private final UsageDataProvider usageDataProvider;

  private final ServiceEndpoint serviceEndpoint;

  private final FetcherErrorHandler errorHandler;

  /**
   * @param counterReportsClient the counter reports client to get the fetch list from
   * @param usageDataProvider the usage data provider to fetch reports for
   * @param serviceEndpoint the service endpoint to use for fetching reports
   * @param logCtx the logger context
   * @param errorHandler the fetcher error handler taking care of error handling
   */
  Fetcher(
      final ExtCounterReportsClient counterReportsClient,
      final UsageDataProvider usageDataProvider,
      final ServiceEndpoint serviceEndpoint,
      final LoggerContext logCtx,
      final FetcherErrorHandler errorHandler) {
    this.logCtx = logCtx;
    this.counterReportsClient = counterReportsClient;
    this.usageDataProvider = usageDataProvider;
    this.serviceEndpoint = serviceEndpoint;
    this.errorHandler = errorHandler;
  }

  /**
   * Retrieves the list of items to fetch from the {@link ExtCounterReportsClient}, passing in the
   * {@link UsageDataProvider} and maximum number of failed attempts as a dependency.
   *
   * @param maxFailedAttempts the maximum number of failed attempts allowed before giving up on
   *     fetching a report
   * @return a future list of items to fetch
   */
  Future<List<FetchItem>> getFetchList(final int maxFailedAttempts) {
    return counterReportsClient
        .getFetchList(usageDataProvider, maxFailedAttempts)
        .map(
            list -> {
              if (list.isEmpty()) {
                LOGGER.info(logCtx.createMsg("No reports need to be fetched."));
              }
              return list;
            })
        .map(Fetcher::collapse);
  }

  /**
   * Fetches a report from a provider using the {@link ServiceEndpoint} to do so.
   *
   * @param queueItem the queue item to fetch the report for
   * @return a future list of counter reports
   */
  Future<List<CounterReport>> fetchReport(final QueueItem queueItem) {
    final var item = queueItem.item();
    LOGGER.info(logCtx.createMsg("processing {}", item));
    return serviceEndpoint
        .fetchReport(item.reportType(), item.begin(), item.end())
        .otherwise(t -> errorHandler.handleException(t, queueItem));
  }

  /**
   * Collapses a list of {@link FetchItem} objects based on their report type and date ranges.
   *
   * <p>This method takes a list of {@link FetchItem} objects and groups them by their report type.
   * For report type "TR" the items remain unchanged. For other report types, the items are grouped
   * into date ranges where each range spans consecutive months and contains up to a maximum number
   * of months specified by {@code MAX_RANGE}.
   *
   * <p>Package-scoped for testing
   *
   * @param items The list of {@link FetchItem} objects to be collapsed.
   * @return A collapsed list of {@link FetchItem} objects, where items with report type "TR" remain
   *     unchanged, and items with other report types are grouped into date ranges. The resulting
   *     list is sorted by report type and date.
   * @see FetchItem
   */
  static List<FetchItem> collapse(final List<FetchItem> items) {
    return items.stream()
        .distinct()
        .collect(Collectors.groupingBy(FetchItem::reportType))
        .entrySet()
        .stream()
        .flatMap(e -> collapseReportType(e.getKey(), e.getValue()).stream())
        .toList();
  }

  /**
   * Collapses the {@link FetchItem}s of a single report type, merging items of report types other
   * than "TR" into as few items as possible.
   */
  private static List<FetchItem> collapseReportType(
      final String reportType, final List<FetchItem> items) {
    if ("TR".equals(reportType)) {
      return items;
    }

    final var sortedMonths =
        items.stream() //
            .map(fi -> DateUtil.getYearMonthFromString(fi.begin())) //
            .sorted() //
            .toList();
    return groupConsecutiveMonths(sortedMonths).stream() //
        .map(range -> FetchItem.of(reportType, range)) //
        .toList();
  }

  /**
   * Groups a sorted list of months into consecutive runs, each spanning at most {@link #MAX_RANGE}
   * months.
   */
  private static List<List<YearMonth>> groupConsecutiveMonths(final List<YearMonth> sortedMonths) {
    final List<List<YearMonth>> ranges = new ArrayList<>();
    for (YearMonth month : sortedMonths) {
      final List<YearMonth> currentRange = ranges.isEmpty() ? null : ranges.getLast();
      final var extendsCurrentRange =
          currentRange != null
              && currentRange.getLast().plusMonths(1).equals(month)
              && currentRange.size() < MAX_RANGE;
      if (extendsCurrentRange) {
        currentRange.add(month);
      } else {
        ranges.add(new ArrayList<>(List.of(month)));
      }
    }
    return ranges;
  }
}
