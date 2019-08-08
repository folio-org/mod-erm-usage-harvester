package org.olf.erm.usage.harvester.periodic;

import io.vertx.core.Context;
import io.vertx.ext.web.client.WebClient;
import org.folio.okapi.common.XOkapiHeaders;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HarvestTenantJob implements Job {

  private static final Logger log = LoggerFactory.getLogger(HarvestTenantJob.class);
  private String tenantId;

  @Override
  public void execute(JobExecutionContext context) {
    Context vertxContext;
    try {
      vertxContext = (Context) context.getScheduler().getContext().get("vertxContext");
    } catch (Exception e) {
      log.error("Tenant: " + tenantId + ", Error getting vert.x context: " + e.getMessage(), e);
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
                      "Tenant: {}, Received {} {}",
                      tenantId,
                      ar.result().statusCode(),
                      ar.result().statusMessage());
                }
                log.info(ar.result().bodyAsString());
              } else {
                log.error(ar.cause().getMessage(), ar.cause());
              }
            });
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }
}
