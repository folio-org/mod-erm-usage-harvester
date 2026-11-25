package org.olf.erm.usage.harvester.worker;

import static org.olf.erm.usage.harvester.worker.WorkerController.QueueItem;

import io.vertx.core.Future;
import java.util.List;
import org.folio.rest.jaxrs.model.CounterReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives a harvest run: determines which reports still need to be harvested, fetches them from the
 * provider and uploads them to storage. Queueing and ending the run are left to the {@link
 * WorkerController}.
 */
final class Orchestrator {

  private static final Logger LOGGER = LoggerFactory.getLogger(Orchestrator.class);

  private final LoggerContext logCtx;

  private final Fetcher fetcher;

  private final Uploader uploader;

  private final WorkerController controller;

  /**
   * @param fetcher the helper responsible for fetching the item form the UDP
   * @param uploader the helper responsible for uploading the report to the storage
   * @param controller the worker controller
   * @param logCtx the logger context
   */
  Orchestrator(
      final Fetcher fetcher,
      final Uploader uploader,
      final WorkerController controller,
      final LoggerContext logCtx) {
    this.logCtx = logCtx;
    this.fetcher = fetcher;
    this.uploader = uploader;
    this.controller = controller;
  }

  /**
   * Loads the initial items of a regular run: the fetch list of the provider, with a retry count of
   * 0.
   *
   * @param maxFailedAttempts the maximum number of failed attempts before giving up
   * @return a future with the initial items of a regular run
   */
  Future<List<QueueItem>> loadFetchList(final Future<Integer> maxFailedAttempts) {
    return maxFailedAttempts
        .compose(fetcher::getFetchList)
        .map(fetchItems -> QueueItem.createQueueItemList(fetchItems, 0));
  }

  /**
   * By calling this function, the complete process of fetching the reports for the initial items
   * and uploading the reports is triggered, once the initial items are available.
   *
   * @param initialItems a future with the queue items to start with, each carrying its retry count.
   *     An empty list ends the run, a failed future aborts the run.
   */
  void startWith(final Future<List<QueueItem>> initialItems) {
    initialItems.onSuccess(this::onInitialItemsSuccess).onFailure(this::onInitialItemsFailure);
  }

  private void onInitialItemsSuccess(final List<QueueItem> queueItems) {
    LOGGER.info(logCtx.createMsg("Succeeded loading {} items", queueItems.size()));
    if (queueItems.isEmpty()) {
      controller.undeploy();
      return;
    }

    controller.enqueue(queueItems);
    controller.startQueueWith(this::dispatch);
  }

  private Future<Void> dispatch(final QueueItem queueItem) {
    return fetcher
        .fetchReport(queueItem)
        .compose(reports -> uploadUnlessAborted(reports, queueItem));
  }

  private Future<Void> uploadUnlessAborted(
      final List<CounterReport> reports, final QueueItem queueItem) {
    if (controller.isAborted()) {
      LOGGER.info(
          logCtx.createMsg(
              "Discarding {} report(s) of {}, the run was aborted",
              reports.size(),
              queueItem.item()));
      return Future.succeededFuture();
    }
    return uploader.uploadReports(reports);
  }

  private void onInitialItemsFailure(final Throwable throwable) {
    LOGGER.error(logCtx.createMsg("Failed to load items"), throwable);
    controller.abort(throwable);
  }
}
