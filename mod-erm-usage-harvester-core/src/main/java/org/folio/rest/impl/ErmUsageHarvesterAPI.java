package org.folio.rest.impl;

import com.google.common.base.Strings;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.resource.ErmUsageHarvester;
import org.olf.erm.usage.harvester.WorkerVerticle;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpointProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ErmUsageHarvesterAPI implements ErmUsageHarvester {

  private static final Logger log = LoggerFactory.getLogger(ErmUsageHarvesterAPI.class);
  public static final Error ERR_NO_TOKEN =
      new Error().withType("Error").withMessage("No X-Okapi-Token provided");

  private Future<String> deployWorkerVerticle(
      Context vertxContext, Map<String, String> okapiHeaders, String providerId) {
    return vertxContext
        .owner()
        .deployVerticle(
            new WorkerVerticle(okapiHeaders, providerId),
            new DeploymentOptions().setConfig(vertxContext.config()))
        .onFailure(
            t ->
                log.error(
                    String.format(
                        "Tenant: %s, failed deploying WorkerVerticle: %s",
                        okapiHeaders.get(XOkapiHeaders.TENANT), t.getMessage()),
                    t));
  }

  @Override
  public void getErmUsageHarvesterStart(
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    String tokenStr = okapiHeaders.get(XOkapiHeaders.TOKEN);
    if (tokenStr == null) {
      asyncResultHandler.handle(
          Future.succeededFuture(
              GetErmUsageHarvesterStartResponse.respond500WithApplicationJson(ERR_NO_TOKEN)));
      return;
    }

    String msg =
        String.format(
            "Processing of tenant: %s requested.", okapiHeaders.get(XOkapiHeaders.TENANT));
    log.info(msg);
    deployWorkerVerticle(vertxContext, okapiHeaders, null);
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
          Future.succeededFuture(
              GetErmUsageHarvesterStartByIdResponse.respond500WithApplicationJson(ERR_NO_TOKEN)));
      return;
    }

    String msg =
        String.format(
            "Processing of ProviderId: %s, Tenant: %s requested.",
            id, okapiHeaders.get(XOkapiHeaders.TENANT));
    log.info(msg);
    deployWorkerVerticle(vertxContext, okapiHeaders, id);
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
        Future.succeededFuture(
            GetErmUsageHarvesterImplResponse.respond200WithApplicationJson(result)));
  }
}
