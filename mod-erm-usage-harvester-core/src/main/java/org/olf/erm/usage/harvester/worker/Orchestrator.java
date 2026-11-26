package org.olf.erm.usage.harvester.worker;

import static org.olf.erm.usage.harvester.worker.WorkerController.QueueItem;

import io.vertx.core.Future;
import java.util.List;
import org.folio.rest.jaxrs.model.CounterReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives a harvest run: queues the initial items the {@link Fetcher} determines, fetches the
 * reports and uploads them to storage. Queueing and ending the run are left to the {@link
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
   * By calling this function, the complete process of loading the initial items, fetching the
   * reports and uploading the reports is triggered.
   */
  void startHarvesting() {
    fetcher
        .loadInitialItems()
        .onSuccess(this::onInitialItemsSuccess)
        .onFailure(this::onInitialItemsFailure);
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
