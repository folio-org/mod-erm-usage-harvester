package org.olf.erm.usage.harvester.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.worker.OrchestratorTest.TestWorkerController;
import static org.olf.erm.usage.harvester.worker.WorkerController.QueueItem;

import com.google.common.io.Resources;
import io.vertx.core.json.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
