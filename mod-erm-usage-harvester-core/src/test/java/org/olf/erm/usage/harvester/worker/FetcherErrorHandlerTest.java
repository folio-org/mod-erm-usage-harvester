package org.olf.erm.usage.harvester.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.worker.FetcherErrorHandler.DefaultFetcherErrorHandler;
import static org.olf.erm.usage.harvester.worker.FetcherErrorHandler.InvalidReportHandler;
import static org.olf.erm.usage.harvester.worker.FetcherErrorHandler.TooManyRequestsHandler;
import static org.olf.erm.usage.harvester.worker.OrchestratorTest.TestWorkerController;
import static org.olf.erm.usage.harvester.worker.WorkerController.QueueItem;

import com.google.common.io.Resources;
import io.vertx.core.json.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.endpoints.InvalidReportException;
import org.olf.erm.usage.harvester.endpoints.TooManyRequestsException;

/** Tests for the {@link FetcherErrorHandler} implementations. */
class FetcherErrorHandlerTest {

  private static final UsageDataProvider USAGE_DATA_PROVIDER;

  static {
    try {
      USAGE_DATA_PROVIDER =
          Json.decodeValue(
              Resources.toString(
                  Resources.getResource("__files/usage-data-provider.json"),
                  StandardCharsets.UTF_8),
              UsageDataProvider.class);
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static final LoggerContext LOG_CTX =
      new LoggerContext("Tenant ID", USAGE_DATA_PROVIDER.getLabel());

  private static final FetchItem FETCH_ITEM_TR = new FetchItem("TR", "2025-01-01", "2025-01-31");

  private static final FetchItem FETCH_ITEM_DR = new FetchItem("DR", "2025-01-01", "2025-03-31");

  private TestWorkerController controller;

  @BeforeEach
  void beforeTest() {
    controller = new TestWorkerController();
  }

  @Test
  void testTooManyRequestsHandlerReEnqueuesWhileRetriesRemain() {
    final var handler = new TooManyRequestsHandler(LOG_CTX, USAGE_DATA_PROVIDER, controller);

    final var result =
        handler.handleException(
            new TooManyRequestsException("Slow down"), QueueItem.of(FETCH_ITEM_TR, 0));

    assertThat(result).as("... no reports are returned yet").isEmpty();
    assertThat(controller.concurrencyWasDisabled).as("... concurrency was disabled").isTrue();
    assertThat(controller.queueItems)
        .as("... the item was re-enqueued with an incremented retry count")
        .containsExactly(QueueItem.of(FETCH_ITEM_TR, 1));
  }

  @Test
  void testTooManyRequestsHandlerReturnsFailedReportsWhenRetriesExhausted() {
    final var handler = new TooManyRequestsHandler(LOG_CTX, USAGE_DATA_PROVIDER, controller);

    final var result =
        handler.handleException(
            new TooManyRequestsException("Slow down"), QueueItem.of(FETCH_ITEM_TR, 2));

    assertThat(controller.concurrencyWasDisabled).as("... concurrency was disabled").isTrue();
    assertThat(controller.queueItems).as("... nothing was re-enqueued").isEmpty();
    assertThat(result)
        .as("... a single failed report is returned for the item")
        .singleElement()
        .satisfies(
            cr -> {
              assertThat(cr.getReportName()).isEqualTo("TR");
              assertThat(cr.getYearMonth()).isEqualTo("2025-01");
              assertThat(cr.getFailedReason()).isEqualTo("Slow down");
            });
  }

  @Test
  void testInvalidReportHandlerReturnsFailedReportForSingleMonth() {
    final var handler = new InvalidReportHandler(LOG_CTX, USAGE_DATA_PROVIDER, controller);

    final var result =
        handler.handleException(
            new InvalidReportException("Invalid report"), QueueItem.of(FETCH_ITEM_TR, 0));

    assertThat(controller.queueItems).as("... nothing was re-enqueued").isEmpty();
    assertThat(result)
        .as("... a single failed report is returned for the item")
        .singleElement()
        .satisfies(
            cr -> {
              assertThat(cr.getReportName()).isEqualTo("TR");
              assertThat(cr.getYearMonth()).isEqualTo("2025-01");
              assertThat(cr.getFailedReason()).isEqualTo("Report not valid: Invalid report");
            });
  }

  @Test
  void testInvalidReportHandlerExpandsAndReEnqueuesMultiMonthItem() {
    final var handler = new InvalidReportHandler(LOG_CTX, USAGE_DATA_PROVIDER, controller);

    final var result =
        handler.handleException(
            new InvalidReportException("Invalid report"), QueueItem.of(FETCH_ITEM_DR, 0));

    assertThat(result).as("... no reports are returned yet").isEmpty();
    assertThat(controller.queueItems)
        .as("... one queue item per expanded month, each with retry count 0")
        .containsExactly(
            QueueItem.of(new FetchItem("DR", "2025-01-01", "2025-01-31"), 0),
            QueueItem.of(new FetchItem("DR", "2025-02-01", "2025-02-28"), 0),
            QueueItem.of(new FetchItem("DR", "2025-03-01", "2025-03-31"), 0));
  }

  @Test
  void testDefaultFetcherErrorHandlerReturnsFailedReportPerMonth() {
    final var handler = new DefaultFetcherErrorHandler(LOG_CTX, USAGE_DATA_PROVIDER);

    final var result =
        handler.handleException(
            new RuntimeException("Something went wrong"), QueueItem.of(FETCH_ITEM_DR, 0));

    assertThat(result)
        .as("... a failed report is returned for every month of the item")
        .hasSize(3)
        .allSatisfy(
            cr -> {
              assertThat(cr.getReportName()).isEqualTo("DR");
              assertThat(cr.getFailedReason()).isEqualTo("Something went wrong");
            })
        .extracting("yearMonth")
        .containsExactly("2025-01", "2025-02", "2025-03");
  }
}
