package org.olf.erm.usage.harvester.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.worker.FetcherTest.TestWorkerController;

import com.google.common.io.Resources;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.client.HttpResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;
import org.folio.rest.jaxrs.model.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClient;

/** Test class for {@link Uploader}. */
public class UploaderTest {

  private static LoggerContext logCtx;

  private static List<CounterReport> counterReports;

  private TestWorkerController controller;

  @BeforeClass
  public static void setup() throws IOException {
    final var counterReport =
        Json.decodeValue(
            Resources.toString(
                Resources.getResource("__files/counter-report-tr.json"), StandardCharsets.UTF_8),
            CounterReport.class);
    counterReports = List.of(counterReport);

    final var usageDataProvider =
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
  public void testUploadReportsWithSuccess() {
    final var counterReportsClient = new TestCounterReportsClient(null);
    final var uploader = new Uploader(counterReportsClient, controller, logCtx);

    final var receivedFuture = uploader.uploadReports(counterReports);
    assertThat(receivedFuture.succeeded()).as("... the future succeeded").isTrue();
  }

  @Test
  public void testUploadReportsWithFailure() {
    final var counterReportsClient =
        new TestCounterReportsClient(new RuntimeException("Something went wrong"));
    final var uploader = new Uploader(counterReportsClient, controller, logCtx);

    final var receivedFuture = uploader.uploadReports(counterReports);
    assertThat(receivedFuture.succeeded()).as("... the future succeeded").isTrue();
    assertThat(controller.abortMessage).isEmpty();

    // To max out the number of failed uploads
    uploader.uploadReports(counterReports);
    uploader.uploadReports(counterReports);
    uploader.uploadReports(counterReports);
    uploader.uploadReports(counterReports);

    assertThat(controller.abortMessage).isEqualTo("Stopping after 5 failed uploads in a row");
  }

  private record TestCounterReportsClient(Throwable throwable) implements ExtCounterReportsClient {

    @Override
    public Future<CounterReport> getReport(CounterReport report, boolean tiny) {
      // No-op since we don't need it
      return null;
    }

    @Override
    public Future<HttpResponse<Buffer>> upsertReport(CounterReport report) {
      final var response =
          throwable != null
              ? new TestResponse(418, "What a teapot")
              : new TestResponse(200, "This was a total success");
      return throwable != null ? Future.failedFuture(throwable) : Future.succeededFuture(response);
    }

    @Override
    public Future<List<FetchItem>> getFetchList(UsageDataProvider provider, int maxFailedAttempts) {
      // No-op since we don't need it
      return null;
    }

    @Override
    public Future<List<YearMonth>> getValidMonths(
        String providerId,
        String reportName,
        String reportRelease,
        YearMonth start,
        YearMonth end,
        int maxFailedAttempts) {
      // No-op since we don't need it
      return null;
    }
  }

  private record TestResponse(int statusCode, String statusMessage)
      implements HttpResponse<Buffer> {

    @Override
    public MultiMap trailers() {
      return null;
    }

    @Override
    public String getTrailer(String trailerName) {
      return "";
    }

    @Override
    public Buffer body() {
      return null;
    }

    @Override
    public Buffer bodyAsBuffer() {
      return null;
    }

    @Override
    public List<String> followedRedirects() {
      return List.of();
    }

    @Override
    public JsonArray bodyAsJsonArray() {
      return null;
    }

    @Override
    public HttpVersion version() {
      return null;
    }

    @Override
    public int statusCode() {
      return statusCode;
    }

    @Override
    public String statusMessage() {
      return statusMessage;
    }

    @Override
    public MultiMap headers() {
      return null;
    }

    @Override
    public String getHeader(String headerName) {
      return "";
    }

    @Override
    public String getHeader(CharSequence headerName) {
      return "";
    }

    @Override
    public List<String> cookies() {
      return List.of();
    }
  }
}
