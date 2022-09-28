package org.olf.erm.usage.harvester.periodic;

import static org.folio.rest.jaxrs.model.JobInfo.Type.PERIODIC;

import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.folio.rest.jaxrs.model.JobInfo;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.listeners.SchedulerListenerSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobInfoSchedulerListener extends SchedulerListenerSupport {

  private static final Logger log = LoggerFactory.getLogger(JobInfoSchedulerListener.class);

  @Override
  public void jobAdded(JobDetail jobDetail) {
    try {
      Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
      scheduler.pauseJob(jobDetail.getKey());
    } catch (SchedulerException e) {
      log.warn(e.getMessage(), e);
    }
  }

  @Override
  public void jobScheduled(Trigger trigger) {
    Scheduler scheduler;
    JobDetail jobDetail;
    try {
      scheduler = StdSchedulerFactory.getDefaultScheduler();
      jobDetail = scheduler.getJobDetail(trigger.getJobKey());
    } catch (SchedulerException e) {
      log.error(e.getMessage(), e);
      return;
    }

    String name = trigger.getJobKey().getName();
    String tenantId = trigger.getJobKey().getGroup();

    // if we are scheduling a new periodic, then an existing one needs to be removed from db
    Future<RowSet<Row>> deleteFuture = Future.succeededFuture();
    if (PERIODIC.value().equals(name)) {
      deleteFuture = JobInfoUtil.deletePeriodicJobInfo(tenantId);
    }

    deleteFuture
        .compose(
            rs -> {
              JobInfo jobInfo = JobInfoUtil.createJobInfo(jobDetail);
              return JobInfoUtil.upsertJobInfo(
                      jobInfo
                          .withStartedAt(trigger.getPreviousFireTime())
                          .withNextStart(trigger.getNextFireTime()),
                      tenantId)
                  .onFailure(t -> log.warn("Error updating JobInfo", t));
            })
        .onComplete(
            ar -> {
              try {
                scheduler.resumeTrigger(trigger.getKey());
              } catch (SchedulerException e) {
                log.error(e.getMessage(), e);
              }
            });
  }

  @Override
  public void jobUnscheduled(TriggerKey triggerKey) {
    if (PERIODIC.value().equals(triggerKey.getName())) {
      JobInfoUtil.deletePeriodicJobInfo(triggerKey.getGroup())
          .onFailure(t -> log.warn("Error deleting JobInfo", t));
    }
  }
}
