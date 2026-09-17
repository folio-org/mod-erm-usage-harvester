package org.olf.erm.usage.harvester.worker;

import static org.olf.erm.usage.harvester.ExceptionUtil.getMessageOrToString;
import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingResult.GiveUp;
import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingResult.GiveUpSlowDown;
import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingResult.RetryNow;
import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingResult.Throttle;
import static org.olf.erm.usage.harvester.worker.WorkerController.QueueItem;

import java.util.Collections;
import java.util.List;
import org.folio.rest.jaxrs.model.CounterReport;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.endpoints.ErrorHandlingStrategy;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
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

  static FetcherErrorHandler create(
      final LoggerContext logCtx,
      final WorkerController controller,
      final ErrorHandlingStrategy strategy) {
    return new DefaultErrorHandler(logCtx, controller, strategy);
  }

  class DefaultErrorHandler implements FetcherErrorHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultErrorHandler.class);

    private final LoggerContext logCtx;

    private final WorkerController controller;

    private final ErrorHandlingStrategy strategy;

    /** {@inheritDoc} */
    @Override
    public List<CounterReport> handleException(Throwable t, QueueItem queueItem) {
      LOGGER.info(logCtx.createMsg("{} Received {}", queueItem.item(), getMessageOrToString(t)));

      final var errorHandlingResult =
          strategy.handleFetchError(queueItem.item(), queueItem.retryCount(), t);

      return switch (errorHandlingResult) {
        case Throttle(var items, var retryCount) -> handleThrottle(items, retryCount, queueItem);
        case RetryNow(var items) -> handleRetryNow(items, 0, queueItem);
        case GiveUpSlowDown(var reports) -> handleGiveUpSlowDown(reports, queueItem);
        case GiveUp(var reports) -> handleGiveUp(reports, queueItem);
      };
    }

    private List<CounterReport> handleThrottle(
        final List<FetchItem> items, final int retryCount, final QueueItem queueItem) {
      LOGGER.info(logCtx.createMsg("Concurrency disabled for {}", queueItem));
      controller.disableConcurrency();

      return handleRetryNow(items, retryCount, queueItem);
    }

    private List<CounterReport> handleRetryNow(
        final List<FetchItem> items, final int retryCount, final QueueItem queueItem) {
      final var queueItems = QueueItem.createQueueItemList(items, retryCount);
      LOGGER.info(logCtx.createMsg("Re-queueing {} item(s) for {}", queueItems.size(), queueItem));
      controller.enqueue(queueItems);

      return Collections.emptyList();
    }

    private List<CounterReport> handleGiveUpSlowDown(
        final List<CounterReport> reports, final QueueItem queueItem) {
      LOGGER.info(logCtx.createMsg("Concurrency disabled for {}", queueItem));
      controller.disableConcurrency();

      return handleGiveUp(reports, queueItem);
    }

    private List<CounterReport> handleGiveUp(
        final List<CounterReport> reports, final QueueItem queueItem) {
      LOGGER.info(logCtx.createMsg("Reports with errors to be uploaded for {}", queueItem));
      return reports;
    }

    private DefaultErrorHandler(
        final LoggerContext logCtx,
        final WorkerController controller,
        final ErrorHandlingStrategy strategy) {
      this.logCtx = logCtx;
      this.controller = controller;
      this.strategy = strategy;
    }
  }
}
