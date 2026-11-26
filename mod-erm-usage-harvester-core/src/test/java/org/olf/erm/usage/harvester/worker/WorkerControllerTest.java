package org.olf.erm.usage.harvester.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.olf.erm.usage.harvester.worker.WorkerController.DefaultWorkerController;
import static org.olf.erm.usage.harvester.worker.WorkerController.QueueItem;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.worker.WorkerVerticle.ParkedItem;

/** Tests for {@link WorkerController}. */
@ExtendWith(VertxExtension.class)
class WorkerControllerTest {

  private static final Vertx VERTX = Vertx.vertx();

  private static final Context CONTEXT = Vertx.vertx().getOrCreateContext();

  private static final LoggerContext LOG_CTX =
      new LoggerContext("Tenant ID", "Usage Data Provider ID");

  private static final List<FetchItem> FETCH_ITEMS =
      List.of(
          new FetchItem("TR", "2025-01-01", "2025-01-31"),
          new FetchItem("DR", "2024-03-01", "2025-03-31"));

  private static final List<QueueItem> QUEUE_ITEMS = QueueItem.createQueueItemList(FETCH_ITEMS, 2);

  private static final Instant DUE_AT = Instant.parse("2025-02-01T10:30:00Z");

  private static final String REASON = "Report Queued for Processing";

  private static final List<ParkedItem> PARKED_ITEMS =
      List.of(
          new ParkedItem(FETCH_ITEMS.getFirst(), 2, DUE_AT, REASON),
          new ParkedItem(FETCH_ITEMS.get(1), 2, DUE_AT, REASON));

  private DefaultWorkerController controller;

  @BeforeEach
  void beforeTest() {
    controller = new DefaultWorkerController(VERTX, CONTEXT, 2, LOG_CTX);
  }

  @Test
  void testEnqueue() {
    controller.enqueue(QUEUE_ITEMS);
    assertThat(controller.getQueue()).as("Check queue size").hasSameSizeAs(QUEUE_ITEMS);
    assertThat(controller.getQueue())
        .as("Check for identical elements")
        .containsExactlyInAnyOrderElementsOf(QUEUE_ITEMS);
  }

  @Test
  void testDisableConcurrency() {
    assertThat(controller.getMaxConcurrency()).as("Check maximum concurrency").isEqualTo(2);
    controller.disableConcurrency();
    assertThat(controller.getMaxConcurrency()).as("Check maximum concurrency").isEqualTo(1);
  }

  @Test
  void testStartQueueWith(VertxTestContext context) throws InterruptedException {
    final List<QueueItem> processed = Collections.synchronizedList(new ArrayList<>());

    controller.enqueue(QUEUE_ITEMS);
    controller.startQueueWith(
        qi -> {
          processed.add(qi);
          if (processed.size() == QUEUE_ITEMS.size()) {
            context.completeNow();
          }
          return Future.succeededFuture();
        });

    assertThat(context.awaitCompletion(2, TimeUnit.SECONDS))
        .as("... all queue items were processed before the timeout")
        .isTrue();
    assertThat(processed).containsExactlyInAnyOrderElementsOf(QUEUE_ITEMS);
  }

  @Test
  void testAbortWithThrowable() {
    final var success = new AtomicBoolean(true);
    final var throwable = new AtomicReference<Throwable>(null);

    final var exception = new RuntimeException("Something went wrong");
    controller.abort(exception);
    controller
        .getFinished()
        .future()
        .onSuccess(v -> success.set(true))
        .onFailure(
            t -> {
              success.set(false);
              throwable.set(t);
            });

    assertThat(success.get()).as("Success should be false").isFalse();
    assertThat(throwable.get()).as("The exception should have been forwarded").isNotNull();
  }

  @Test
  void testNoQueueItemIsStartedAfterAbort() {
    final List<QueueItem> processed = Collections.synchronizedList(new ArrayList<>());

    controller.abort("Something went wrong");
    // e.g. items the error handler re-enqueues after the abort cleared the queue
    controller.enqueue(QUEUE_ITEMS);
    controller.startQueueWith(
        qi -> {
          processed.add(qi);
          return Future.succeededFuture();
        });

    assertThat(controller.isAborted()).isTrue();
    assertThat(processed).as("... no queue item was started").isEmpty();
  }

  @Test
  void testAbortWithMessage() {
    final var success = new AtomicBoolean(true);
    final var message = new AtomicReference<String>("");

    controller.abort("Something went wrong");
    controller
        .getFinished()
        .future()
        .onSuccess(v -> success.set(true))
        .onFailure(
            t -> {
              success.set(false);
              message.set(t.getMessage());
            });

    assertThat(success.get()).as("Success should be false").isFalse();
    assertThat(message.get())
        .as("The exception should have been forwarded")
        .isEqualTo("Something went wrong");
  }

  @Test
  void testUndeploy() {
    controller.enqueue(QUEUE_ITEMS);
    assertThat(controller.getQueue()).as("Check queue size").hasSameSizeAs(QUEUE_ITEMS);

    assertThat(controller.undeploy().succeeded()).as("Check undeploy succeeded").isTrue();
    assertThat(controller.getQueue()).as("Check queue size").isEmpty();
    assertThat(controller.getFinished().future().succeeded()).as("Check run completed").isTrue();
  }

  @Test
  void testParkAndSealParked() {
    controller.park(QUEUE_ITEMS, DUE_AT, REASON);

    final var parked = controller.sealParked();
    assertThat(parked)
        .as("... the parked items are returned with their retry count, due time and reason")
        .containsExactlyElementsOf(PARKED_ITEMS);
    assertThat(controller.getQueue()).as("... parked items are not queued").isEmpty();
    assertThatThrownBy(parked::clear)
        .as("... the snapshot is immutable")
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void testParkAfterSealIsIgnored() {
    controller.park(QUEUE_ITEMS.subList(0, 1), DUE_AT, REASON);
    final var first = controller.sealParked();

    controller.park(QUEUE_ITEMS.subList(1, 2), DUE_AT, REASON);

    assertThat(controller.sealParked())
        .as("... a later park adds nothing, and sealing again returns the same items")
        .isEqualTo(first)
        .containsExactly(PARKED_ITEMS.getFirst());
  }

  @Test
  void testParkedItemsSurviveUndeployAndAbort() {
    controller.park(QUEUE_ITEMS, DUE_AT, REASON);

    controller.undeploy();
    controller.abort("Something went wrong");

    assertThat(controller.getFinished().future().isComplete())
        .as("... parked items don't keep the controller from finishing")
        .isTrue();
    assertThat(controller.sealParked())
        .as("... parked items are kept")
        .containsExactlyElementsOf(PARKED_ITEMS);
  }

  @Test
  void testQueueItemsFromParked() {
    final var parked =
        List.of(
            new ParkedItem(FETCH_ITEMS.getFirst(), 1, DUE_AT, REASON),
            new ParkedItem(FETCH_ITEMS.get(1), 3, DUE_AT.plusSeconds(60), REASON));

    assertThat(QueueItem.fromParked(parked))
        .as("... each item keeps its retry count, in the same order")
        .containsExactly(
            QueueItem.of(FETCH_ITEMS.getFirst(), 1), QueueItem.of(FETCH_ITEMS.get(1), 3));
  }
}
