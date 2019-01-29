package org.folio.rest.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.log4j.Logger;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.HarvesterSetting;
import org.folio.rest.jaxrs.resource.ErmUsageHarvester;
import org.folio.rest.persist.PostgresClient;
import org.olf.erm.usage.harvester.OkapiClient;
import org.olf.erm.usage.harvester.WorkerVerticle;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpointProvider;
import com.google.common.base.Strings;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ErmUsageHarvesterAPI implements ErmUsageHarvester {

  private static final String SETTINGS_TABLE = "harvester_settings";
  private static final Logger LOG = Logger.getLogger(ErmUsageHarvesterAPI.class);

  public void processAllTenants(Vertx vertx) {
    JsonObject config = vertx.getOrCreateContext().config();
    OkapiClient okapiClient = new OkapiClient(vertx, config);
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
                                  System.out.println(config.encodePrettily());
                                  vertx.deployVerticle(
                                      new WorkerVerticle(h.result()),
                                      new DeploymentOptions().setConfig(config));
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

  public void processSingleTenant(Vertx vertx, String tenantId) {
    JsonObject config = vertx.getOrCreateContext().config();
    OkapiClient okapiClient = new OkapiClient(vertx, config);
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
                    new WorkerVerticle(h.result()), new DeploymentOptions().setConfig(config));
              } else {
                LOG.error(h.cause().getMessage());
              }
            });
  }

  public void processSingleProvider(Vertx vertx, String tenantId, String providerId) {
    JsonObject config = vertx.getOrCreateContext().config();
    OkapiClient okapiClient = new OkapiClient(vertx, config);
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
                    new DeploymentOptions().setConfig(config));
              } else {
                LOG.error(h.cause().getMessage());
              }
            });
  }

  public Future<HarvesterSetting> getHarvesterSetting() {
    return Future.succeededFuture();
  }

  @Override
  public void getErmUsageHarvesterSettings(
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PostgresClient.getInstance(vertxContext.owner(), okapiHeaders.get(XOkapiHeaders.TENANT))
        .get(
            SETTINGS_TABLE,
            HarvesterSetting.class,
            "*",
            false,
            false,
            ar -> {
              if (ar.succeeded()) {
                asyncResultHandler.handle(
                    Future.succeededFuture(Response.ok(ar.result().getResults().get(0)).build()));
              } else {
                asyncResultHandler.handle(Future.succeededFuture(Response.status(400).build()));
              }
            });
  }

  @Override
  public void postErmUsageHarvesterSettings(
      HarvesterSetting entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PostgresClient.getInstance(vertxContext.owner(), okapiHeaders.get(XOkapiHeaders.TENANT))
        .save(
            SETTINGS_TABLE,
            entity,
            ar -> {
              if (ar.succeeded()) {
                LOG.info("succeeded saving setting");
                asyncResultHandler.handle(
                    Future.succeededFuture(
                        ErmUsageHarvester.GetErmUsageHarvesterSettingsResponse
                            .respond200WithApplicationJson(entity)));
              } else {
                LOG.info("failed saving setting", ar.cause());
                asyncResultHandler.handle(Future.succeededFuture(Response.status(400).build()));
              }
            });
  }

  @Override
  public void getErmUsageHarvesterStart(
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    String tenantId = okapiHeaders.get(XOkapiHeaders.TENANT);
    String msg = "Processing of tenant " + tenantId + " requested.";
    LOG.info(msg);
    processSingleTenant(vertxContext.owner(), tenantId); // FIXME
    String result = new JsonObject().put("message", msg).toString();
    asyncResultHandler.handle(
        Future.succeededFuture(Response.ok(result, MediaType.APPLICATION_JSON_TYPE).build()));
  }

  @Override
  public void getErmUsageHarvesterStartById(
      String id,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    String tenantId = okapiHeaders.get(XOkapiHeaders.TENANT);
    String providerId = id;
    String msg =
        "Processing of ProviderId: " + providerId + ", Tenant: " + tenantId + " requested.";
    LOG.info(msg);
    processSingleProvider(vertxContext.owner(), tenantId, providerId);
    String result = new JsonObject().put("message", msg).toString();
    asyncResultHandler.handle(
        Future.succeededFuture(Response.ok(result, MediaType.APPLICATION_JSON_TYPE).build()));
  }

  @Override
  public void getErmUsageHarvesterImpl(
      String aggregator,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    List<JsonObject> collect =
        ServiceEndpoint.getAvailableProviders()
            .stream()
            .filter(
                provider ->
                    Strings.isNullOrEmpty(aggregator)
                        || (aggregator != null
                            && provider.isAggregator().equals(Boolean.valueOf(aggregator))))
            .sorted(Comparator.comparing(ServiceEndpointProvider::getServiceName))
            .map(ServiceEndpointProvider::toJson)
            .collect(Collectors.toList());
    String result = new JsonObject().put("implementations", new JsonArray(collect)).toString();
    asyncResultHandler.handle(
        Future.succeededFuture(Response.ok(result, MediaType.APPLICATION_JSON_TYPE).build()));
  }
}
