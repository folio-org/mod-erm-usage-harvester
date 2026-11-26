package org.olf.erm.usage.harvester.periodic;

import static io.vertx.core.ThreadingModel.WORKER;
import static java.util.Objects.requireNonNull;

import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.WebClientProvider;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClient;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClientImpl;
import org.olf.erm.usage.harvester.client.ExtUsageDataProvidersClient;
import org.olf.erm.usage.harvester.client.ExtUsageDataProvidersClientImpl;
import org.olf.erm.usage.harvester.errorhandling.ErrorHandlingLoader;
import org.olf.erm.usage.harvester.worker.WorkerVerticle;
import org.olf.erm.usage.harvester.worker.WorkerVerticle.ParkedItem;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HarvestProviderJob extends AbstractHarvestJob {

  private static final Logger LOGGER = LoggerFactory.getLogger(HarvestProviderJob.class);

  /**
   * The maximum number of report requests a single provider run sends to the provider in parallel
   * at the start.
   *
   * <p>"Concurrency" here means requests in flight at the same time, not threads. They all run
   * asynchronously on the {@link WorkerVerticle}'s context.
   *
   * <p>Not to be confused with {@code org.quartz.threadPool.threadCount}, which limits how many
   * provider runs execute at once per module instance.
   */
  private static final int INITIAL_CONCURRENCY = 4;

  /** The message a retry run fails with when it gives way to a regular run. */
  static final String CANCELLED_MESSAGE = "Cancelled: a regular harvest was requested";

  /**
   * Runs one harvest for one provider and blocks the Quartz thread until it has finished.
   *
   * <p>Blocking is intended: while the job runs, its {@code JobKey(providerId, tenantId)} exists,
   * so no second run of the same provider can start.
   *
   * <p>If the job carries retry items, it is a retry run: it harvests only those of the items that
   * are due, and parks the others again. Items the run parks are handed to the {@link
   * RetryScheduler} when it is over, whether it succeeded or failed.
   *
   * <p>A retry run gives way to a regular run: it checks every {@link
   * SchedulingUtil#CHECK_INTERVAL} whether one was requested, and then cancels itself. It fails
   * with {@link #CANCELLED_MESSAGE} and parks nothing, the regular run that follows covers all its
   * months. Regular runs are never cancelled.
   */
  @Override
  public void execute(final JobExecutionContext context) throws JobExecutionException {
    final var scheduler = context.getScheduler();
    final var vertxContext = getVertxContext(scheduler);
    final String providerId;
    final String tenantId;
    final Vertx vertx;
    final String okapiUrl;
    try {
      providerId = requireNonNull(getProviderId());
      tenantId = requireNonNull(getTenantId());
      okapiUrl = requireNonNull(vertxContext.config().getString("okapiUrl"));
      vertx = requireNonNull(vertxContext.owner());
    } catch (NullPointerException e) {
      throw new JobExecutionException(e);
    }
    final var retryScheduler = new RetryScheduler(scheduler, tenantId, providerId);

    // A non-empty list of retry items means we'll do a retry run, otherwise we'll do a regular run
    final List<ParkedItem> retryItems;
    if (isRetryRun()) {
      if (shouldCancel(() -> isRegularRunRequested(scheduler, tenantId, providerId))) {
        throw new JobExecutionException(CANCELLED_MESSAGE);
      }
      retryItems = retryScheduler.getDueAndParkRest(getRetryItems(), Instant.now());
      if (retryItems.isEmpty()) {
        LOGGER.info(
            "Tenant: {}, Provider: {}, no retry item is due yet, nothing to harvest",
            tenantId,
            providerId);
        return;
      }
    } else {
      retryItems = List.of();
    }

    final var webClient = WebClientProvider.get(vertx);
    final var crClient = new ExtCounterReportsClientImpl(okapiUrl, tenantId, webClient);
    final var providersClient = new ExtUsageDataProvidersClientImpl(okapiUrl, tenantId, webClient);
    final var provider = await(providersClient.getActiveProviderById(providerId));
    final var workerVerticle =
        createWorkerVerticle(crClient, providersClient, tenantId, provider, retryItems);

    final var cancelled = new AtomicBoolean();
    try {
      await(
          vertx.deployVerticle(workerVerticle, new DeploymentOptions().setThreadingModel(WORKER)));
      if (isRetryRun()) {
        awaitOrCancel(
            workerVerticle.getFinished(),
            SchedulingUtil.CHECK_INTERVAL,
            () -> isRegularRunRequested(scheduler, tenantId, providerId),
            () -> {
              cancelled.set(true);
              workerVerticle.cancel(CANCELLED_MESSAGE);
            });
      } else {
        await(workerVerticle.getFinished());
      }
    } finally {
      if (!cancelled.get()) {
        retryScheduler.park(workerVerticle.getParked());
      }
    }
  }

  private static boolean isRegularRunRequested(
      final Scheduler scheduler, final String tenantId, final String providerId)
      throws SchedulerException {
    return scheduler.checkExists(HarvestDeferredStartJob.startJobKey(tenantId, providerId));
  }

  private static Context getVertxContext(final Scheduler scheduler) throws JobExecutionException {
    try {
      return requireNonNull((Context) scheduler.getContext().get("vertxContext"));
    } catch (SchedulerException | ClassCastException | NullPointerException e) {
      throw new JobExecutionException(e);
    }
  }

  /**
   * @param retryItems the due items of a retry run; empty for a regular run
   */
  private static WorkerVerticle createWorkerVerticle(
      final ExtCounterReportsClient counterReportsClient,
      final ExtUsageDataProvidersClient usageDataProvidersClient,
      final String tenantId,
      final UsageDataProvider usageDataProvider,
      final List<ParkedItem> retryItems) {
    return new WorkerVerticle(
        counterReportsClient,
        usageDataProvidersClient,
        tenantId,
        usageDataProvider,
        INITIAL_CONCURRENCY,
        ErrorHandlingLoader.forTenant(tenantId),
        retryItems);
  }

  /** Checks whether a run should be cancelled. */
  @FunctionalInterface
  interface CancelCheck {
    boolean shouldCancel() throws SchedulerException;
  }

  /**
   * Like {@link #await(Future)}, but checks every {@code checkInterval} whether the run should be
   * cancelled, and then calls {@code cancel} once. A failing check is logged and the run goes on.
   *
   * @param finished completes or fails when the run has ended; {@code cancel} must make it end
   */
  static void awaitOrCancel(
      final Future<?> finished,
      final Duration checkInterval,
      final CancelCheck cancelCheck,
      final Runnable cancel)
      throws JobExecutionException {
    final var future = finished.toCompletionStage().toCompletableFuture();
    var cancelRequested = false;
    while (true) {
      try {
        future.get(checkInterval.toMillis(), TimeUnit.MILLISECONDS);
        return;
      } catch (TimeoutException e) {
        if (!cancelRequested && shouldCancel(cancelCheck)) {
          cancelRequested = true;
          cancel.run();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new JobExecutionException(e);
      } catch (ExecutionException | RuntimeException e) {
        throw new JobExecutionException(e);
      }
    }
  }

  private static boolean shouldCancel(final CancelCheck cancelCheck) {
    try {
      return cancelCheck.shouldCancel();
    } catch (SchedulerException | RuntimeException e) {
      LOGGER.warn("Can't check whether to cancel the run, it goes on: {}", e.getMessage());
      return false;
    }
  }

  /** Blocks until the future completes and returns its result; a failure fails the job. */
  private static <T> T await(final Future<T> future) throws JobExecutionException {
    try {
      return future.toCompletionStage().toCompletableFuture().get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new JobExecutionException(e);
    } catch (ExecutionException | RuntimeException e) {
      throw new JobExecutionException(e);
    }
  }
}
