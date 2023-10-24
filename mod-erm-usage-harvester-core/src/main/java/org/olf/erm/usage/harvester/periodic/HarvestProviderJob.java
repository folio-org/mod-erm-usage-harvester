package org.olf.erm.usage.harvester.periodic;

import static java.util.Objects.requireNonNull;

import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.ext.web.client.WebClient;
import java.util.concurrent.CompletableFuture;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.WorkerVerticle;
import org.olf.erm.usage.harvester.client.ExtAggregatorSettingsClient;
import org.olf.erm.usage.harvester.client.ExtAggregatorSettingsClientImpl;
import org.olf.erm.usage.harvester.client.ExtConfigurationsClient;
import org.olf.erm.usage.harvester.client.ExtConfigurationsClientImpl;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClient;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClientImpl;
import org.olf.erm.usage.harvester.client.ExtUsageDataProvidersClient;
import org.olf.erm.usage.harvester.client.ExtUsageDataProvidersClientImpl;
import org.olf.erm.usage.harvester.client.ServiceEndpointFactory;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;

public class HarvestProviderJob extends AbstractHarvestJob {

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    String providerId;
    String tenantId;
    String token;
    Context vertxContext;
    String okapiUrl;
    try {
      providerId = requireNonNull(getProviderId());
      tenantId = requireNonNull(getTenantId());
      token = requireNonNull(getToken());
      vertxContext =
          requireNonNull((Context) context.getScheduler().getContext().get("vertxContext"));
      okapiUrl = requireNonNull(vertxContext.config().getString("okapiUrl"));
    } catch (SchedulerException | ClassCastException | NullPointerException e) {
      throw new JobExecutionException(e);
    }

    WebClient webClient = WebClient.create(vertxContext.owner());
    ExtConfigurationsClient configurationsClient =
        new ExtConfigurationsClientImpl(okapiUrl, tenantId, token, webClient);
    ExtAggregatorSettingsClient aggregatorSettingsClient =
        new ExtAggregatorSettingsClientImpl(okapiUrl, tenantId, token, webClient);
    ExtCounterReportsClient counterReportsClient =
        new ExtCounterReportsClientImpl(okapiUrl, tenantId, token, webClient);
    ExtUsageDataProvidersClient usageDataProvidersClient =
        new ExtUsageDataProvidersClientImpl(okapiUrl, tenantId, token, webClient);
    int initialConcurrency = 4;

    try {
      UsageDataProvider usageDataProvider =
          usageDataProvidersClient
              .getActiveProviderById(providerId)
              .toCompletionStage()
              .toCompletableFuture()
              .get();

      ServiceEndpoint serviceEndpoint =
          new ServiceEndpointFactory(aggregatorSettingsClient)
              .createServiceEndpoint(usageDataProvider)
              .toCompletionStage()
              .toCompletableFuture()
              .get();

      WorkerVerticle workerVerticle =
          new WorkerVerticle(
              configurationsClient,
              counterReportsClient,
              usageDataProvidersClient,
              tenantId,
              usageDataProvider,
              serviceEndpoint,
              initialConcurrency);
      CompletableFuture<String> cfDeploy =
          vertxContext
              .owner()
              .deployVerticle(workerVerticle, new DeploymentOptions().setWorker(true))
              .toCompletionStage()
              .toCompletableFuture();

      cfDeploy.get();
      workerVerticle.getFinished().toCompletionStage().toCompletableFuture().get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new JobExecutionException(e);
    } catch (Exception e) {
      throw new JobExecutionException(e);
    }
  }
}
