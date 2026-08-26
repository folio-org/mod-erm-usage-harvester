package org.olf.erm.usage.harvester.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.worker.WorkerController.QueueItem;

import com.google.common.io.Resources;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.ext.web.client.HttpResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.folio.rest.jaxrs.model.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.FetchListUtil;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClient;
import org.olf.erm.usage.harvester.endpoints.InvalidReportException;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.olf.erm.usage.harvester.endpoints.TooManyRequestsException;

/** Test class for {@link Fetcher}. */
public class FetcherTest {

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

  private static List<CounterReport> counterReports;

  private static UsageDataProvider usageDataProvider;

  private static LoggerContext logCtx;

  private TestWorkerController controller;

  @BeforeClass
  public static void setup() throws IOException {
    final var counterReport =
        Json.decodeValue(
            Resources.toString(
                Resources.getResource("__files/counter-report-tr.json"), StandardCharsets.UTF_8),
            CounterReport.class);
    counterReports = List.of(counterReport);

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
  public void testGetFetchListWithSuccess() {
    final var counterReportsClient = new TestCounterReportsClient(FETCH_LIST);
    final var fetcher = new Fetcher(counterReportsClient, null, null, controller, logCtx);

    final var receivedFuture = fetcher.getFetchList(5);
    assertThat(receivedFuture.succeeded()).as("... the future succeeded").isTrue();
    assertThat(receivedFuture.result())
        .as("... it contains the expected result")
        .containsExactlyInAnyOrderElementsOf(FETCH_LIST);
  }

  @Test
  public void testGetFetchListWithError() {
    final var counterReportsClient = new TestCounterReportsClient(null);
    final var fetcher = new Fetcher(counterReportsClient, null, null, controller, logCtx);

    final var receivedFuture = fetcher.getFetchList(5);
    assertThat(receivedFuture.failed()).as("... the future failed").isTrue();
  }

  @Test
  public void testFetchReportWithSuccess() {
    final var serviceEndpoint = new TestServiceEndpoint(counterReports, null);
    final var fetcher = new Fetcher(null, null, serviceEndpoint, controller, logCtx);

    final var receivedFuture = fetcher.fetchReport(QueueItem.of(FETCH_LIST.getFirst(), 1));
    assertThat(receivedFuture.succeeded()).as("... the future succeeded").isTrue();
    assertThat(receivedFuture.result())
        .as("... it contains the expected result")
        .containsExactlyElementsOf(counterReports);
  }

  @Test
  public void testFetchReportWithTooManyRequestsErrorBelowRetryThreshold() {
    final var serviceEndpoint =
        new TestServiceEndpoint(null, new TooManyRequestsException("Don't be too greedy"));
    final var fetcher = new Fetcher(null, usageDataProvider, serviceEndpoint, controller, logCtx);

    final var receivedFuture = fetcher.fetchReport(QueueItem.of(FETCH_LIST.getFirst(), 1));
    assertThat(receivedFuture.succeeded()).as("... the future succeeded").isTrue();
    assertThat(receivedFuture.result())
        .as("... it contains the expected result")
        .isEqualTo(Collections.emptyList());

    assertThat(controller.concurrencyWasDisabled).as("... the concurrency was disabled").isTrue();
    assertThat(controller.queueItems)
        .as("... it contains the expected result")
        .containsExactlyElementsOf(List.of(QueueItem.of(FETCH_LIST.getFirst(), 1 + 1)));
  }

  @Test
  public void testFetchReportWithTooManyRequestsErrorAboveRetryThreshold() {
    final var serviceEndpoint =
        new TestServiceEndpoint(null, new TooManyRequestsException("Don't be too greedy"));
    final var fetcher = new Fetcher(null, usageDataProvider, serviceEndpoint, controller, logCtx);

    final var receivedFuture = fetcher.fetchReport(QueueItem.of(FETCH_LIST.getFirst(), 2));
    assertThat(receivedFuture.succeeded()).as("... the future succeeded").isTrue();
    assertThat(receivedFuture.result().getFirst().getFailedReason())
        .as("... it contains the failed reason")
        .contains("Don't be too greedy");

    assertThat(controller.concurrencyWasDisabled).as("... the concurrency was disabled").isTrue();
    assertThat(controller.queueItems)
        .as("... it contains the expected result")
        .isEqualTo(Collections.emptyList());
  }

  @Test
  public void testFetchReportWithInvalidReportError() {
    final var serviceEndpoint =
        new TestServiceEndpoint(null, new InvalidReportException("Please be precise"));
    final var fetcher = new Fetcher(null, usageDataProvider, serviceEndpoint, controller, logCtx);

    final var receivedFuture = fetcher.fetchReport(QueueItem.of(FETCH_LIST.getFirst(), 1));
    assertThat(receivedFuture.succeeded()).as("... the future succeeded").isTrue();
    assertThat(receivedFuture.result().getFirst().getFailedReason())
        .as("... it contains the failed reason")
        .contains("Please be precise");

    assertThat(controller.concurrencyWasDisabled)
        .as("... the concurrency was not disabled")
        .isFalse();
  }

  @Test
  public void testFetchReportWithUnknownError() {
    final var serviceEndpoint =
        new TestServiceEndpoint(null, new RuntimeException("Something just went wrong"));
    final var fetcher = new Fetcher(null, usageDataProvider, serviceEndpoint, controller, logCtx);

    final var receivedFuture = fetcher.fetchReport(QueueItem.of(FETCH_LIST.getFirst(), 1));
    assertThat(receivedFuture.succeeded()).as("... the future succeeded").isTrue();
    assertThat(receivedFuture.result().getFirst().getFailedReason())
        .as("... it contains the failed reason")
        .contains("Something just went wrong");

    assertThat(controller.concurrencyWasDisabled)
        .as("... the concurrency was not disabled")
        .isFalse();
  }

  static class TestWorkerController implements WorkerController {

    final List<QueueItem> queueItems = new ArrayList<>();

    boolean concurrencyWasDisabled = false;

    boolean startQueueWithWasCalled = false;

    boolean undeployWasCalled = false;

    String abortMessage = "";

    Throwable abortThrowable;

    @Override
    public void enqueue(List<QueueItem> items) {
      queueItems.addAll(items);
    }

    @Override
    public void disableConcurrency() {
      concurrencyWasDisabled = true;
    }

    @Override
    public void startQueueWith(Function<QueueItem, Future<Void>> callback) {
      this.startQueueWithWasCalled = true;
    }

    @Override
    public void abort(Throwable cause) {
      this.abortThrowable = cause;
    }

    @Override
    public void abort(String message) {
      this.abortMessage = message;
    }

    @Override
    public void undeploy() {
      this.undeployWasCalled = true;
    }
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

  private record TestServiceEndpoint(
      List<CounterReport> counterReports, RuntimeException exceptionToThrow)
      implements ServiceEndpoint {

    @Override
    public Future<List<CounterReport>> fetchReport(
        String report, String beginDate, String endDate) {
      return exceptionToThrow != null
          ? Future.failedFuture(exceptionToThrow)
          : Future.succeededFuture(counterReports);
    }
  }
}
