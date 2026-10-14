package org.olf.erm.usage.harvester.endpoints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingResult.GiveUp;
import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingResult.GiveUpSlowDown;
import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingResult.RetryNow;
import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingResult.Throttle;

import java.util.stream.Stream;
import org.folio.rest.jaxrs.model.HarvestingConfig;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.olf.erm.usage.harvester.FetchItem;

/** Tests {@link DefaultErrorHandlingStrategy}. */
class DefaultErrorHandlingStrategyTest {

  private static final UsageDataProvider USAGE_DATA_PROVIDER =
      new UsageDataProvider()
          .withId("provider-id")
          .withHarvestingConfig(new HarvestingConfig().withReportRelease("5"));

  private static final FetchItem FETCH_ITEM_TR = new FetchItem("TR", "2025-01-01", "2025-01-31");

  private static final FetchItem FETCH_ITEM_DR = new FetchItem("DR", "2025-01-01", "2025-03-31");

  private final ErrorHandlingStrategy strategy = ErrorHandlingStrategy.create(USAGE_DATA_PROVIDER);

  @Test
  void testTooManyRequestsReturnsThrottleWhileRetriesRemain() {
    var result =
        strategy.handleFetchError(FETCH_ITEM_TR, 0, new TooManyRequestsException("Slow down"));

    assertThat(result)
        .isInstanceOfSatisfying(
            Throttle.class,
            throttle -> {
              assertThat(throttle.items()).containsExactly(FETCH_ITEM_TR);
              assertThat(throttle.retryCount()).isEqualTo(1);
            });
  }

  @Test
  void testTooManyRequestsReturnsThrottleForEachRetryBelowThreshold() {
    var result =
        strategy.handleFetchError(FETCH_ITEM_TR, 1, new TooManyRequestsException("Slow down"));

    assertThat(result)
        .isInstanceOfSatisfying(
            Throttle.class,
            throttle -> {
              assertThat(throttle.items()).containsExactly(FETCH_ITEM_TR);
              assertThat(throttle.retryCount()).isEqualTo(2);
            });
  }

  @Test
  void testTooManyRequestsReturnsGiveUpSlowDownWhenRetriesExhausted() {
    var result =
        strategy.handleFetchError(FETCH_ITEM_TR, 2, new TooManyRequestsException("Slow down"));

    assertThat(result)
        .isInstanceOfSatisfying(
            GiveUpSlowDown.class,
            giveUpSlowDown ->
                assertThat(giveUpSlowDown.reports())
                    .singleElement()
                    .satisfies(
                        cr -> {
                          assertThat(cr.getReportName()).isEqualTo("TR");
                          assertThat(cr.getYearMonth()).isEqualTo("2025-01");
                          assertThat(cr.getFailedReason()).isEqualTo("Slow down");
                          assertThat(cr.getProviderId()).isEqualTo("provider-id");
                          assertThat(cr.getRelease()).isEqualTo("5");
                        }));
  }

  @Test
  void testTooManyRequestsGiveUpSlowDownCreatesReportPerExpandedMonth() {
    var result =
        strategy.handleFetchError(FETCH_ITEM_DR, 2, new TooManyRequestsException("Slow down"));

    assertThat(result)
        .isInstanceOfSatisfying(
            GiveUpSlowDown.class,
            giveUpSlowDown ->
                assertThat(giveUpSlowDown.reports())
                    .hasSize(3)
                    .allSatisfy(cr -> assertThat(cr.getFailedReason()).isEqualTo("Slow down"))
                    .extracting("yearMonth")
                    .containsExactly("2025-01", "2025-02", "2025-03"));
  }

  @Test
  void testInvalidReportReturnsGiveUpForSingleMonthItem() {
    var result =
        strategy.handleFetchError(FETCH_ITEM_TR, 0, new InvalidReportException("Invalid report"));

    assertThat(result)
        .isInstanceOfSatisfying(
            GiveUp.class,
            giveUp ->
                assertThat(giveUp.reports())
                    .singleElement()
                    .satisfies(
                        cr -> {
                          assertThat(cr.getReportName()).isEqualTo("TR");
                          assertThat(cr.getYearMonth()).isEqualTo("2025-01");
                          assertThat(cr.getFailedReason())
                              .isEqualTo("Report not valid: Invalid report");
                        }));
  }

