package org.olf.erm.usage.harvester.periodic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.vertx.core.Promise;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;

/** Test class for {@link HarvestProviderJob} */
class HarvestProviderJobTest {

  private static final Duration CHECK_INTERVAL = Duration.ofMillis(10);

  @Test
  void testAwaitOrCancelReturnsWhenFinished() {
    final var finished = Promise.<Void>promise();
    final var checks = new AtomicInteger();
    final var cancels = new AtomicInteger();
    final var thread =
        new Thread(
            () -> {
              sleep(50);
              finished.complete();
            });
    thread.start();

    assertThatCode(
            () ->
                HarvestProviderJob.awaitOrCancel(
                    finished.future(),
                    CHECK_INTERVAL,
                    () -> checks.incrementAndGet() < 0,
                    cancels::incrementAndGet))
        .doesNotThrowAnyException();
    assertThat(checks).as("... it checked while waiting").hasPositiveValue();
    assertThat(cancels).hasValue(0);
  }

  @Test
  void testAwaitOrCancelCancelsOnce() {
    final var finished = Promise.<Void>promise();
    final var cancels = new AtomicInteger();
    final var thread =
        new Thread(
            () -> {
              sleep(50);
              finished.fail(HarvestProviderJob.CANCELLED_MESSAGE);
            });

    assertThatThrownBy(
            () ->
                HarvestProviderJob.awaitOrCancel(
                    finished.future(),
                    CHECK_INTERVAL,
                    () -> true,
                    () -> {
                      if (cancels.incrementAndGet() == 1) {
                        thread.start();
                      }
                    }))
        .isInstanceOf(JobExecutionException.class)
        .hasRootCauseMessage(HarvestProviderJob.CANCELLED_MESSAGE);
    assertThat(cancels).as("... cancel is requested only once").hasValue(1);
  }

  @Test
  void testAwaitOrCancelGoesOnIfCheckFails() {
    final var finished = Promise.<Void>promise();
    final var checks = new AtomicInteger();

    assertThatCode(
            () ->
                HarvestProviderJob.awaitOrCancel(
                    finished.future(),
                    CHECK_INTERVAL,
                    () -> {
                      if (checks.incrementAndGet() == 3) {
                        finished.complete();
                      }
                      throw new SchedulerException("job store unavailable");
                    },
                    () -> {
                      throw new AssertionError("must not cancel");
                    }))
        .doesNotThrowAnyException();
  }

  private static void sleep(final long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
