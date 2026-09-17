package org.olf.erm.usage.harvester.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.worker.Fetcher.collapse;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClient;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.olf.erm.usage.harvester.endpoints.exceptions.InvalidReportException;
import org.olf.erm.usage.harvester.endpoints.exceptions.TooManyRequestsException;

/** Test class for {@link Fetcher}. */
class FetcherTest {

  private static final List<FetchItem> FETCH_LIST =
      List.of(
          new FetchItem("PR", "2026-02-01", "2026-02-28"),
          new FetchItem("DR", "2025-11-01", "2025-11-30"),
          new FetchItem("TR", "2025-01-01", "2025-01-31"),
          new FetchItem("IR", "2024-04-01", "2024-04-30"));

  private static final List<Throwable> HANDLER_CALLS = new ArrayList<>();

  private static final CounterReport COUNTER_REPORT =
      new CounterReport().withReportName("TR").withYearMonth("2025-01");

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

  @BeforeEach
  void beforeTest() {
    HANDLER_CALLS.clear();
  }

  private static Fetcher configureFetcher(
      final ExtCounterReportsClient counterReportsClient, final ServiceEndpoint serviceEndpoint) {
    return new Fetcher(
        counterReportsClient,
        USAGE_DATA_PROVIDER,
        serviceEndpoint,
        LOG_CTX,
        (t, qi) -> {
          HANDLER_CALLS.add(t);
          return Collections.emptyList();
        });
  }

  @Test
  void testGetFetchListWithSuccess() {
    final var counterReportsClient = new TestCounterReportsClient(FETCH_LIST);
    final var fetcher = configureFetcher(counterReportsClient, null);

    final var receivedFuture = fetcher.getFetchList(5);
    assertThat(receivedFuture.succeeded()).as("... the future succeeded").isTrue();
    assertThat(receivedFuture.result())
        .as("... it contains the expected result")
        .containsExactlyInAnyOrderElementsOf(FETCH_LIST);
  }

  @Test
  void testGetFetchListWithError() {
    final var counterReportsClient = new TestCounterReportsClient(null);
    final var fetcher = configureFetcher(counterReportsClient, null);

    final var receivedFuture = fetcher.getFetchList(5);
    assertThat(receivedFuture.failed()).as("... the future failed").isTrue();
  }

  @Test
  void testFetchReportWithSuccess() {
    final var serviceEndpoint = new TestServiceEndpoint(List.of(COUNTER_REPORT), null);
    final var fetcher = configureFetcher(null, serviceEndpoint);

    final var receivedFuture = fetcher.fetchReport(QueueItem.of(FETCH_LIST.getFirst(), 1));
    assertThat(receivedFuture.succeeded()).as("... the future succeeded").isTrue();
    assertThat(receivedFuture.result())
        .as("... it contains the expected result")
        .containsExactlyElementsOf(List.of(COUNTER_REPORT));
  }

  @ParameterizedTest
  @MethodSource("getTestFetchReportWithHandledErrorParameters")
  void testFetchReportWithHandledError(final RuntimeException ex) {
    final var serviceEndpoint = new TestServiceEndpoint(null, ex);
    final var fetcher = configureFetcher(null, serviceEndpoint);

    final var receivedFuture = fetcher.fetchReport(QueueItem.of(FETCH_LIST.getFirst(), 1));

    assertThat(receivedFuture.succeeded()).as("... the future succeeded").isTrue();
    assertThat(HANDLER_CALLS)
        .as("... the correct error handler was called exactly once with the thrown exception")
        .singleElement()
        .isSameAs(ex);
  }

  private static Stream<Arguments> getTestFetchReportWithHandledErrorParameters() {
    return Stream.of(
        Arguments.of(new TooManyRequestsException("Don't be too greedy")),
        Arguments.of(new InvalidReportException("Please be precise")),
        Arguments.of(new RuntimeException("Something went wrong")));
  }