  @Test
  void testInvalidReportReturnsRetryNowForMultiMonthItem() {
    var result =
        strategy.handleFetchError(FETCH_ITEM_DR, 0, new InvalidReportException("Invalid report"));

    assertThat(result)
        .isInstanceOfSatisfying(
            RetryNow.class,
            retryNow ->
                assertThat(retryNow.items())
                    .containsExactly(
                        new FetchItem("DR", "2025-01-01", "2025-01-31"),
                        new FetchItem("DR", "2025-02-01", "2025-02-28"),
                        new FetchItem("DR", "2025-03-01", "2025-03-31")));
  }

  @Test
  void testUnknownErrorReturnsGiveUpWithFailedReportPerExpandedMonth() {
    var result =
        strategy.handleFetchError(FETCH_ITEM_DR, 0, new RuntimeException("Something went wrong"));

    assertThat(result)
        .isInstanceOfSatisfying(
            GiveUp.class,
            giveUp ->
                assertThat(giveUp.reports())
                    .hasSize(3)
                    .allSatisfy(
                        cr -> {
                          assertThat(cr.getReportName()).isEqualTo("DR");
                          assertThat(cr.getFailedReason()).isEqualTo("Something went wrong");
                        })
                    .extracting("yearMonth")
                    .containsExactly("2025-01", "2025-02", "2025-03"));
  }

  @Test
  void testUnknownErrorWithoutMessageUsesToString() {
    var error = new RuntimeException();

    var result = strategy.handleFetchError(FETCH_ITEM_TR, 0, error);

    assertThat(result)
        .isInstanceOfSatisfying(
            GiveUp.class,
            giveUp ->
                assertThat(giveUp.reports())
                    .singleElement()
                    .satisfies(cr -> assertThat(cr.getFailedReason()).isEqualTo(error.toString())));
  }

  @ParameterizedTest
  @MethodSource("tooManyRequestsCounterApiExceptions")
  void testCounterApiExceptionTooManyRequestsReturnsThrottle(CounterApiException e) {
    var result = strategy.handleFetchError(FETCH_ITEM_TR, 0, e);

    assertThat(result)
        .isInstanceOfSatisfying(
            Throttle.class,
            throttle -> {
              assertThat(throttle.items()).containsExactly(FETCH_ITEM_TR);
              assertThat(throttle.retryCount()).isEqualTo(1);
            });
  }

  @ParameterizedTest
  @MethodSource("tooManyRequestsCounterApiExceptions")
  void testCounterApiExceptionTooManyRequestsReturnsGiveUpSlowDownWhenRetriesExhausted(
      CounterApiException e) {
    var result = strategy.handleFetchError(FETCH_ITEM_DR, 2, e);

    assertThat(result)
        .isInstanceOfSatisfying(
            GiveUpSlowDown.class,
            giveUpSlowDown ->
                assertThat(giveUpSlowDown.reports())
                    .hasSize(3)
                    .allSatisfy(cr -> assertThat(cr.getFailedReason()).isEqualTo(e.getMessage()))
                    .extracting("yearMonth")
                    .containsExactly("2025-01", "2025-02", "2025-03"));
  }

