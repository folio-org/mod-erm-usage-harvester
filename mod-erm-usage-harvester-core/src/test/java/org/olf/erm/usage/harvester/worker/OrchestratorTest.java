package org.olf.erm.usage.harvester.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.worker.WorkerController.QueueItem;

import com.google.common.io.Resources;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClient;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;

/** Test class for {@link Orchestrator} */
class OrchestratorTest {

  private static final int MAX_FAILED_ATTEMPTS = 1;

  /**
   * We only use one month time spans here, to avoid {@link Fetcher#collapse(List)} being called.
   */
  private static final List<FetchItem> FETCH_LIST =
      List.of(
          new FetchItem("PR", "2026-02-01", "2026-02-28"),
          new FetchItem("DR", "2025-11-01", "2025-11-30"),
          new FetchItem("TR", "2025-01-01", "2025-01-31"),
          new FetchItem("IR", "2024-04-01", "2024-04-30"));

  private static UsageDataProvider usageDataProvider;

  private static LoggerContext logCtx;

  private TestWorkerController controller;

  @BeforeAll
  static void setup() throws IOException {
    usageDataProvider =
        Json.decodeValue(
            Resources.toString(
                Resources.getResource("__files/usage-data-provider.json"), StandardCharsets.UTF_8),
            UsageDataProvider.class);

    logCtx = new LoggerContext("Tenant ID", usageDataProvider.getLabel());
  }

  @BeforeEach
  void beforeTest() {
    controller = new TestWorkerController();
  }

  private Fetcher configureFetcher(final ExtCounterReportsClient counterReportsClient) {
    return configureFetcher(counterReportsClient, List.of());
  }

  private Fetcher configureFetcher(
      final ExtCounterReportsClient counterReportsClient, final List<QueueItem> initialItems) {
    return new Fetcher(
        counterReportsClient,
        usageDataProvider,
        null,
        logCtx,
        (t, qi) -> Collections.emptyList(),
        MAX_FAILED_ATTEMPTS,
        initialItems);
  }

  @Test
  void testStartWithSuccessAndNonEmptyFetchList() {
    final var counterReportsClient = new TestCounterReportsClient(FETCH_LIST);
    final var fetcher = configureFetcher(counterReportsClient);
    final var uploader = new Uploader(counterReportsClient, controller, logCtx);
    final var orchestrator = new Orchestrator(fetcher, uploader, controller, logCtx);

    orchestrator.startHarvesting();

    assertThat(controller.queueItems)
        .as("... we got the correct number of items in the queue")
        .hasSameSizeAs(FETCH_LIST);
    assertThat(controller.queueItems)
        .as("... the items of a regular run start with a retry count of 0")
        .extracting(QueueItem::retryCount)
        .containsOnly(0);
    assertThat(controller.startQueueWithWasCalled).as("... the queue was started").isTrue();
    assertThat(controller.undeployWasCalled).as("... the queue was not undeployed").isFalse();
    assertThat(controller.abortThrowable).isNull();
  }

  @Test
  void testStartWithSuccessAndEmptyFetchList() {
    final var counterReportsClient = new TestCounterReportsClient(Collections.emptyList());
    final var fetcher = configureFetcher(counterReportsClient);
    final var uploader = new Uploader(counterReportsClient, controller, logCtx);
    final var orchestrator = new Orchestrator(fetcher, uploader, controller, logCtx);

    orchestrator.startHarvesting();

    assertThat(controller.queueItems)
        .as("... we got the correct number of items in the queue")
        .isEmpty();
    assertThat(controller.startQueueWithWasCalled).as("... the queue was not started").isFalse();
    assertThat(controller.undeployWasCalled).as("... the queue was undeployed").isTrue();
    assertThat(controller.abortThrowable).isNull();
  }

  @Test
  void testStartWithFailure() {
    final var counterReportsClient = new TestCounterReportsClient(null);
    final var fetcher = configureFetcher(counterReportsClient);
    final var uploader = new Uploader(counterReportsClient, controller, logCtx);
    final var orchestrator = new Orchestrator(fetcher, uploader, controller, logCtx);

    orchestrator.startHarvesting();

    assertThat(controller.queueItems)
        .as("... we got the correct number of items in the queue")
        .isEmpty();
    assertThat(controller.startQueueWithWasCalled).as("... the queue was not started").isFalse();
    assertThat(controller.abortThrowable).as("... the whole thing was aborted").isNotNull();
  }

  @Test
  void testStartWithInitialItemsKeepsRetryCount() {
    final var counterReportsClient = new TestCounterReportsClient(FETCH_LIST);
    final var retryItems = List.of(new QueueItem(FETCH_LIST.getFirst(), 2));
    final var fetcher = configureFetcher(counterReportsClient, retryItems);
    final var uploader = new Uploader(counterReportsClient, controller, logCtx);
    final var orchestrator = new Orchestrator(fetcher, uploader, controller, logCtx);

    orchestrator.startHarvesting();

    assertThat(controller.queueItems)
        .as("... the initial items are queued with their retry count")
        .containsExactlyElementsOf(retryItems);
    assertThat(controller.startQueueWithWasCalled).as("... the queue was started").isTrue();
    assertThat(counterReportsClient.fetchListCalls())
        .as("... the fetch list was not loaded")
        .hasValue(0);
    assertThat(controller.abortThrowable).isNull();
  }

  @Test
  void testReportsOfFetchCompletingAfterAbortAreNotUploaded() {
    final var counterReportsClient = new TestCounterReportsClient(FETCH_LIST);
    final var fetcher =
        new Fetcher(
            counterReportsClient,
            usageDataProvider,
            new SingleReportServiceEndpoint(),
            logCtx,
            (t, qi) -> Collections.emptyList(),
            MAX_FAILED_ATTEMPTS,
            List.of());
    final var uploader = new Uploader(counterReportsClient, controller, logCtx);
    final var orchestrator = new Orchestrator(fetcher, uploader, controller, logCtx);
    orchestrator.startHarvesting();

    controller.abort("aborted while the fetch was running");
    final var dispatched = controller.queueCallback.apply(controller.queueItems.getFirst());

    assertThat(dispatched.succeeded())
        .as("... the fetched report was discarded instead of uploaded")
        .isTrue();
  }

  private static class SingleReportServiceEndpoint implements ServiceEndpoint {

    @Override
    public Future<List<CounterReport>> fetchReport(
        String report, String beginDate, String endDate) {
      return Future.succeededFuture(
          List.of(new CounterReport().withReportName(report).withYearMonth("2026-02")));
    }

    @Override
    public void close() {
      // nothing to release
    }
  }
}
