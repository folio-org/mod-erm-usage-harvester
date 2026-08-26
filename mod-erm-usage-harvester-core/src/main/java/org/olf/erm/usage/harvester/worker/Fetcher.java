package org.olf.erm.usage.harvester.worker;

import static org.olf.erm.usage.harvester.worker.FetcherErrorHandler.DefaultFetcherErrorHandler;
import static org.olf.erm.usage.harvester.worker.FetcherErrorHandler.InvalidReportHandler;
import static org.olf.erm.usage.harvester.worker.FetcherErrorHandler.TooManyRequestsHandler;
import static org.olf.erm.usage.harvester.worker.WorkerController.QueueItem;

import io.vertx.core.Future;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.FetchListUtil;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClient;
import org.olf.erm.usage.harvester.endpoints.InvalidReportException;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.olf.erm.usage.harvester.endpoints.TooManyRequestsException;
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

  private final ExceptionToHandlerMap exceptionToHandlerMap;

  /**
   * @param counterReportsClient the counter reports client to get the fetch list from
   * @param usageDataProvider the usage data provider to fetch reports for
   * @param serviceEndpoint the service endpoint to use for fetching reports
   * @param controller the worker controller
   * @param logCtx the logger context
   */
  Fetcher(
      final ExtCounterReportsClient counterReportsClient,
      final UsageDataProvider usageDataProvider,
      final ServiceEndpoint serviceEndpoint,
      final WorkerController controller,
      final LoggerContext logCtx) {
    this.logCtx = logCtx;
    this.counterReportsClient = counterReportsClient;
    this.usageDataProvider = usageDataProvider;
    this.serviceEndpoint = serviceEndpoint;

    // TODO: Make the handlers an external dependency, will be done with
    // https://folio-org.atlassian.net/browse/UIEUS-459
    exceptionToHandlerMap =
        new ExceptionToHandlerMap(
            new DefaultFetcherErrorHandler(logCtx, usageDataProvider),
            ExceptionToHandlerPair.of(
                TooManyRequestsException.class,
                new TooManyRequestsHandler(logCtx, usageDataProvider, controller)),
            ExceptionToHandlerPair.of(
                InvalidReportException.class,
                new InvalidReportHandler(logCtx, usageDataProvider, controller)));
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
        .otherwise(t -> exceptionToHandlerMap.handlerFor(t).handleException(t, queueItem));
  }

  /**
   * Maps an exception to a dedicated exception handler
   *
   * @param exception the exception to be handled
   * @param handler the handler for the exception
   */
  record ExceptionToHandlerPair(Class<? extends Throwable> exception, FetcherErrorHandler handler) {

    static ExceptionToHandlerPair of(
        Class<? extends Throwable> exception, FetcherErrorHandler handler) {
      return new ExceptionToHandlerPair(exception, handler);
    }
  }

  private static class ExceptionToHandlerMap {

    private final FetcherErrorHandler defaultHandler;

    private final Map<Class<? extends Throwable>, FetcherErrorHandler> map;

    ExceptionToHandlerMap(
        final FetcherErrorHandler defaultHandler, final ExceptionToHandlerPair... handlerPairs) {
      this.defaultHandler = defaultHandler;
      map = new HashMap<>();
      for (final var handlerPair : handlerPairs) {
        map.put(handlerPair.exception, handlerPair.handler);
      }
    }

    FetcherErrorHandler handlerFor(final Throwable t) {
      for (Class<?> c = t.getClass(); c != null; c = c.getSuperclass()) {
        final var handler = map.get(c);
        if (handler != null) {
          return handler;
        }
      }
      return defaultHandler;
    }
  }
}
