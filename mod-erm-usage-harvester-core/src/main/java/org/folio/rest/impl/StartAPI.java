package org.folio.rest.impl;

import java.util.List;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.log4j.Logger;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.resource.Start;
import org.olf.erm.usage.harvester.OkapiClient;
import org.olf.erm.usage.harvester.Token;
import org.olf.erm.usage.harvester.WorkerVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class StartAPI implements Start {

  private static final Logger LOG = Logger.getLogger(StartAPI.class);

  public void processAllTenants(Vertx vertx, Token token) {
    JsonObject config = vertx.getOrCreateContext().config();
    OkapiClient okapiClient = new OkapiClient(vertx, config);
    okapiClient
        .getTenants()
        .setHandler(
            ar -> {
              if (ar.succeeded()) {
                List<String> tenantList = ar.result();
                tenantList.forEach(
                    tenantId ->
                        // deploy WorkerVerticle for each tenant
                        vertx.deployVerticle(
                            new WorkerVerticle(token.withTenantId(tenantId)),
                            new DeploymentOptions().setConfig(config)));
              } else {
                LOG.error("Failed to get tenants: " + ar.cause().getMessage());
              }
            });
  }

  @Override
  public void getStart(
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    String result;
    try {
      String msg = "Processing of all tenants requested.";
      LOG.info(msg);
      processAllTenants(vertxContext.owner(), new Token(okapiHeaders.get(XOkapiHeaders.TOKEN)));
      result = new JsonObject().put("message", msg).toString();
      asyncResultHandler.handle(
          Future.succeededFuture(Response.ok(result, MediaType.APPLICATION_JSON_TYPE).build()));
    } catch (Exception e) {
      asyncResultHandler.handle(
          Future.succeededFuture(Response.serverError().entity(e.getMessage()).build()));
      LOG.error("Error while procesing all tenants", e);
    }
  }
}
