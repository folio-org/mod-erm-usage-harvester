package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.HarvesterSetting;
import org.folio.rest.jaxrs.resource.ErmUsageHarvester;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;

public class ErmUsageHarvesterAPI implements ErmUsageHarvester {

  private static final Logger LOG = Logger.getLogger(ErmUsageHarvesterAPI.class);

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
      Context vertxContext) { // TODO Auto-generated method stub
  }

  @Override
  public void getErmUsageHarvesterStartById(
      String id,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) { // TODO Auto-generated method stub
  }

  @Override
  public void getErmUsageHarvesterImpl(
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) { // TODO Auto-generated method stub
  }
}
