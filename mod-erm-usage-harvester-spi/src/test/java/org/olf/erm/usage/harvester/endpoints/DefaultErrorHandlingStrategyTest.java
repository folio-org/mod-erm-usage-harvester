package org.olf.erm.usage.harvester.endpoints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingResult.GiveUp;
import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingResult.GiveUpSlowDown;
import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingResult.RetryNow;
import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingResult.Throttle;
import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingStrategy.DefaultErrorHandlingStrategy;

import org.folio.rest.jaxrs.model.HarvestingConfig;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.jupiter.api.Test;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.endpoints.exceptions.InvalidReportException;
import org.olf.erm.usage.harvester.endpoints.exceptions.TooManyRequestsException;

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
}
