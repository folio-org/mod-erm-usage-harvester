package org.folio.rest.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.apache.log4j.Logger;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.HarvesterSetting;
import org.folio.rest.jaxrs.resource.ErmUsageHarvester;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.security.AES;
import org.olf.erm.usage.harvester.OkapiClient;
import org.olf.erm.usage.harvester.Token;
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

  private static final String PERM_REQUIRED = "ermusage.all";
  private static final String SETTINGS_TABLE = "harvester_settings";
  private static final String SETTINGS_ID = "8bf5fe33-5ec8-420c-a86d-6320c55ba554";
  private static final Logger LOG = Logger.getLogger(ErmUsageHarvesterAPI.class);

  private static SecretKey secretKey = null;

  public static void setSecretKey(SecretKey secretKey) {
    ErmUsageHarvesterAPI.secretKey = secretKey;
  }

  public Future<Token> getAuthToken(Vertx vertx, String tenantId) {
    JsonObject config = vertx.getOrCreateContext().config();
    OkapiClient okapiClient = new OkapiClient(vertx, config);
    return okapiClient
        .hasEnabledUsageModules(tenantId)
        .compose(
            en -> {
              if (en) {
                return getHarvesterSettingsFromDB(vertx, tenantId);
              } else {
                return Future.failedFuture("Module not enabled for Tenant " + tenantId);
              }
            })
        .compose(
            setting -> {
              if (secretKey != null) {
                try {
                  setting.setPassword(AES.decryptPassword(setting.getPassword(), secretKey));
                } catch (Exception e) {
                  e.printStackTrace();
                  return Future.failedFuture(e);
                }
              }
              return okapiClient.getAuthToken(
                  tenantId, setting.getUsername(), setting.getPassword(), PERM_REQUIRED);
            });
  }

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
                    tenandId ->
                        getAuthToken(vertx, tenandId)
                            .setHandler(
                                h -> {
                                  if (h.succeeded()) {
                                    // deploy WorkerVerticle for each tenant
                                    vertx.deployVerticle(
                                        new WorkerVerticle(h.result()),
                                        new DeploymentOptions().setConfig(config));
                                  } else {
                                    LOG.error(h.cause().getMessage());
                                  }
                                }));
              } else {
                LOG.error("Failed to get tenants: " + ar.cause().getMessage());
              }
            });
  }

  public void processSingleTenant(Vertx vertx, String tenantId) {
    getAuthToken(vertx, tenantId)
        .setHandler(
            h -> {
              if (h.succeeded()) {
                // deploy WorkerVerticle for tenant
                vertx.deployVerticle(
                    new WorkerVerticle(h.result()),
                    new DeploymentOptions().setConfig(vertx.getOrCreateContext().config()));
              } else {
                LOG.error(h.cause().getMessage());
              }
            });
  }

  public void processSingleProvider(Vertx vertx, String tenantId, String providerId) {
    getAuthToken(vertx, tenantId)
        .setHandler(
            h -> {
              if (h.succeeded()) {
                // deploy WorkerVerticle for tenant with providerId
                vertx.deployVerticle(
                    new WorkerVerticle(h.result(), providerId),
                    new DeploymentOptions().setConfig(vertx.getOrCreateContext().config()));
              } else {
                LOG.error(h.cause().getMessage());
              }
            });
  }

  public Future<HarvesterSetting> getHarvesterSettingsFromDB(Vertx vertx, String tenantId) {
    Future<HarvesterSetting> future = Future.future();
    PostgresClient.getInstance(vertx, tenantId)
        .get(
            SETTINGS_TABLE,
            HarvesterSetting.class,
            "*",
            false,
            false,
            ar -> {
              if (ar.succeeded()) {
                if (!ar.result().getResults().isEmpty()) {
                  future.complete(ar.result().getResults().get(0));
                } else {
                  future.fail("No harvester settings found for Tenant: " + tenantId);
                }
              } else {
                future.fail(
                    "Failed to get harvester settings for Tenant: " + tenantId + ", " + ar.cause());
              }
            });
    return future;
  }

  @Override
  public void getErmUsageHarvesterSettings(
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    getHarvesterSettingsFromDB(vertxContext.owner(), okapiHeaders.get(XOkapiHeaders.TENANT))
        .setHandler(
            ar -> {
              if (ar.succeeded()) {
                asyncResultHandler.handle(Future.succeededFuture(Response.ok(ar.result()).build()));
              } else {
                asyncResultHandler.handle(Future.succeededFuture(Response.status(404).build()));
              }
            });
  }

  @Override
  public void postErmUsageHarvesterSettings(
      HarvesterSetting entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    if (secretKey != null) {
      try {
        entity.setPassword(AES.encryptPasswordAsBase64(entity.getPassword(), secretKey));
      } catch (Exception e) {
        asyncResultHandler.handle(Future.succeededFuture(Response.serverError().build()));
        e.printStackTrace();
      }
    }

    PostgresClient.getInstance(vertxContext.owner(), okapiHeaders.get(XOkapiHeaders.TENANT))
        .save(
            SETTINGS_TABLE,
            SETTINGS_ID,
            entity,
            false,
            true,
            ar -> {
              if (ar.succeeded()) {
                LOG.info("succeeded saving setting");
                asyncResultHandler.handle(
                    Future.succeededFuture(
                        ErmUsageHarvester.GetErmUsageHarvesterSettingsResponse
                            .respond200WithApplicationJson(entity)));
              } else {
                LOG.info("failed saving setting", ar.cause());
                asyncResultHandler.handle(Future.succeededFuture(Response.status(500).build()));
              }
            });
  }

  @Override
  public void deleteErmUsageHarvesterSettings(
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PostgresClient.getInstance(vertxContext.owner(), okapiHeaders.get(XOkapiHeaders.TENANT))
        .delete(
            SETTINGS_TABLE,
            SETTINGS_ID,
            ar -> {
              if (ar.succeeded()) {
                if (ar.result().getUpdated() == 1) {
                  asyncResultHandler.handle(Future.succeededFuture(Response.noContent().build()));
                } else {
                  asyncResultHandler.handle(
                      Future.succeededFuture(Response.status(Status.NOT_FOUND).build()));
                }
              } else {
                asyncResultHandler.handle(Future.succeededFuture(Response.serverError().build()));
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
    processSingleTenant(vertxContext.owner(), tenantId);
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