  @Test
  void testCollapseJR1() {
    final String reportType = "JR1";
    List<FetchItem> fetchItemList =
        List.of(
            FetchItem.of(reportType, YearMonth.of(2019, 12)),
            FetchItem.of(reportType, YearMonth.of(2020, 1)),
            FetchItem.of(reportType, YearMonth.of(2020, 2)));
    List<FetchItem> result = collapse(fetchItemList);

    assertThat(fetchItemList).hasSize(3);
    assertThat(result)
        .hasSize(1)
        .containsExactly(FetchItem.of(reportType, YearMonth.of(2019, 12), YearMonth.of(2020, 2)));
  }

  @Test
  void testCollapseTR() {
    final String reportType = "TR";
    List<FetchItem> fetchItemList =
        List.of(
            FetchItem.of(reportType, YearMonth.of(2019, 12)),
            FetchItem.of(reportType, YearMonth.of(2020, 1)),
            FetchItem.of(reportType, YearMonth.of(2020, 2)));
    List<FetchItem> result = collapse(fetchItemList);

    assertThat(fetchItemList).hasSize(3);
    assertThat(result).hasSize(3).containsExactlyElementsOf(fetchItemList);
  }

  @Test
  void testCollapseMultipleReportTypes() {
    List<FetchItem> collapsed = collapse(createSampleFetchList());
    assertThat(collapsed)
        .containsExactlyInAnyOrder(
            FetchItem.of("JR1", YearMonth.of(2018, 1), YearMonth.of(2018, 12)),
            FetchItem.of("JR1", YearMonth.of(2019, 1), YearMonth.of(2019, 12)),
            FetchItem.of("JR1", YearMonth.of(2020, 1), YearMonth.of(2020, 12)),
            FetchItem.of("JR1", YearMonth.of(2021, 1), YearMonth.of(2021, 4)),
            FetchItem.of("JR1", YearMonth.of(2022, 1), YearMonth.of(2022, 2)),
            FetchItem.of("PR1", YearMonth.of(2018, 7), YearMonth.of(2019, 6)),
            FetchItem.of("PR1", YearMonth.of(2019, 7), YearMonth.of(2019, 8)),
            FetchItem.of("TR", YearMonth.of(2022, 1)),
            FetchItem.of("TR", YearMonth.of(2022, 2)));
  }

  private static List<FetchItem> createSampleFetchList() {
    List<YearMonth> months1 =
        IntStream.range(0, 40).boxed().map(YearMonth.of(2018, 1)::plusMonths).toList();
    List<YearMonth> months2 = Arrays.asList(YearMonth.of(2022, 1), YearMonth.of(2022, 2));
    List<YearMonth> months3 =
        IntStream.range(0, 14).boxed().map(YearMonth.of(2018, 7)::plusMonths).toList();

    List<FetchItem> jr1 =
        Stream.concat(months1.stream(), months2.stream())
            .map(ym -> FetchItem.of("JR1", ym))
            .toList();

    List<FetchItem> pr1 = months3.stream().map(ym -> FetchItem.of("PR1", ym)).toList();

    List<FetchItem> tr = months2.stream().map(ym -> FetchItem.of("TR", ym)).toList();

    List<FetchItem> duplicates =
        List.of(
            FetchItem.of("JR1", YearMonth.of(2018, 1)), FetchItem.of("PR1", YearMonth.of(2018, 7)));

    return Stream.of(jr1, pr1, tr, duplicates).flatMap(Collection::stream).toList();
  }

  @Test
  void testCollapseAndExpandMultipleReportTypes() {
    List<FetchItem> distinctFetchList = createSampleFetchList().stream().distinct().toList();
    List<FetchItem> collapsed = collapse(distinctFetchList);
    List<FetchItem> expanded =
        collapsed.stream().flatMap(fi -> fi.expand().stream()).collect(Collectors.toList());
    assertThat(distinctFetchList).containsExactlyInAnyOrderElementsOf(expanded);
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
