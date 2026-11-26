package org.olf.erm.usage.harvester.periodic;

import static io.vertx.core.ThreadingModel.WORKER;
import static java.util.Objects.requireNonNull;

import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.concurrent.ExecutionException;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.WebClientProvider;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClientImpl;
import org.olf.erm.usage.harvester.client.ExtUsageDataProvidersClientImpl;
import org.olf.erm.usage.harvester.errorhandling.ErrorHandlingPgUtil;
import org.olf.erm.usage.harvester.worker.WorkerVerticle;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;

public class HarvestProviderJob extends AbstractHarvestJob {

  private static final int INITIAL_CONCURRENCY = 4;

  @Override
  public void execute(final JobExecutionContext context) throws JobExecutionException {
    final String providerId;
    final String tenantId;
    final Context vertxContext;
    final Vertx vertx;
    final String okapiUrl;
    try {
      providerId = requireNonNull(getProviderId());
      tenantId = requireNonNull(getTenantId());
      vertxContext =
          requireNonNull((Context) context.getScheduler().getContext().get("vertxContext"));
      okapiUrl = requireNonNull(vertxContext.config().getString("okapiUrl"));
      vertx = requireNonNull(vertxContext.owner());
    } catch (SchedulerException | ClassCastException | NullPointerException e) {
      throw new JobExecutionException(e);
    }

    try {
      harvest(vertx, okapiUrl, tenantId, providerId);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new JobExecutionException(e);
    } catch (ExecutionException | RuntimeException e) {
      throw new JobExecutionException(e);
    }
  }

  /**
   * Harvests one provider: fetches it, runs a {@link WorkerVerticle} and waits until the run is
   * over. Blocks the calling (Quartz) thread.
   */
  private static void harvest(
      final Vertx vertx, final String okapiUrl, final String tenantId, final String providerId)
      throws InterruptedException, ExecutionException {
    final var webClient = WebClientProvider.get(vertx);
    final var counterReportsClient = new ExtCounterReportsClientImpl(okapiUrl, tenantId, webClient);
    final var usageDataProvidersClient =
        new ExtUsageDataProvidersClientImpl(okapiUrl, tenantId, webClient);
    final UsageDataProvider usageDataProvider =
        usageDataProvidersClient
            .getActiveProviderById(providerId)
            .toCompletionStage()
            .toCompletableFuture()
            .get();

    // Create the Verticle and deploy it
    final var workerVerticle =
        new WorkerVerticle(
            counterReportsClient,
            usageDataProvidersClient,
            tenantId,
            usageDataProvider,
            INITIAL_CONCURRENCY,
            ctx -> ErrorHandlingPgUtil.get(ctx, tenantId));
    final Future<String> deployedWorkerVerticle =
        vertx.deployVerticle(workerVerticle, new DeploymentOptions().setThreadingModel(WORKER));

    // Block this Quartz thread until the Verticle is deployed and then until the harvest run is
    // finished.
    deployedWorkerVerticle.toCompletionStage().toCompletableFuture().get();
    workerVerticle.getFinished().toCompletionStage().toCompletableFuture().get();
  }
}
