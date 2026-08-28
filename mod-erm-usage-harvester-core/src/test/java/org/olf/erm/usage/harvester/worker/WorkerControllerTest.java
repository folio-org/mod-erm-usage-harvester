package org.olf.erm.usage.harvester.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.worker.WorkerController.DefaultWorkerController;
import static org.olf.erm.usage.harvester.worker.WorkerController.QueueItem;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
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

    controller.undeploy();
    assertThat(controller.getQueue()).as("Check queue size").isEmpty();
  }
}
