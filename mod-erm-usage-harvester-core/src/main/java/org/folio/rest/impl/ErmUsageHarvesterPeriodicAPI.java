package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.PeriodicConfig;
import org.folio.rest.jaxrs.resource.ErmUsageHarvesterPeriodic;
import org.olf.erm.usage.harvester.periodic.PeriodicConfigPgUtil;
import org.olf.erm.usage.harvester.periodic.SchedulingUtil;

public class ErmUsageHarvesterPeriodicAPI implements ErmUsageHarvesterPeriodic {

  @Override
  public void getErmUsageHarvesterPeriodic(
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PeriodicConfigPgUtil.get(vertxContext, okapiHeaders)
        .setHandler(
            ar -> {
              if (ar.succeeded()) {
                if (ar.result() == null)
                  asyncResultHandler.handle(Future.succeededFuture(Response.status(404).build()));
                else
                  asyncResultHandler.handle(
                      Future.succeededFuture(Response.ok(ar.result()).build()));
              } else {
                asyncResultHandler.handle(Future.succeededFuture(Response.serverError().build()));
              }
            });
  }

  @Override
  public void postErmUsageHarvesterPeriodic(
      PeriodicConfig entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PeriodicConfigPgUtil.upsert(vertxContext, okapiHeaders, entity)
        .setHandler(
            ar -> {
              if (ar.succeeded()) {
                asyncResultHandler.handle(Future.succeededFuture(Response.status(201).build()));
                SchedulingUtil.createOrUpdateJob(entity, okapiHeaders.get(XOkapiHeaders.TENANT));
              } else {
                asyncResultHandler.handle(Future.succeededFuture(Response.serverError().build()));
              }
            });
  }

  @Override
  public void deleteErmUsageHarvesterPeriodic(
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PeriodicConfigPgUtil.delete(vertxContext, okapiHeaders)
        .setHandler(
            ar -> {
              if (ar.succeeded()) {
                if (ar.result().getUpdated() == 1) {
                  asyncResultHandler.handle(Future.succeededFuture(Response.noContent().build()));
                  SchedulingUtil.deleteJob(okapiHeaders.get(XOkapiHeaders.TENANT));
                } else {
                  asyncResultHandler.handle(Future.succeededFuture(Response.status(404).build()));
                }
              } else {
                asyncResultHandler.handle(Future.succeededFuture(Response.serverError().build()));
              }
            });
  }
}
