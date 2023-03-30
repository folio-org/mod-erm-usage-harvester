package org.olf.erm.usage.harvester.periodic;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;
import static org.folio.rest.jaxrs.model.JobInfo.Result.FAILURE;
import static org.folio.rest.jaxrs.model.JobInfo.Result.SUCCESS;
import static org.folio.rest.jaxrs.model.JobInfo.Type.PERIODIC;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_JOB_ID;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_TIMESTAMP;
import static org.olf.erm.usage.harvester.periodic.JobInfoUtil.createJobInfo;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.folio.rest.jaxrs.model.JobInfo;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.listeners.JobListenerSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobInfoJobListener extends JobListenerSupport {

  private static final Logger log = LoggerFactory.getLogger(JobInfoJobListener.class);

  @Override
  public String getName() {
    return this.getClass().getSimpleName();
  }

  @Override
  public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
    JobDetail jobDetail = context.getJobDetail();
    String tenant = jobDetail.getKey().getGroup();

    JobInfo jobInfo =
        createJobInfo(jobDetail)
            .withStartedAt(context.getFireTime())
            .withFinishedAt(
                Date.from(context.getFireTime().toInstant().plusMillis(context.getJobRunTime())));
    if (jobException != null) {
      jobInfo.withResult(FAILURE);
      jobInfo.withErrorMessage(getRootCauseMessage(jobException));
    } else {
      jobInfo.withResult(SUCCESS);
    }
    upsertJobInfo(jobInfo, tenant);
  }

  @Override
  public void jobToBeExecuted(JobExecutionContext context) {
    JobDetail jobDetail = context.getJobDetail();
    String tenant = jobDetail.getKey().getGroup();

    JobInfo jobInfo = createJobInfo(jobDetail);
    if (PERIODIC.equals(jobInfo.getType())) {
      jobDetail.getJobDataMap().put(DATAKEY_JOB_ID, UUID.randomUUID().toString());
      jobDetail.getJobDataMap().put(DATAKEY_TIMESTAMP, Instant.now().toEpochMilli());
      JobInfo newJobInfo = createJobInfo(jobDetail);
      upsertJobInfo(newJobInfo.withStartedAt(context.getFireTime()).withNextStart(null), tenant);
    }
    upsertJobInfo(jobInfo.withStartedAt(null).withNextStart(context.getNextFireTime()), tenant);
  }

  private void upsertJobInfo(JobInfo jobInfo, String tenant) {
    try {
      JobInfoUtil.upsertJobInfo(jobInfo, tenant)
          .onFailure(t -> log.warn("Error saving JobInfo", t))
          .toCompletionStage()
          .toCompletableFuture()
          .get();
    } catch (InterruptedException e) {
      log.warn(e.getMessage(), e);
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      log.warn(e.getMessage(), e);
    }
  }
}
