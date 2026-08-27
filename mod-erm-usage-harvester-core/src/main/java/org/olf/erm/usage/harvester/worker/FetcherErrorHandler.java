package org.olf.erm.usage.harvester.worker;

import static org.olf.erm.usage.harvester.DateUtil.getYearMonthFromString;
import static org.olf.erm.usage.harvester.ExceptionUtil.getMessageOrToString;
import static org.olf.erm.usage.harvester.FetchListUtil.expand;
import static org.olf.erm.usage.harvester.endpoints.ServiceEndpoint.createCounterReport;
import static org.olf.erm.usage.harvester.worker.WorkerController.QueueItem;

import java.util.Collections;
import java.util.List;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.endpoints.InvalidReportException;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.olf.erm.usage.harvester.endpoints.TooManyRequestsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An interface that handles one of the exceptions we expect to be thrown from {@link
 * ServiceEndpoint#fetchReport(String, String, String)}.
 */
@FunctionalInterface
interface FetcherErrorHandler {

  /**
   * Handles the exception thrown from {@link ServiceEndpoint#fetchReport(String, String, String)}
   *
   * @param t the thrown exception
   * @param queueItem the queue item the exception was thrown for
   * @return a list of counter reports containing some information about the exception we received
   */
  List<CounterReport> handleException(Throwable t, QueueItem queueItem);

  abstract class AbstractFetcherErrorHandler implements FetcherErrorHandler {

    protected final LoggerContext logCtx;

    protected final UsageDataProvider usageDataProvider;

    protected final WorkerController controller;

    AbstractFetcherErrorHandler(
        final LoggerContext logCtx,
        final UsageDataProvider usageDataProvider,
        final WorkerController controller) {
      this.logCtx = logCtx;
      this.usageDataProvider = usageDataProvider;
      this.controller = controller;
    }

    protected static List<CounterReport> createFailedReports(
        final FetchItem item, final Throwable t, final UsageDataProvider usageDataProvider) {
      return createFailedReports(expand(item), t, usageDataProvider);
    }

    protected static List<CounterReport> createFailedReports(
        final List<FetchItem> items, final Throwable t, final UsageDataProvider usageDataProvider) {
      return items.stream()
          .map(
              i ->
                  createCounterReport(
                          null,
                          i.getReportType(),
                          usageDataProvider,
                          getYearMonthFromString(i.getBegin()))
                      .withFailedReason(getMessageOrToString(t)))
          .toList();
    }
  }

  /** Handles {@link TooManyRequestsException}. */
  class TooManyRequestsHandler extends AbstractFetcherErrorHandler {

    private static final int RETRY_COUNT_TOO_MANY_REQUESTS = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger(TooManyRequestsHandler.class);

    TooManyRequestsHandler(
        final LoggerContext logCtx,
        final UsageDataProvider usageDataProvider,
        final WorkerController controller) {
      super(logCtx, usageDataProvider, controller);
    }

    /** {@inheritDoc} */
    @Override
    public List<CounterReport> handleException(final Throwable t, final QueueItem queueItem) {
      LOGGER.info(logCtx.createMsg("{} Received {}", queueItem.item(), getMessageOrToString(t)));
      final var item = queueItem.item();
      controller.disableConcurrency();

      if (queueItem.retryCount() < RETRY_COUNT_TOO_MANY_REQUESTS) {
        LOGGER.info(logCtx.createMsg("Too many requests.. adding {} back to queue", item));
        final var newQueueItem = QueueItem.of(item, queueItem.retryCount() + 1);
        controller.enqueue(List.of(newQueueItem));
        return Collections.emptyList();
      } else {
        LOGGER.info(
            logCtx.createMsg(
                "Too many requests.. returning null for {} after {} retries",
                item,
                RETRY_COUNT_TOO_MANY_REQUESTS));
        return createFailedReports(item, t, usageDataProvider);
      }
    }
  }

  /** Handles {@link InvalidReportException}. */
  class InvalidReportHandler extends AbstractFetcherErrorHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(InvalidReportHandler.class);

    InvalidReportHandler(
        final LoggerContext logCtx,
        final UsageDataProvider usageDataProvider,
        final WorkerController controller) {
      super(logCtx, usageDataProvider, controller);
    }

    /** {@inheritDoc} */
    @Override
    public List<CounterReport> handleException(Throwable t, QueueItem queueItem) {
      LOGGER.info(logCtx.createMsg("{} Received {}", queueItem.item(), getMessageOrToString(t)));
      final var item = queueItem.item();
      final var expanded = expand(item);

      // handle failed single month
      if (expanded.size() <= 1) {
        LOGGER.info(logCtx.createMsg("Returning null for {}", item));
        return createFailedReports(expanded, t, usageDataProvider);
      } else {
        LOGGER.info(logCtx.createMsg("Expanded {} into {} FetchItems", item, expanded.size()));
        final var newQueueItems = QueueItem.createQueueItemList(expanded, 0);
        controller.enqueue(newQueueItems);
        // reports for each of them.
        return Collections.emptyList();
      }
    }
  }

  /** Default handler if all the other handlers don't apply. */
  class DefaultFetcherErrorHandler extends AbstractFetcherErrorHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFetcherErrorHandler.class);

    DefaultFetcherErrorHandler(
        final LoggerContext logCtx, final UsageDataProvider usageDataProvider) {
      super(logCtx, usageDataProvider, null);
    }

    @Override
    public List<CounterReport> handleException(Throwable t, QueueItem queueItem) {
      LOGGER.info(logCtx.createMsg("{} Received {}", queueItem.item(), getMessageOrToString(t)));
      return createFailedReports(queueItem.item(), t, usageDataProvider);
    }
  }
}
