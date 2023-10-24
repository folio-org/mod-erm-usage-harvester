package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import java.util.List;
import org.folio.rest.jaxrs.model.PeriodicConfig;
import org.folio.rest.resource.interfaces.PostDeployVerticle;
import org.olf.erm.usage.harvester.client.OkapiClientImpl;
import org.olf.erm.usage.harvester.periodic.HarvestProviderJobListener;
import org.olf.erm.usage.harvester.periodic.JobInfoJobListener;
import org.olf.erm.usage.harvester.periodic.JobInfoSchedulerListener;
import org.olf.erm.usage.harvester.periodic.PeriodicConfigPgUtil;
import org.olf.erm.usage.harvester.periodic.SchedulingUtil;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostDeployImpl implements PostDeployVerticle {

  private static final Logger log = LoggerFactory.getLogger(PostDeployImpl.class);

  private void processTenants(Context vertxContext, List<String> tenantList) {
    tenantList.forEach(
        tenant ->
            PeriodicConfigPgUtil.get(vertxContext, tenant)
                .onComplete(
                    ar -> {
                      if (ar.succeeded()) {
                        PeriodicConfig periodicConfig = ar.result();
                        SchedulingUtil.createOrUpdateJob(periodicConfig, tenant);
                      } else {
                        log.error(
                            "Tenant: {}, failed getting PeriodicConfig: {}",
                            tenant,
                            ar.cause().getMessage());
                      }
                    }));
  }

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> resultHandler) {
    if (Boolean.TRUE.equals(context.config().getBoolean("testing"))) {
      log.info("Skipping PostDeployImpl (testing==true)");
      resultHandler.handle(Future.succeededFuture(true));
      return;
    }

    try {
      Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
      scheduler.getListenerManager().addJobListener(new JobInfoJobListener());
      scheduler.getListenerManager().addJobListener(new HarvestProviderJobListener());
      scheduler.getListenerManager().addSchedulerListener(new JobInfoSchedulerListener());
      scheduler.getContext().put("vertxContext", context);
      scheduler.start();
    } catch (SchedulerException e) {
      log.error("Error setting up quartz scheduler: {}", e.getMessage(), e);
      resultHandler.handle(Future.failedFuture(e.getMessage()));
      return;
    }

    new OkapiClientImpl(WebClient.create(vertx), context.config())
        .getTenants()
        .onComplete(
            ar -> {
              if (ar.succeeded()) {
                log.info("Found tenants: {}", ar.result());
                processTenants(context, ar.result());
              } else {
                log.error("failed getting tenants");
              }
            });

    resultHandler.handle(Future.succeededFuture(true));
  }
}
