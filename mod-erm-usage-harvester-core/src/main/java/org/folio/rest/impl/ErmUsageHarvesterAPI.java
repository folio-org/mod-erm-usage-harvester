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
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.resource.ErmUsageHarvester;
import org.olf.erm.usage.harvester.Token;
import org.olf.erm.usage.harvester.WorkerVerticle;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpointProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ErmUsageHarvesterAPI implements ErmUsageHarvester {

  private static final Logger log = LoggerFactory.getLogger(ErmUsageHarvesterAPI.class);
  public static final Error ERR_NO_TOKEN =
      new Error().withType("Error").withMessage("No Okapi Token provided");

  public void deployWorkerVerticle(Vertx vertx, Token token, String providerId) {
    Future<String> deploy = Future.future();
    WorkerVerticle verticle =
        (Strings.isNullOrEmpty(providerId))
            ? new WorkerVerticle(token)
            : new WorkerVerticle(token, providerId);
    vertx.deployVerticle(
        verticle,
        new DeploymentOptions().setConfig(vertx.getOrCreateContext().config()),
        deploy.completer());

    deploy.setHandler(
        ar -> {
          if (ar.failed()) {
            log.error(
                String.format(
                    "Tenant: %s, failed deploying WorkerVerticle: %s",
                    token.getTenantId(), ar.cause().getMessage()),
                ar.cause());
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
    log.info(msg);
    deployWorkerVerticle(vertxContext.owner(), token, null);
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
    log.info(msg);
    deployWorkerVerticle(vertxContext.owner(), token, id);
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
