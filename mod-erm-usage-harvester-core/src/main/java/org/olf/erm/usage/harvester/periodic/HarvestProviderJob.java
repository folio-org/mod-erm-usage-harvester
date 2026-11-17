package org.olf.erm.usage.harvester.periodic;

import static io.vertx.core.ThreadingModel.WORKER;
import static java.util.Objects.requireNonNull;

import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.ext.web.client.WebClient;
import java.util.concurrent.CompletableFuture;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.WebClientProvider;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClient;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClientImpl;
import org.olf.erm.usage.harvester.client.ExtUsageDataProvidersClient;
import org.olf.erm.usage.harvester.client.ExtUsageDataProvidersClientImpl;
import org.olf.erm.usage.harvester.client.ServiceEndpointFactory;
import org.olf.erm.usage.harvester.worker.WorkerVerticle;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;

public class HarvestProviderJob extends AbstractHarvestJob {

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

  /**
   * Runs one harvest for one provider and blocks the Quartz thread until it has finished.
   *
   * <p>Blocking is intended: while the job runs, its {@code JobKey(providerId, tenantId)} exists,
   * so no second run of the same provider can start.
   */
  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    String providerId;
    String tenantId;
    Context vertxContext;
    String okapiUrl;
    try {
      providerId = requireNonNull(getProviderId());
      tenantId = requireNonNull(getTenantId());
      vertxContext =
          requireNonNull((Context) context.getScheduler().getContext().get("vertxContext"));
      okapiUrl = requireNonNull(vertxContext.config().getString("okapiUrl"));
    } catch (SchedulerException | ClassCastException | NullPointerException e) {
      throw new JobExecutionException(e);
    }

    WebClient webClient = WebClientProvider.get(vertxContext.owner());
    ExtCounterReportsClient counterReportsClient =
        new ExtCounterReportsClientImpl(okapiUrl, tenantId, webClient);
    ExtUsageDataProvidersClient usageDataProvidersClient =
        new ExtUsageDataProvidersClientImpl(okapiUrl, tenantId, webClient);

    try {
      UsageDataProvider usageDataProvider =
          usageDataProvidersClient
              .getActiveProviderById(providerId)
              .toCompletionStage()
              .toCompletableFuture()
              .get();

      WorkerVerticle workerVerticle =
          new WorkerVerticle(
              counterReportsClient,
              usageDataProvidersClient,
              tenantId,
              usageDataProvider,
              ServiceEndpointFactory::createServiceEndpoint,
              INITIAL_CONCURRENCY);

      CompletableFuture<String> cfDeploy =
          vertxContext
              .owner()
              .deployVerticle(workerVerticle, new DeploymentOptions().setThreadingModel(WORKER))
              .toCompletionStage()
              .toCompletableFuture();

      // Waits until the Verticle is deployed
      cfDeploy.get();
      // Waits until the Verticle is finished
      workerVerticle.getFinished().toCompletionStage().toCompletableFuture().get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new JobExecutionException(e);
    } catch (Exception e) {
      throw new JobExecutionException(e);
    }
  }
}
