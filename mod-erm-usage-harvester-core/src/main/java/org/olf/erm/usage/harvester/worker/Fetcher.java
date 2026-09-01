package org.olf.erm.usage.harvester.worker;

import static org.olf.erm.usage.harvester.worker.WorkerController.QueueItem;

import io.vertx.core.Future;
import java.util.List;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.FetchListUtil;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClient;
import org.olf.erm.usage.harvester.endpoints.FetchItem;
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
        .map(FetchListUtil::collapse);
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
        .fetchReport(item.getReportType(), item.getBegin(), item.getEnd())
        .otherwise(t -> errorHandler.handleException(t, queueItem));
  }
}
