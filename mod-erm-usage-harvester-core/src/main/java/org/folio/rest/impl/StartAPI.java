package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.resource.Start;
import org.olf.erm.usage.harvester.OkapiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StartAPI implements Start {

  private static final Logger LOG = LoggerFactory.getLogger(StartAPI.class);

  public void processAllTenants(Vertx vertx) {
    JsonObject config = vertx.getOrCreateContext().config();
    OkapiClient okapiClient = new OkapiClient(vertx, config);
    okapiClient
        .getTenants()
        .compose(
            tenantList -> {
              tenantList.forEach(
                  tenantId -> {
                    // call /start endpoint for each tenant
                    Future<AsyncResult<HttpResponse<Buffer>>> startTenant = Future.future();
                    String okapiUrl = vertx.getOrCreateContext().config().getString("okapiUrl");
                    WebClient.create(vertx)
                        .getAbs(okapiUrl + "/erm-usage-harvester/start")
                        .putHeader(XOkapiHeaders.TENANT, tenantId)
                        .send(ar -> startTenant.completer());
                    startTenant.setHandler(
                        ar -> {
                          if (ar.failed()) {
                            LOG.error(ar.cause().getMessage(), ar.cause());
                          }
                        });
                  });
              return Future.succeededFuture();
            })
        .setHandler(
            ar -> {
              if (ar.failed()) {
                LOG.error(ar.cause().getMessage(), ar.cause());
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
      processAllTenants(vertxContext.owner());
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
