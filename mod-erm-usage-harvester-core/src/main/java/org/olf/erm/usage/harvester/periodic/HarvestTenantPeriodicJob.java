package org.olf.erm.usage.harvester.periodic;

import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HarvestTenantPeriodicJob extends AbstractHarvestJob {

  private static final Logger log = LoggerFactory.getLogger(HarvestTenantPeriodicJob.class);

  private Future<String> updateLastTriggeredAt(Context vertxContext, Date fireTime) {
    return PeriodicConfigPgUtil.get(vertxContext, getTenantId())
        .compose(
            pc ->
                PeriodicConfigPgUtil.upsert(
                    vertxContext, getTenantId(), pc.withLastTriggeredAt(fireTime)));
  }

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    Context vertxContext;
    try {
      Object o = context.getScheduler().getContext().get("vertxContext");
      vertxContext = o instanceof Context c ? c : null;
    } catch (SchedulerException e) {
      throw new JobExecutionException(
          String.format(
              "Tenant: %s, error getting scheduler context: %s", getTenantId(), e.getMessage()));
    }

    if (vertxContext == null) {
      throw new JobExecutionException(
          String.format("Tenant: %s, error getting vert.x context", getTenantId()));
    }

    try {
      SchedulingUtil.scheduleTenantJob(context.getScheduler(), getTenantId(), null);
    } catch (SchedulerException e) {
      throw new JobExecutionException(
          String.format("Tenant: %s, error starting harvester: %s", getTenantId(), e.getMessage()));
    }

    try {
      updateLastTriggeredAt(vertxContext, context.getFireTime())
          .onFailure(
              t ->
                  log.error(
                      "Tenant: {}, failed updating lastTriggeredAt: {}",
                      getTenantId(),
                      t.getMessage()))
          .transform(ar -> succeededFuture())
          .toCompletionStage()
          .toCompletableFuture()
          .get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new JobExecutionException(e);
    } catch (ExecutionException e) {
      throw new JobExecutionException(
          String.format(
              "Tenant: %s, error updating lastTriggeredAt: %s", getTenantId(), e.getMessage()));
    }
  }
}
