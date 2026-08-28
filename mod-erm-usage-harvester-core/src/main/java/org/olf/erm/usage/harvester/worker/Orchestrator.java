package org.olf.erm.usage.harvester.worker;

import static org.olf.erm.usage.harvester.worker.WorkerController.QueueItem;

import io.vertx.core.Future;
import java.util.List;
import org.olf.erm.usage.harvester.FetchItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Orchestrates fetching and uploading items */
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
   * By calling this function, the complete process of fetching the fetch items, fetching the
   * reports and uploading the reports is triggered.
   *
   * @param maxFailedAttempts the maximum number of failed attempts before giving up.
   */
  void startWith(final Future<Integer> maxFailedAttempts) {
    maxFailedAttempts
        .compose(fetcher::getFetchList)
        .onSuccess(this::onFetchListSuccess)
        .onFailure(this::onFetchListFailure);
  }

  private void onFetchListSuccess(final List<FetchItem> fetchItems) {
    LOGGER.info(logCtx.createMsg("Succeeded fetching {} items", fetchItems.size()));
    if (fetchItems.isEmpty()) {
      controller.undeploy();
      return;
    }

    final var newQueueItems = QueueItem.createQueueItemList(fetchItems, 0);
    controller.enqueue(newQueueItems);
    controller.startQueueWith(this::dispatch);
  }

  private Future<Void> dispatch(final QueueItem queueItem) {
    return fetcher.fetchReport(queueItem).compose(uploader::uploadReports);
  }

  private void onFetchListFailure(final Throwable throwable) {
    LOGGER.error(logCtx.createMsg("Failed to fetch items"), throwable);
    controller.abort(throwable);
  }
}
