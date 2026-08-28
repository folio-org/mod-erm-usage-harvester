package org.olf.erm.usage.harvester.worker;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.olf.erm.usage.harvester.Messages.createMsgStatus;
import static org.olf.erm.usage.harvester.worker.LoggerContext.counterReportToString;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import java.util.List;
import org.folio.rest.jaxrs.model.CounterReport;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A helper that encapsulates the necessary dependencies and methods for uploading reports to a
 * counter reports client. It provides methods to upload a list of CounterReport objects
 * sequentially, handling failures and logging the results.
 */
final class Uploader {

  private static final Logger LOGGER = LoggerFactory.getLogger(Uploader.class);

  private static final int MAX_FAILED_UPLOAD_COUNT = 5;

  private final LoggerContext logCtx;

  private final ExtCounterReportsClient counterReportsClient;

  private final WorkerController controller;

  private int failedUploadCount = 0;

  /**
   * @param counterReportsClient the client to upload counter reports to
   * @param controller the worker controller
   * @param logCtx the logger context
   */
  Uploader(
      final ExtCounterReportsClient counterReportsClient,
      final WorkerController controller,
      final LoggerContext logCtx) {
    this.logCtx = logCtx;
    this.counterReportsClient = counterReportsClient;
    this.controller = controller;

    this.failedUploadCount = 0;
  }

  /**
   * Uploads a list of CounterReport objects to the specified counter reports client, handling
   * failures and logging the results.
   *
   * @param crs the list of CounterReport objects to upload
   * @return a Future<Void> representing the completion of the upload process
   */
  Future<Void> uploadReports(final List<CounterReport> crs) {
    return crs.stream()
        .reduce(
            succeededFuture(),
            (acc, counterReport) ->
                acc.compose(
                    v ->
                        counterReportsClient
                            .upsertReport(counterReport)
                            .onSuccess(response -> onUpsertSuccess(response, counterReport))
                            .onFailure(t -> onUpsertFailure(t, counterReport))
                            .transform(this::toFuture)),
            (a, b) -> a);
  }

  private void onUpsertSuccess(
      final HttpResponse<Buffer> response, final CounterReport counterReport) {
    if (response.statusCode() / 100 != 2) {
      failedUploadCount += 1;
    } else {
      failedUploadCount = 0;
    }
    LOGGER.info(
        logCtx.createMsg(
            "Upload of {} {}",
            counterReportToString(counterReport),
            createMsgStatus(response.statusCode(), response.statusMessage())));
  }

  private void onUpsertFailure(final Throwable throwable, final CounterReport counterReport) {
    failedUploadCount += 1;
    LOGGER.error(
        logCtx.createMsg(
            "Failed to upload {}: {}",
            counterReportToString(counterReport),
            throwable.getMessage()),
        throwable);
  }

  private Future<Void> toFuture(final AsyncResult<HttpResponse<Buffer>> asyncResult) {
    if (failedUploadCount >= MAX_FAILED_UPLOAD_COUNT) {
      final var msg = "Stopping after " + MAX_FAILED_UPLOAD_COUNT + " failed uploads in a row";
      controller.abort(msg);
      return failedFuture(msg);
    } else {
      return succeededFuture();
    }
  }
}
