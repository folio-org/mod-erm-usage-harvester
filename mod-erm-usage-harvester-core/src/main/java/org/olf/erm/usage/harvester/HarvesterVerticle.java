package org.olf.erm.usage.harvester;

import java.util.List;
import org.apache.log4j.Logger;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;

public class HarvesterVerticle extends AbstractVerticle {

  private static final Logger LOG = Logger.getLogger(HarvesterVerticle.class);

  public void processAllTenants() {
    OkapiClient okapiClient = new OkapiClient(vertx, config());
    okapiClient
        .getTenants()
        .setHandler(
            ar -> {
              if (ar.succeeded()) {
                List<String> tenantList = ar.result();
                tenantList.forEach(
                    t -> {
                      okapiClient
                          .hasEnabledUsageModules(t)
                          .compose(
                              en -> {
                                if (en) {
                                  return okapiClient.getAuthToken(
                                      t, "diku_admin", "admin", "ermusage.all");
                                } else {
                                  return Future.failedFuture("Module not enabled for Tenant " + t);
                                }
                              })
                          .setHandler(
                              h -> {
                                if (h.succeeded()) {
                                  // deploy WorkerVerticle for tenant
                                  System.out.println(config().encodePrettily());
                                  vertx.deployVerticle(
                                      new WorkerVerticle(h.result()),
                                      new DeploymentOptions().setConfig(config()));
                                } else {
                                  LOG.error(h.cause().getMessage());
                                }
                              });
                    });
              } else {
                LOG.error("Failed to get tenants: " + ar.cause().getMessage());
              }
            });
  }

  public void processSingleTenant(String tenantId) {
    OkapiClient okapiClient = new OkapiClient(vertx, config());
    okapiClient
        .hasEnabledUsageModules(tenantId)
        .compose(
            en -> {
              if (en) {
                return okapiClient.getAuthToken(tenantId, "diku_admin", "admin", "ermusage.all");
              } else {
                return Future.failedFuture("Module not enabled for Tenant " + tenantId);
              }
            })
        .setHandler(
            h -> {
              if (h.succeeded()) {
                // deploy WorkerVerticle for tenant
                vertx.deployVerticle(
                    new WorkerVerticle(h.result()), new DeploymentOptions().setConfig(config()));
              } else {
                LOG.error(h.cause().getMessage());
              }
            });
  }

  public void processSingleProvider(String tenantId, String providerId) {
    OkapiClient okapiClient = new OkapiClient(vertx, config());
    okapiClient
        .hasEnabledUsageModules(tenantId)
        .compose(
            en -> {
              if (en) {
                return okapiClient.getAuthToken(tenantId, "diku_admin", "admin", "ermusage.all");
              } else {
                return Future.failedFuture("Module not enabled for Tenant " + tenantId);
              }
            })
        .setHandler(
            h -> {
              if (h.succeeded()) {
                // deploy WorkerVerticle for tenant
                vertx.deployVerticle(
                    new WorkerVerticle(h.result(), providerId),
                    new DeploymentOptions().setConfig(config()));
              } else {
                LOG.error(h.cause().getMessage());
              }
            });
  }
}
