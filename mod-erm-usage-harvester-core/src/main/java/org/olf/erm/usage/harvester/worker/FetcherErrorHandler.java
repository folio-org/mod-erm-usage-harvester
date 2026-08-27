package org.olf.erm.usage.harvester.worker;

import static org.olf.erm.usage.harvester.endpoints.ServiceEndpoint.ErrorHandlingStrategy;
import static org.olf.erm.usage.harvester.worker.WorkerController.QueueItem;

import java.util.List;
import org.folio.rest.jaxrs.model.CounterReport;
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
      final var errorHandlingResult =
          strategy.handleFetchError(queueItem.item(), queueItem.retryCount(), t);

      if (errorHandlingResult.disableConcurrency()) {
        LOGGER.info(logCtx.createMsg("Concurrency disabled for {}", queueItem));
        controller.disableConcurrency();
      }

      if (!errorHandlingResult.itemsToRetry().isEmpty()) {
        LOGGER.info(logCtx.createMsg("Retrying for {}", queueItem));
        final var fetchItems = errorHandlingResult.itemsToRetry();
        final var retryCount = errorHandlingResult.retryCount();
        final var queueItems = QueueItem.createQueueItemList(fetchItems, retryCount);
        controller.enqueue(queueItems);
      }

      if (!errorHandlingResult.reportsToUpload().isEmpty()) {
        LOGGER.error(logCtx.createMsg("Reports with errors to be uploaded for {}", queueItem));
      }

      return errorHandlingResult.reportsToUpload();
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
