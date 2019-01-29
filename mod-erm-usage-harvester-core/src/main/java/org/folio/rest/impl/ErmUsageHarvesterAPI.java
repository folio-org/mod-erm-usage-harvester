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
import org.olf.erm.usage.harvester.HarvesterVerticle;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpointProvider;
import com.google.common.base.Strings;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ErmUsageHarvesterAPI implements ErmUsageHarvester {

  private static final Logger LOG = Logger.getLogger(ErmUsageHarvesterAPI.class);
  private static final HarvesterVerticle harvester = new HarvesterVerticle();

  @Override
  public void getErmUsageHarvesterSettings(
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PostgresClient.getInstance(vertxContext.owner(), okapiHeaders.get(XOkapiHeaders.TENANT))
        .get(
            "harvester_settings",
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
    ;
  }

  @Override
  public void postErmUsageHarvesterSettings(
      HarvesterSetting entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PostgresClient.getInstance(vertxContext.owner(), okapiHeaders.get(XOkapiHeaders.TENANT))
        .save(
            "harvester_settings",
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
    // harvester.processSingleTenant(tenantId);
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
    // harvester.processSingleProvider(tenantId, providerId);
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
