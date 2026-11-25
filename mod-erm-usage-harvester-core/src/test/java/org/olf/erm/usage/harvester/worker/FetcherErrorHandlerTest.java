package org.olf.erm.usage.harvester.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.worker.TestWorkerController.ParkCall;
import static org.olf.erm.usage.harvester.worker.WorkerController.QueueItem;

import com.google.common.io.Resources;
import io.vertx.core.json.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.test.appender.ListAppender;
import org.apache.logging.log4j.message.Message;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.endpoints.CounterApiException;
import org.olf.erm.usage.harvester.endpoints.ErrorHandlingResult.RetryLater;
import org.olf.erm.usage.harvester.endpoints.ErrorHandlingStrategy;
import org.olf.erm.usage.harvester.endpoints.InvalidReportException;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.olf.erm.usage.harvester.endpoints.TooManyRequestsException;

/**
 * Tests for the {@link FetcherErrorHandler} implementation. This currently only tests the {@link
 * FetcherErrorHandler.DefaultErrorHandler} and will be replaced as soon as {@link ServiceEndpoint}
 * implementation specific error handling is in place.
 */
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

  private static final ErrorHandlingStrategy STRATEGY =
      ErrorHandlingStrategy.create(USAGE_DATA_PROVIDER);

  private static final Instant NOW = Instant.parse("2025-02-01T10:00:00Z");

  private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private static final Duration DELAY = Duration.ofMinutes(30);

  private static final String REASON = "Report Queued for Processing";

  /** A strategy that answers every error with RetryLater for the failed item. */
  private static final ErrorHandlingStrategy RETRY_LATER_STRATEGY =
      (item, retryCount, t) -> new RetryLater(List.of(item), retryCount + 1, DELAY, REASON);

  private static final String LOGGER_NAME = FetcherErrorHandler.DefaultErrorHandler.class.getName();

  private TestWorkerController controller;

  private FetcherErrorHandler handler;

  private ListAppender listAppender;

  private Level previousLevel;

  @BeforeEach
  void beforeTest() {
    controller = new TestWorkerController();
    handler = FetcherErrorHandler.create(LOG_CTX, controller, STRATEGY);

    listAppender = new ListAppender("fetcherErrorHandlerTest");
    listAppender.start();
    coreLogger().addAppender(listAppender);
    previousLevel = coreLogger().getLevel();
    Configurator.setLevel(LOGGER_NAME, Level.INFO);
  }

  @AfterEach
  void afterTest() {
    coreLogger().removeAppender(listAppender);
    listAppender.stop();
    Configurator.setLevel(LOGGER_NAME, previousLevel);
  }

  @Test
  void testTooManyRequestsHandlerReEnqueuesWhileRetriesRemain() {
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
  void testTooManyRequestsWithoutMessageUsesToStringAsFailedReason() {
    final var exception = new TooManyRequestsException();
    final var result = handler.handleException(exception, QueueItem.of(FETCH_ITEM_TR, 2));

    assertThat(result)
        .singleElement()
        .satisfies(cr -> assertThat(cr.getFailedReason()).isEqualTo(exception.toString()));
  }

  @Test
  void testDefaultFetcherErrorHandlerUsesToStringIfMessageIsNull() {
    final var result = handler.handleException(new Exception(), QueueItem.of(FETCH_ITEM_DR, 0));

    assertThat(result)
        .as("... the failed reason falls back to toString() for every month of the item")
        .hasSize(3)
        .allSatisfy(cr -> assertThat(cr.getFailedReason()).isEqualTo("java.lang.Exception"));
  }

  @Test
  void testDefaultFetcherErrorHandlerReturnsFailedReportPerMonth() {
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

  @Test
  void testCounterApiExceptionIsLoggedWithCodesAndOutcome() {
    final var exception =
        CounterApiException.fromReportHeader("[{\"Code\":3030},{\"Code\":1020}]").orElseThrow();

    final var result = handler.handleException(exception, QueueItem.of(FETCH_ITEM_TR, 0));

    assertThat(result).as("... no reports are returned yet").isEmpty();
    assertThat(controller.concurrencyWasDisabled).as("... concurrency was disabled").isTrue();
    assertThat(loggedMessages())
        .as("... the codes, the delivery and the outcome are logged")
        .anyMatch(
            msg ->
                msg.contains(
                    "COUNTER exception code(s) [3030, 1020], delivered in"
                        + " REPORT_HEADER, handled as Throttle"));
  }

  @Test
  void testCounterApiExceptionWithErrorStatusReturnsFailedReportNamingTheCode() {
    final var body = "{\"Code\":2010,\"Message\":\"Requestor is Not Authorized\"}";
    final var exception = CounterApiException.fromBareBody(403, "Forbidden", body).orElseThrow();

    final var result = handler.handleException(exception, QueueItem.of(FETCH_ITEM_TR, 0));

    assertThat(result)
        .as("... a failed report naming the code and message is returned")
        .singleElement()
        .satisfies(
            cr -> assertThat(cr.getFailedReason()).isEqualTo("HTTP 403: Forbidden - " + body));
    assertThat(loggedMessages())
        .anyMatch(
            msg ->
                msg.contains(
                    "COUNTER exception code(s) [2010], delivered in"
                        + " BARE_ERROR_STATUS, handled as GiveUp"));
  }

  @Test
  void testOtherExceptionsAreNotLoggedAsCounterExceptions() {
    handler.handleException(
        new InvalidReportException("Invalid report"), QueueItem.of(FETCH_ITEM_TR, 0));

    assertThat(loggedMessages()).noneMatch(msg -> msg.contains("COUNTER exception code(s)"));
  }

  @Test
  void testRetryLaterParksTheItemWithDueTimeAndRetryCount() {
    final var retryHandler =
        FetcherErrorHandler.create(LOG_CTX, controller, RETRY_LATER_STRATEGY, FIXED_CLOCK);

    final var result =
        retryHandler.handleException(
            new RuntimeException("try later"), QueueItem.of(FETCH_ITEM_TR, 0));

    assertThat(result).as("... no reports are returned").isEmpty();
    assertThat(controller.parkCalls)
        .as("... the item was parked once, due after the delay, with an incremented retry count")
        .containsExactly(
            new ParkCall(List.of(QueueItem.of(FETCH_ITEM_TR, 1)), NOW.plus(DELAY), REASON));
    assertThat(controller.queueItems).as("... nothing was re-enqueued").isEmpty();
    assertThat(controller.concurrencyWasDisabled).as("... concurrency was not disabled").isFalse();
  }

  @Test
  void testRetryLaterUsesTheRetryCountFromTheResult() {
    final var retryHandler =
        FetcherErrorHandler.create(LOG_CTX, controller, RETRY_LATER_STRATEGY, FIXED_CLOCK);

    retryHandler.handleException(new RuntimeException("try later"), QueueItem.of(FETCH_ITEM_TR, 2));

    assertThat(controller.parkCalls)
        .singleElement()
        .satisfies(
            call -> assertThat(call.items()).containsExactly(QueueItem.of(FETCH_ITEM_TR, 3)));
  }

  @Test
  void testRetryLaterParksAllItemsInOneCall() {
    final var monthItems = FETCH_ITEM_DR.expand();
    final ErrorHandlingStrategy splittingStrategy =
        (item, retryCount, t) -> new RetryLater(item.expand(), retryCount + 1, DELAY, REASON);
    final var retryHandler =
        FetcherErrorHandler.create(LOG_CTX, controller, splittingStrategy, FIXED_CLOCK);

    retryHandler.handleException(new RuntimeException("try later"), QueueItem.of(FETCH_ITEM_DR, 0));

    assertThat(controller.parkCalls)
        .as("... all items were parked in one call with the same due time")
        .containsExactly(
            new ParkCall(QueueItem.createQueueItemList(monthItems, 1), NOW.plus(DELAY), REASON));
  }

  @Test
  void testRetryLaterForCounterApiExceptionIsLogged() {
    final var exception = CounterApiException.fromReportHeader("[{\"Code\":1011}]").orElseThrow();
    final var retryHandler =
        FetcherErrorHandler.create(LOG_CTX, controller, RETRY_LATER_STRATEGY, FIXED_CLOCK);

    retryHandler.handleException(exception, QueueItem.of(FETCH_ITEM_TR, 0));

    assertThat(loggedMessages())
        .as("... the outcome and the parking are logged")
        .anyMatch(
            msg ->
                msg.contains("COUNTER exception code(s) [1011]")
                    && msg.contains("handled as RetryLater"))
        .anyMatch(
            msg -> msg.contains("Parking 1 item(s)") && msg.contains(NOW.plus(DELAY).toString()));
  }

  private List<String> loggedMessages() {
    return listAppender.getEvents().stream()
        .map(LogEvent::getMessage)
        .map(Message::getFormattedMessage)
        .toList();
  }

  private static org.apache.logging.log4j.core.Logger coreLogger() {
    return (org.apache.logging.log4j.core.Logger) LogManager.getLogger(LOGGER_NAME);
  }
}
