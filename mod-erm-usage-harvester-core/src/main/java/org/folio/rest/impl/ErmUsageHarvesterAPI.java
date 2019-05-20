package org.folio.rest.impl;

import com.google.common.base.Strings;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.HarvesterSetting;
import org.folio.rest.jaxrs.resource.ErmUsageHarvester;
import org.folio.rest.persist.PostgresClient;
import org.olf.erm.usage.harvester.OkapiClient;
import org.olf.erm.usage.harvester.Token;
import org.olf.erm.usage.harvester.WorkerVerticle;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpointProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ErmUsageHarvesterAPI implements ErmUsageHarvester {

  private static final String SETTINGS_TABLE = "harvester_settings";
  private static final String SETTINGS_ID = "8bf5fe33-5ec8-420c-a86d-6320c55ba554";
  private static final Logger LOG = LoggerFactory.getLogger(ErmUsageHarvesterAPI.class);
  public static final Error ERR_NO_TOKEN =
      new Error().withType("Error").withMessage("No Okapi Token provided");

  public void depoyWorkerVerticle(Vertx vertx, Token token, String providerId) {
    new OkapiClient(vertx, vertx.getOrCreateContext().config())
        .hasHarvesterInterface(token.getTenantId())
        .compose(
            v -> {
              Future<String> deploy = Future.future();
              WorkerVerticle verticle =
                  (Strings.isNullOrEmpty(providerId))
                      ? new WorkerVerticle(token)
                      : new WorkerVerticle(token, providerId);
              vertx.deployVerticle(
                  verticle,
                  new DeploymentOptions().setConfig(vertx.getOrCreateContext().config()),
                  deploy.completer());
              return deploy;
            })
        .setHandler(
            ar -> {
              if (ar.failed()) {
                LOG.error(
                    String.format(
                        "Tenant: %s, failed deploying WorkerVerticle: %s",
                        token.getTenantId(), ar.cause().getMessage()),
                    ar.cause());
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

    String tokenStr = okapiHeaders.get(XOkapiHeaders.TOKEN);
    if (tokenStr == null) {
      asyncResultHandler.handle(
          Future.succeededFuture(Response.serverError().entity(ERR_NO_TOKEN).build()));
      return;
    }

    Token token = new Token(tokenStr);
    String msg = String.format("Processing of tenant: %s requested.", token.getTenantId());
    LOG.info(msg);
    depoyWorkerVerticle(vertxContext.owner(), token, null);
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

    String tokenStr = okapiHeaders.get(XOkapiHeaders.TOKEN);
    if (tokenStr == null) {
      asyncResultHandler.handle(
          Future.succeededFuture(Response.serverError().entity(ERR_NO_TOKEN).build()));
      return;
    }

    Token token = new Token(tokenStr);
    String msg =
        String.format(
            "Processing of ProviderId: %s, Tenant: %s requested.", id, token.getTenantId());
    LOG.info(msg);
    depoyWorkerVerticle(vertxContext.owner(), token, id);
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
        ServiceEndpoint.getAvailableProviders().stream()
            .filter(
                provider ->
                    Strings.isNullOrEmpty(aggregator)
                        || provider.isAggregator().equals(Boolean.valueOf(aggregator)))
            .sorted(Comparator.comparing(ServiceEndpointProvider::getServiceName))
            .map(ServiceEndpointProvider::toJson)
            .collect(Collectors.toList());
    String result = new JsonObject().put("implementations", new JsonArray(collect)).toString();
    asyncResultHandler.handle(
        Future.succeededFuture(Response.ok(result, MediaType.APPLICATION_JSON_TYPE).build()));
  }
}
