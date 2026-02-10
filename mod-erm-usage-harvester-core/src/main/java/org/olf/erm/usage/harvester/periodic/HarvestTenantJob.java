package org.olf.erm.usage.harvester.periodic;

import io.vertx.core.Context;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.folio.rest.jaxrs.model.UsageDataProviders;
import org.olf.erm.usage.harvester.WebClientProvider;
import org.olf.erm.usage.harvester.client.ExtUsageDataProvidersClientImpl;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;

public class HarvestTenantJob extends AbstractHarvestJob {

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    Context vertxContext;
    try {
      vertxContext = (Context) context.getScheduler().getContext().get("vertxContext");
    } catch (SchedulerException e) {
      throw new JobExecutionException(e);
    }

    CompletableFuture<List<String>> complete =
        new ExtUsageDataProvidersClientImpl(
                vertxContext.config().getString("okapiUrl"),
                getTenantId(),
                getToken(),
                WebClientProvider.get(vertxContext.owner()))
            .getActiveProviders()
            .map(UsageDataProviders::getUsageDataProviders)
            .map(
                udps ->
                    udps.stream()
                        .map(
                            udp -> {
                              try {
                                SchedulingUtil.scheduleProviderJob(
                                    context.getScheduler(), getTenantId(), getToken(), udp.getId());
                              } catch (SchedulerException e) {
                                return udp.getId();
                              }
                              return null;
                            })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()))
            .toCompletionStage()
            .toCompletableFuture();
    try {
      context.setResult(complete.get());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new JobExecutionException(e);
    } catch (ExecutionException e) {
      throw new JobExecutionException(e);
    }
  }
}
