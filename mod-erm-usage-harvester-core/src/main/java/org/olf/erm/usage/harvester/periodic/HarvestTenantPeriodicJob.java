package org.olf.erm.usage.harvester.periodic;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.web.client.WebClient;
import java.util.Date;
import org.olf.erm.usage.harvester.SystemUser;
import org.olf.erm.usage.harvester.client.OkapiClient;
import org.olf.erm.usage.harvester.client.OkapiClientImpl;
import org.quartz.JobExecutionContext;
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

  private void failAndLog(Promise<?> promise, String message) {
    log.error(message);
    promise.fail(message);
  }

  @Override
  public void execute(JobExecutionContext context) {
    Promise<String> promise = Promise.promise();
    context.setResult(promise);

    Context vertxContext;
    try {
      Object o = context.getScheduler().getContext().get("vertxContext");
      vertxContext = o instanceof Context ? (Context) o : null;
    } catch (SchedulerException e) {
      failAndLog(
          promise,
          String.format(
              "Tenant: %s, error getting scheduler context: %s", getTenantId(), e.getMessage()));
      return;
    }

    if (vertxContext == null) {
      failAndLog(promise, String.format("Tenant: %s, error getting vert.x context", getTenantId()));
      return;
    }

    WebClient webClient = WebClient.create(vertxContext.owner());
    OkapiClient okapiClient = new OkapiClientImpl(webClient, vertxContext.config());

    okapiClient
        .loginSystemUser(getTenantId(), new SystemUser(getTenantId()))
        .compose(token -> okapiClient.startHarvester(getTenantId(), token))
        .onSuccess(
            resp -> {
              if (resp.statusCode() != 200) {
                failAndLog(
                    promise,
                    String.format(
                        "Tenant: %s, error starting job, received %s %s from start interface: %s",
                        getTenantId(), resp.statusCode(), resp.statusMessage(), resp.bodyAsString()));
              } else {
                log.info("Tenant: {}, job started", getTenantId());
                updateLastTriggeredAt(vertxContext, context.getFireTime())
                    .onComplete(
                        ar2 -> {
                          if (ar2.succeeded()) {
                            promise.complete();
                          } else {
                            failAndLog(
                                promise,
                                String.format(
                                    "Tenant: %s, failed updating lastTriggeredAt: %s",
                                    getTenantId(), ar2.cause().getMessage()));
                          }
                        });
              }
            })
        .onFailure(
            t ->
                failAndLog(
                    promise,
                    String.format(
                        "Tenant: %s, error starting harvester: %s", getTenantId(), t.getMessage())));
  }
}
