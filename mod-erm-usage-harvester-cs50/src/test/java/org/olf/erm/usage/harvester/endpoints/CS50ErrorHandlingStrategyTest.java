package org.olf.erm.usage.harvester.endpoints;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.rest.jaxrs.model.HarvestingConfig;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.jupiter.api.Test;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.endpoints.ErrorHandlingResult.GiveUp;
import org.olf.erm.usage.harvester.endpoints.ErrorHandlingResult.RetryNow;
import org.olf.erm.usage.harvester.endpoints.ErrorHandlingResult.Throttle;

/** Tests {@link CS50ErrorHandlingStrategy}. */
class CS50ErrorHandlingStrategyTest {

  private static final UsageDataProvider USAGE_DATA_PROVIDER =
      new UsageDataProvider()
          .withId("provider-id")
          .withHarvestingConfig(new HarvestingConfig().withReportRelease("5"));

  private static final FetchItem FETCH_ITEM_DR = new FetchItem("DR", "2025-01-01", "2025-03-31");

  private static final String BODY = "{\"Code\":3030,\"Message\":\"No Usage Available\"}";

  private static final String TOO_MANY_REQUESTS_BODY =
      "{\"Code\":1020,\"Message\":\"Client has made too many requests\"}";

  private final ErrorHandlingStrategy strategy = new CS50ErrorHandlingStrategy(USAGE_DATA_PROVIDER);

  @Test
  void testBareExceptionWithSuccessStatusReturnsGiveUp() {
    var result = strategy.handleFetchError(FETCH_ITEM_DR, 0, bare(200, BODY));

    assertThat(result)
        .isInstanceOfSatisfying(GiveUp.class, giveUp -> assertThat(giveUp.reports()).hasSize(3));
  }

  @Test
  void testBareTooManyRequestsWithSuccessStatusReturnsGiveUp() {
    var result = strategy.handleFetchError(FETCH_ITEM_DR, 0, bare(200, TOO_MANY_REQUESTS_BODY));

    assertThat(result)
        .isInstanceOfSatisfying(GiveUp.class, giveUp -> assertThat(giveUp.reports()).hasSize(3));
  }

  @Test
  void testBareTooManyRequestsWithErrorStatusReturnsGiveUp() {
    var result = strategy.handleFetchError(FETCH_ITEM_DR, 0, bare(400, TOO_MANY_REQUESTS_BODY));

    assertThat(result)
        .isInstanceOfSatisfying(GiveUp.class, giveUp -> assertThat(giveUp.reports()).hasSize(3));
  }

  @Test
  void testBareExceptionWithStatus429ReturnsThrottle() {
    var result = strategy.handleFetchError(FETCH_ITEM_DR, 0, bare(429, BODY));

    assertThat(result)
        .isInstanceOfSatisfying(
            Throttle.class,
            throttle -> assertThat(throttle.items()).containsExactly(FETCH_ITEM_DR));
  }

  @Test
  void testHeaderTooManyRequestsReturnsThrottle() {
    var result = strategy.handleFetchError(FETCH_ITEM_DR, 0, header(TOO_MANY_REQUESTS_BODY));

    assertThat(result)
        .isInstanceOfSatisfying(
            Throttle.class,
            throttle -> assertThat(throttle.items()).containsExactly(FETCH_ITEM_DR));
  }

  @Test
  void testHeaderMessageOnlyTooManyRequestsReturnsThrottle() {
    var result =
        strategy.handleFetchError(
            FETCH_ITEM_DR, 0, header("{\"Message\":\"Client has made too many requests\"}"));

    assertThat(result)
        .isInstanceOfSatisfying(
            Throttle.class,
            throttle -> assertThat(throttle.items()).containsExactly(FETCH_ITEM_DR));
  }

  @Test
  void testHeaderExceptionReturnsRetryNow() {
    var result = strategy.handleFetchError(FETCH_ITEM_DR, 0, header(BODY));

    assertThat(result)
        .isInstanceOfSatisfying(
            RetryNow.class, retryNow -> assertThat(retryNow.items()).hasSize(3));
  }

  private static CounterApiException bare(int statusCode, String body) {
    return CounterApiException.fromBareBody(statusCode, "Some Status", body).orElseThrow();
  }

  private static CounterApiException header(String exception) {
    return CounterApiException.fromReportHeader("[" + exception + "]").orElseThrow();
  }
}
