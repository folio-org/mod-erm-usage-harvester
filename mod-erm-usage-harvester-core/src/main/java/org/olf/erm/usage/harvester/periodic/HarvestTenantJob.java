package org.olf.erm.usage.harvester.periodic;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.ext.web.client.WebClient;
import java.util.Date;
import org.folio.okapi.common.XOkapiHeaders;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
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

  @Override
  public void execute(JobExecutionContext context) {
    Context vertxContext;
    try {
      vertxContext = (Context) context.getScheduler().getContext().get("vertxContext");
      if (vertxContext == null) {
        log.error("Tenant: {}, vert.x context is null", tenantId);
        return;
      }
    } catch (Exception e) {
      log.error("Tenant: {}, error getting vert.x context: ", e.getMessage(), e);
      return;
    }

    String okapiUrl = vertxContext.config().getString("okapiUrl");
    WebClient.create(vertxContext.owner())
        .getAbs(okapiUrl + "/erm-usage-harvester/start")
        .putHeader(XOkapiHeaders.TENANT, tenantId)
        .send(
            ar -> {
              if (ar.succeeded()) {
                if (ar.result().statusCode() != 200) {
                  log.error(
                      "Tenant: {}, error starting job, received {} {} from start interface: {}",
                      tenantId,
                      ar.result().statusCode(),
                      ar.result().statusMessage(),
                      ar.result().bodyAsString());
                } else {
                  log.info("Tenant: {}, job started", tenantId);
                }
              } else {
                log.error(
                    "Tenant: {}, error connecting to start interface: {}",
                    tenantId,
                    ar.cause().getMessage(),
                    ar.cause());
              }

              updateLastTriggeredAt(vertxContext, context.getFireTime())
                  .setHandler(
                      ar2 -> {
                        if (ar2.failed()) {
                          log.error(
                              "Tenant: {}, failed updating lastTriggeredAt: {}",
                              tenantId,
                              ar2.cause().getMessage());
                        }
                      });
            });
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }
}
