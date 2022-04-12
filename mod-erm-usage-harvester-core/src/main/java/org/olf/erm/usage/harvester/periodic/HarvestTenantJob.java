package org.olf.erm.usage.harvester.periodic;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.web.client.WebClient;
import java.util.Date;
import org.olf.erm.usage.harvester.client.OkapiClient;
import org.olf.erm.usage.harvester.SystemUser;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HarvestTenantJob implements Job {

  private static final Logger log = LoggerFactory.getLogger(HarvestTenantJob.class);
  private String tenantId;

  private Future<String> updateLastTriggeredAt(Context vertxContext, Date fireTime) {
    return PeriodicConfigPgUtil.get(vertxContext, tenantId)
        .compose(
            pc ->
                PeriodicConfigPgUtil.upsert(
                    vertxContext, tenantId, pc.withLastTriggeredAt(fireTime)));
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
              "Tenant: %s, error getting scheduler context: %s", tenantId, e.getMessage()));
      return;
    }

    if (vertxContext == null) {
      failAndLog(promise, String.format("Tenant: %s, error getting vert.x context", tenantId));
      return;
    }

    WebClient webClient = WebClient.create(vertxContext.owner());
    OkapiClient okapiClient = new OkapiClient(webClient, vertxContext.config());

    okapiClient
        .loginSystemUser(tenantId, new SystemUser(tenantId))
        .compose(token -> okapiClient.startHarvester(tenantId, token))
        .onSuccess(
            resp -> {
              if (resp.statusCode() != 200) {
                failAndLog(
                    promise,
                    String.format(
                        "Tenant: %s, error starting job, received %s %s from start interface: %s",
                        tenantId, resp.statusCode(), resp.statusMessage(), resp.bodyAsString()));
              } else {
                log.info("Tenant: {}, job started", tenantId);
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
                                    tenantId, ar2.cause().getMessage()));
                          }
                        });
              }
            })
        .onFailure(
            t ->
                failAndLog(
                    promise,
                    String.format(
                        "Tenant: %s, error starting harvester: %s", tenantId, t.getMessage())));
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }
}
