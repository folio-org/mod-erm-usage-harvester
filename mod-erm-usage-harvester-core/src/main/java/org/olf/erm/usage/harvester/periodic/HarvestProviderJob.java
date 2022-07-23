package org.olf.erm.usage.harvester.periodic;

import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.folio.okapi.common.XOkapiHeaders;
import org.olf.erm.usage.harvester.WorkerVerticle;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;

public class HarvestProviderJob extends AbstractHarvestJob {

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    Context vertxContext;
    try {
      Objects.requireNonNull(getProviderId());
      Objects.requireNonNull(getTenantId());
      Objects.requireNonNull(getToken());
      vertxContext = (Context) context.getScheduler().getContext().get("vertxContext");
    } catch (SchedulerException | ClassCastException | NullPointerException e) {
      throw new JobExecutionException(e);
    }

    WorkerVerticle workerVerticle =
        new WorkerVerticle(
            Map.of(XOkapiHeaders.TENANT, getTenantId(), XOkapiHeaders.TOKEN, getToken()),
            getProviderId());
    DeploymentOptions options =
        new DeploymentOptions().setConfig(vertxContext.config()).setWorker(true);
    CompletableFuture<String> cfDeploy =
        vertxContext
            .owner()
            .deployVerticle(workerVerticle, options)
            .toCompletionStage()
            .toCompletableFuture();

    try {
      cfDeploy.get();
      workerVerticle.getFinished().toCompletionStage().toCompletableFuture().get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new JobExecutionException(e);
    } catch (ExecutionException e) {
      throw new JobExecutionException(e);
    }
  }
}
