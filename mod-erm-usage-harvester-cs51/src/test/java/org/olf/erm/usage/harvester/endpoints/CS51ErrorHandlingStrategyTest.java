package org.olf.erm.usage.harvester.endpoints;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.rest.jaxrs.model.HarvestingConfig;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.jupiter.api.Test;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.endpoints.ErrorHandlingResult.GiveUp;
import org.olf.erm.usage.harvester.endpoints.ErrorHandlingResult.RetryNow;
import org.olf.erm.usage.harvester.endpoints.ErrorHandlingResult.Throttle;

/** Tests {@link CS51ErrorHandlingStrategy}. */
class CS51ErrorHandlingStrategyTest {

  private static final UsageDataProvider USAGE_DATA_PROVIDER =
      new UsageDataProvider()
          .withId("provider-id")
          .withHarvestingConfig(new HarvestingConfig().withReportRelease("5.1"));

  private static final FetchItem FETCH_ITEM_TR = new FetchItem("TR", "2025-01-01", "2025-01-31");

  private static final FetchItem FETCH_ITEM_DR = new FetchItem("DR", "2025-01-01", "2025-03-31");

  private static final String BODY = "{\"Code\":3030,\"Message\":\"No Usage Available\"}";

  private static final String TOO_MANY_REQUESTS_BODY =
      "{\"Code\":1020,\"Message\":\"Client has made too many requests\"}";

  private final ErrorHandlingStrategy strategy = new CS51ErrorHandlingStrategy(USAGE_DATA_PROVIDER);

  @Test
  void testBareExceptionWithSuccessStatusReturnsRetryNowForMultiMonthItem() {
    var result = strategy.handleFetchError(FETCH_ITEM_DR, 0, bare(200, BODY));

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
  void testBareExceptionWithSuccessStatusReturnsGiveUpForSingleMonthItem() {
    var result = strategy.handleFetchError(FETCH_ITEM_TR, 0, bare(200, BODY));

    assertThat(result)
        .isInstanceOfSatisfying(
            GiveUp.class,
            giveUp ->
                assertThat(giveUp.reports())
                    .singleElement()
                    .satisfies(cr -> assertThat(cr.getFailedReason()).isEqualTo(BODY)));
  }

  @Test
  void testBareExceptionWithErrorStatusReturnsGiveUp() {
    var result = strategy.handleFetchError(FETCH_ITEM_DR, 0, bare(400, BODY));

    assertThat(result)
        .isInstanceOfSatisfying(GiveUp.class, giveUp -> assertThat(giveUp.reports()).hasSize(3));
  }

  @Test
  void testBareTooManyRequestsWithSuccessStatusReturnsThrottle() {
    var result = strategy.handleFetchError(FETCH_ITEM_DR, 0, bare(200, TOO_MANY_REQUESTS_BODY));

    assertThat(result)
        .isInstanceOfSatisfying(
            Throttle.class,
            throttle -> assertThat(throttle.items()).containsExactly(FETCH_ITEM_DR));
  }

  @Test
  void testBareTooManyRequestsWithErrorStatusReturnsThrottle() {
    var result = strategy.handleFetchError(FETCH_ITEM_DR, 0, bare(400, TOO_MANY_REQUESTS_BODY));

    assertThat(result)
        .isInstanceOfSatisfying(
            Throttle.class,
            throttle -> assertThat(throttle.items()).containsExactly(FETCH_ITEM_DR));
  }

  @Test
  void testHeaderTooManyRequestsReturnsThrottle() {
    var e = CounterApiException.fromReportHeader("[" + TOO_MANY_REQUESTS_BODY + "]").orElseThrow();
    var result = strategy.handleFetchError(FETCH_ITEM_DR, 0, e);

    assertThat(result)
        .isInstanceOfSatisfying(
            Throttle.class,
            throttle -> assertThat(throttle.items()).containsExactly(FETCH_ITEM_DR));
  }

  @Test
  void testBareExceptionWithStatus429ReturnsThrottle() {
    var result = strategy.handleFetchError(FETCH_ITEM_DR, 0, bare(429, BODY));

    assertThat(result)
        .isInstanceOfSatisfying(
            Throttle.class,
            throttle -> assertThat(throttle.items()).containsExactly(FETCH_ITEM_DR));
  }

  private static CounterApiException bare(int statusCode, String body) {
    return CounterApiException.fromBareBody(statusCode, "Some Status", body).orElseThrow();
  }
}
