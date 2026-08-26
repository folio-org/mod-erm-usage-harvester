package org.olf.erm.usage.harvester.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.worker.FetcherTest.TestWorkerController;

import com.google.common.io.Resources;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.ext.web.client.HttpResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.FetchListUtil;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClient;

/** Test class for {@link Orchestrator} */
public class OrchestratorTest {

  /**
   * We only use one month time spans here, to avoid {@link FetchListUtil#collapse(List)} being
   * called.
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

  @BeforeClass
  public static void setup() throws IOException {
    usageDataProvider =
        Json.decodeValue(
            Resources.toString(
                Resources.getResource("__files/usage-data-provider.json"), StandardCharsets.UTF_8),
            UsageDataProvider.class);

    logCtx = new LoggerContext("Tenant ID", usageDataProvider.getLabel());
  }

  @Before
  public void beforeTest() {
    controller = new TestWorkerController();
  }

  @Test
  public void testStartWithSuccessAndNonEmptyFetchList() {
    final var counterReportsClient = new TestCounterReportsClient(FETCH_LIST);
    final var fetcher =
        new Fetcher(counterReportsClient, usageDataProvider, null, controller, logCtx);
    final var uploader = new Uploader(counterReportsClient, controller, logCtx);
    final var orchestrator = new Orchestrator(fetcher, uploader, controller, logCtx);

    orchestrator.startWith(Future.succeededFuture(1));

    assertThat(controller.queueItems)
        .as("... we got the correct number of items in the queue")
        .hasSameSizeAs(FETCH_LIST);
    assertThat(controller.startQueueWithWasCalled).as("... the queue was started").isTrue();
    assertThat(controller.undeployWasCalled).as("... the queue was not undeployed").isFalse();
    assertThat(controller.abortThrowable).isNull();
  }

  @Test
  public void testStartWithSuccessAndEmptyFetchList() {
    final var counterReportsClient = new TestCounterReportsClient(Collections.emptyList());
    final var fetcher =
        new Fetcher(counterReportsClient, usageDataProvider, null, controller, logCtx);
    final var uploader = new Uploader(counterReportsClient, controller, logCtx);
    final var orchestrator = new Orchestrator(fetcher, uploader, controller, logCtx);

    orchestrator.startWith(Future.succeededFuture(1));

    assertThat(controller.queueItems)
        .as("... we got the correct number of items in the queue")
        .isEmpty();
    assertThat(controller.startQueueWithWasCalled).as("... the queue was not started").isFalse();
    assertThat(controller.undeployWasCalled).as("... the queue was undeployed").isTrue();
    assertThat(controller.abortThrowable).isNull();
  }

  @Test
  public void testStartWithFailure() {
    final var counterReportsClient = new TestCounterReportsClient(null);
    final var fetcher =
        new Fetcher(counterReportsClient, usageDataProvider, null, controller, logCtx);
    final var uploader = new Uploader(counterReportsClient, controller, logCtx);
    final var orchestrator = new Orchestrator(fetcher, uploader, controller, logCtx);

    orchestrator.startWith(Future.succeededFuture(1));

    assertThat(controller.queueItems)
        .as("... we got the correct number of items in the queue")
        .isEmpty();
    assertThat(controller.startQueueWithWasCalled).as("... the queue was not started").isFalse();
    assertThat(controller.abortThrowable).as("... the whole thing was aborted").isNotNull();
  }

  private record TestCounterReportsClient(List<FetchItem> fetchItems)
      implements ExtCounterReportsClient {

    @Override
    public Future<CounterReport> getReport(CounterReport report, boolean tiny) {
      // No-op, since we don't need it
      return null;
    }

    @Override
    public Future<HttpResponse<Buffer>> upsertReport(CounterReport report) {
      // No-op, since we don't need it
      return null;
    }

    @Override
    public Future<List<FetchItem>> getFetchList(UsageDataProvider provider, int maxFailedAttempts) {
      if (fetchItems == null) {
        return Future.failedFuture(new NullPointerException());
      }
      return Future.succeededFuture(fetchItems);
    }

    @Override
    public Future<List<YearMonth>> getValidMonths(
        String providerId,
        String reportName,
        String reportRelease,
        YearMonth start,
        YearMonth end,
        int maxFailedAttempts) {
      // No-op, since we don't need it
      return null;
    }
  }
}
