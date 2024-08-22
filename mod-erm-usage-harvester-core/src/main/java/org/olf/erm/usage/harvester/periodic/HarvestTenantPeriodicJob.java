package org.olf.erm.usage.harvester.periodic;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.Boolean.parseBoolean;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.ext.web.client.WebClient;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.olf.erm.usage.harvester.SystemUser;
import org.olf.erm.usage.harvester.client.OkapiClient;
import org.olf.erm.usage.harvester.client.OkapiClientImpl;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HarvestTenantPeriodicJob extends AbstractHarvestJob {

  private static final Logger log = LoggerFactory.getLogger(HarvestTenantPeriodicJob.class);

  private Future<String> updateLastTriggeredAt(Context vertxContext, Date fireTime) {
    return PeriodicConfigPgUtil.get(vertxContext, getTenantId())
        .compose(
            pc ->
                PeriodicConfigPgUtil.upsert(
                    vertxContext, getTenantId(), pc.withLastTriggeredAt(fireTime)));
  }

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    Context vertxContext;
    try {
      Object o = context.getScheduler().getContext().get("vertxContext");
      vertxContext = o instanceof Context ? (Context) o : null;
    } catch (SchedulerException e) {
      throw new JobExecutionException(
          String.format(
              "Tenant: %s, error getting scheduler context: %s", getTenantId(), e.getMessage()));
    }

    if (vertxContext == null) {
      throw new JobExecutionException(
          String.format("Tenant: %s, error getting vert.x context", getTenantId()));
    }

    WebClient webClient = WebClient.create(vertxContext.owner());
    OkapiClient okapiClient = new OkapiClientImpl(webClient, vertxContext.config());

    CompletableFuture<Void> complete =
        loginSystemUserIfEnabled(okapiClient, getTenantId())
            .compose(token -> okapiClient.startHarvester(getTenantId(), token))
            .<Void>compose(
                resp -> {
                  if (resp.statusCode() != 200) {
                    return failedFuture(
                        String.format(
                            "Tenant: %s, error starting job, received %s %s from start interface: %s",
                            getTenantId(),
                            resp.statusCode(),
                            resp.statusMessage(),
                            resp.bodyAsString()));
                  } else {
                    return updateLastTriggeredAt(vertxContext, context.getFireTime())
                        .onFailure(
                            t ->
                                log.error(
                                    String.format(
                                        "Tenant: %s, failed updating lastTriggeredAt: %s",
                                        getTenantId(), t.getMessage())))
                        .transform(ar -> succeededFuture());
                  }
                })
            .toCompletionStage()
            .toCompletableFuture();

    try {
      complete.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new JobExecutionException(e);
    } catch (ExecutionException e) {
      throw new JobExecutionException(
          String.format("Tenant: %s, error starting harvester: %s", getTenantId(), e.getMessage()));
    }
  }

  private Future<String> loginSystemUserIfEnabled(OkapiClient okapiClient, String tenantId) {
    return okapiClient.loginSystemUser(tenantId, new SystemUser(tenantId));
  }
}