  @Test
  void testCounterApiExceptionInReportHeaderReturnsGiveUpForSingleMonthItem() {
    var json = "[{\"Code\":3030,\"Message\":\"No Usage Available for Requested Dates\"}]";

    var result = strategy.handleFetchError(FETCH_ITEM_TR, 0, header(json));

    assertThat(result)
        .isInstanceOfSatisfying(
            GiveUp.class,
            giveUp ->
                assertThat(giveUp.reports())
                    .singleElement()
                    .satisfies(
                        cr -> {
                          assertThat(cr.getReportName()).isEqualTo("TR");
                          assertThat(cr.getYearMonth()).isEqualTo("2025-01");
                          assertThat(cr.getFailedReason()).isEqualTo("Report not valid: " + json);
                        }));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"Too Many Requests", "TOO MANY REQUESTS", "Client has made too many requests"})
  void testCounterApiExceptionWithAliasIgnoresCase(String message) {
    var e = header("[{\"Code\":2000,\"Message\":\"" + message + "\"}]");

    var result = strategy.handleFetchError(FETCH_ITEM_DR, 0, e);

    assertThat(result).isInstanceOf(Throttle.class);
  }

  @Test
  void testCounterApiExceptionInReportHeaderWithMessageOnlyAliasReturnsThrottle() {
    var e = header("[{\"Message\":\"Client has made too many requests\"}]");

    var result = strategy.handleFetchError(FETCH_ITEM_DR, 0, e);

    assertThat(result)
        .isInstanceOfSatisfying(
            Throttle.class,
            throttle -> assertThat(throttle.items()).containsExactly(FETCH_ITEM_DR));
  }

  @Test
  void testCounterApiExceptionInReportHeaderReturnsRetryNowForMultiMonthItem() {
    var result = strategy.handleFetchError(FETCH_ITEM_DR, 0, header("{\"Code\":3030}"));

    assertThat(result)
        .isInstanceOfSatisfying(
            RetryNow.class,
            retryNow ->
                assertThat(retryNow.items())
                    .containsExactly(
                        new FetchItem("DR", "2025-01-01", "2025-01-31"),
                        new FetchItem("DR", "2025-02-01", "2025-02-28"),
                        new FetchItem("DR", "2025-03-01", "2025-03-31")));
  }

  @Test
  void testCounterApiExceptionWithErrorStatusReturnsGiveUp() {
    var body = "{\"Code\":2010,\"Message\":\"Requestor is Not Authorized\"}";

    var result =
        strategy.handleFetchError(
            FETCH_ITEM_DR,
            0,
            CounterApiException.fromBareBody(401, "Unauthorized", body).orElseThrow());

    assertThat(result)
        .isInstanceOfSatisfying(
            GiveUp.class,
            giveUp ->
                assertThat(giveUp.reports())
                    .hasSize(3)
                    .allSatisfy(
                        cr ->
                            assertThat(cr.getFailedReason())
                                .isEqualTo("HTTP 401: Unauthorized - " + body))
                    .extracting("yearMonth")
                    .containsExactly("2025-01", "2025-02", "2025-03"));
  }

  @Test
  void testCounterApiExceptionWithSuccessStatusReturnsGiveUp() {
    var body = "{\"Code\":3030,\"Message\":\"No Usage Available for Requested Dates\"}";

    var result = strategy.handleFetchError(FETCH_ITEM_DR, 0, bare(200, body));

    assertThat(result)
        .isInstanceOfSatisfying(
            GiveUp.class,
            giveUp ->
                assertThat(giveUp.reports())
                    .hasSize(3)
                    .allSatisfy(cr -> assertThat(cr.getFailedReason()).isEqualTo(body)));
  }

  private static Stream<Arguments> tooManyRequestsCounterApiExceptions() {
    return Stream.of(
        arguments(named("1020 in report header", header("[{\"Code\":1020}]"))),
        arguments(named("1020 in bare body with 2xx", bare(200, "{\"Code\":1020}"))),
        arguments(named("1020 in bare body with 4xx", bare(400, "{\"Code\":1020}"))),
        arguments(named("1020 not first", header("[{\"Code\":3030},{\"Code\":1020}]"))),
        arguments(
            named(
                "message mentions too many requests",
                header("{\"Code\":3030,\"Message\":\"Error: too many requests\"}"))),
        arguments(named("HTTP 429 with other code", bare(429, "{\"Code\":3030}"))));
  }

  private static CounterApiException header(String exceptionsJson) {
    return CounterApiException.fromReportHeader(exceptionsJson).orElseThrow();
  }

  private static CounterApiException bare(int statusCode, String body) {
    return CounterApiException.fromBareBody(statusCode, "Some Status", body).orElseThrow();
  }
}
