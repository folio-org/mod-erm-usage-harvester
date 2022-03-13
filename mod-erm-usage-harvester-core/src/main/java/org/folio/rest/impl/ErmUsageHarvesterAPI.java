package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

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
import javax.ws.rs.core.Response;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.resource.ErmUsageHarvester;
import org.olf.erm.usage.harvester.WorkerVerticle;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpointProvider;

public class ErmUsageHarvesterAPI implements ErmUsageHarvester {

  private Future<String> deployWorkerVerticle(
      Context vertxContext, Map<String, String> okapiHeaders, String providerId) {
    return vertxContext
        .owner()
        .deployVerticle(
            new WorkerVerticle(okapiHeaders, providerId),
            new DeploymentOptions().setConfig(vertxContext.config()));
  }

  private String createResponseEntity(Map<String, String> okapiHeaders) {
    return this.createResponseEntity(okapiHeaders, null);
  }

  private String createResponseEntity(Map<String, String> okapiHeaders, String providerId) {
    String message =
        String.format("Harvesting started for tenant: %s", okapiHeaders.get(XOkapiHeaders.TENANT));
    if (providerId != null) message += ", providerId: " + providerId;
    return new JsonObject().put("message", message).toString();
  }

  @Override
  public void getErmUsageHarvesterStart(
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    deployWorkerVerticle(vertxContext, okapiHeaders, null)
        .<Response>map(
            s ->
                GetErmUsageHarvesterStartResponse.respond200WithApplicationJson(
                    createResponseEntity(okapiHeaders)))
        .otherwise(t -> GetErmUsageHarvesterStartResponse.respond500WithTextPlain(t.getMessage()))
        .onComplete(asyncResultHandler);
  }

  @Override
  public void getErmUsageHarvesterStartById(
      String id,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    deployWorkerVerticle(vertxContext, okapiHeaders, id)
        .<Response>map(
            s ->
                GetErmUsageHarvesterStartByIdResponse.respond200WithApplicationJson(
                    createResponseEntity(okapiHeaders, id)))
        .otherwise(
            t -> GetErmUsageHarvesterStartByIdResponse.respond500WithTextPlain(t.getMessage()))
        .onComplete(asyncResultHandler);
  }

  @Override
  public void getErmUsageHarvesterImpl(
      String aggregator,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    try {
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
          succeededFuture(GetErmUsageHarvesterImplResponse.respond200WithApplicationJson(result)));
    } catch (Exception e) {
      asyncResultHandler.handle(
          succeededFuture(
              GetErmUsageHarvesterImplResponse.respond500WithTextPlain(e.getMessage())));
    }
  }
}
