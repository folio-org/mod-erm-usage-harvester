package org.olf.erm.usage.harvester.periodic;

import static java.util.Objects.requireNonNull;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_JOB_ID;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_PROVIDER_ID;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_RETRY_ITEMS;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_TENANT;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_TIMESTAMP;
import static org.quartz.impl.matchers.GroupMatcher.jobGroupEquals;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.folio.rest.jaxrs.model.PeriodicConfig;
import org.olf.erm.usage.harvester.worker.WorkerVerticle.ParkedItem;
import org.quartz.CronScheduleBuilder;
import org.quartz.DateBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchedulingUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(SchedulingUtil.class);

  public static final String TENANT_JOB_KEY = "tenant";
  public static final String PERIODIC_JOB_KEY = "periodic";

  /**
   * How often a job that waits for a provider checks whether its run has ended, e.g. the retry job.
   */
  static final Duration CHECK_INTERVAL = Duration.ofSeconds(5);

  private static JobDetail createJobDetail(
      Class<? extends Job> jobClass, JobKey jobKey, String providerId) {
    return JobBuilder.newJob(jobClass)
        .withIdentity(jobKey)
        .usingJobData(DATAKEY_TENANT, jobKey.getGroup())
        .usingJobData(DATAKEY_PROVIDER_ID, providerId)
        .usingJobData(DATAKEY_TIMESTAMP, Instant.now().toEpochMilli())
        .usingJobData(DATAKEY_JOB_ID, UUID.randomUUID().toString())
        .build();
  }

  /**
   * Schedules a one-time {@link HarvestProviderJob} that harvests one provider as soon as a Quartz
   * worker thread is free. Returns right after registering the job; it doesn't wait for the run.
   *
   * <p>The job key {@code (providerId, tenantId)} makes sure a provider has at most one run at a
   * time, across all module instances. The job exists until its run has finished, and a second job
   * with the same key is rejected. Runs of different providers execute in parallel, limited by the
   * Quartz thread pool.
   *
   * <p>A regular run takes precedence over retries: the provider's waiting retry job is deleted
   * first, even if the run can't be scheduled because another one is in progress. Its items are
   * covered by the regular run's fetch list (or the one in progress) and aren't counted.
   *
   * @param scheduler the Quartz scheduler
   * @param tenantId the tenant ID, used as the job group
   * @param providerId the ID of the usage data provider, used as the job name
   * @throws SchedulerException if a job for this provider is already scheduled or running, or the
   *     job can't be stored
   */
  public static void scheduleProviderJob(Scheduler scheduler, String tenantId, String providerId)
      throws SchedulerException {
    new RetryScheduler(scheduler, tenantId, providerId).deleteRetryJob();
    scheduleProviderRun(scheduler, tenantId, providerId, null);
  }

  /**
   * Like {@link #scheduleProviderJob(Scheduler, String, String)}, but schedules a retry run: the
   * job starts with the given items instead of the provider's fetch list. Doesn't touch the
   * provider's retry job.
   *
   * @param scheduler the Quartz scheduler
   * @param tenantId the tenant ID, used as the job group
   * @param providerId the ID of the usage data provider, used as the job name
   * @param retryItems the items to retry
   * @throws SchedulerException if a job for this provider is already scheduled or running, or the
   *     job can't be stored
   */
  static void scheduleProviderJob(
      Scheduler scheduler, String tenantId, String providerId, List<ParkedItem> retryItems)
      throws SchedulerException {
    scheduleProviderRun(scheduler, tenantId, providerId, requireNonNull(retryItems));
  }

  /**
   * @param retryItems the items to retry, or {@code null} for a regular run
   */
  private static void scheduleProviderRun(
      Scheduler scheduler, String tenantId, String providerId, List<ParkedItem> retryItems)
      throws SchedulerException {
    JobKey jobKey = new JobKey(providerId, tenantId);
    if (scheduler.checkExists(jobKey)) {
      throw new SchedulerException(
          "A job for provider with id '" + providerId + "' is already scheduled/running");
    } else {
      JobDetail jobDetail = createJobDetail(HarvestProviderJob.class, jobKey, providerId);
      if (retryItems != null) {
        jobDetail.getJobDataMap().put(DATAKEY_RETRY_ITEMS, RetryItemsCodec.encode(retryItems));
      }
      scheduler.scheduleJob(jobDetail, TriggerBuilder.newTrigger().startNow().build());
    }
  }

  /**
   * Schedules a one-time {@link HarvestTenantJob} that harvests all active providers of a tenant.
   * Returns right after registering the job; it doesn't wait for the run.
   *
   * <p>The tenant job itself only fetches the active providers and calls {@link
   * #scheduleProviderJob} for each of them, so the provider runs execute in parallel, limited by
   * the Quartz thread pool.
   *
   * <p>Refused while a tenant job or a regular provider run exists. Retries don't count: waiting
   * retry jobs and running retry runs must not block a regular harvest. A single provider can still
   * be started while a tenant harvest is running; the tenant job then skips that provider.
   *
   * @param scheduler the Quartz scheduler
   * @param tenantId the tenant ID, used as the job group
   * @throws SchedulerException if a harvest for the tenant is already in progress, or the job can't
   *     be stored
   */
  public static void scheduleTenantJob(Scheduler scheduler, String tenantId)
      throws SchedulerException {
    if (isHarvestInProgress(scheduler, tenantId)) {
      throw new SchedulerException(
          "Harvesting for tenant '" + tenantId + "' is already in progress");
    } else {
      JobKey jobKey = new JobKey(TENANT_JOB_KEY, tenantId);
      JobDetail jobDetail = createJobDetail(HarvestTenantJob.class, jobKey, null);
      scheduler.scheduleJob(jobDetail, TriggerBuilder.newTrigger().startNow().build());
    }
  }

  /**
   * @return {@code true} if the tenant has a job other than its periodic job and its retries
   */
  private static boolean isHarvestInProgress(Scheduler scheduler, String tenantId)
      throws SchedulerException {
    for (JobKey jobKey : scheduler.getJobKeys(jobGroupEquals(tenantId))) {
      if (!PERIODIC_JOB_KEY.equals(jobKey.getName())
          && !RetryScheduler.isRetryJobKey(jobKey)
          && !RetryScheduler.isRetryRun(scheduler.getJobDetail(jobKey))) {
        return true;
      }
    }
    return false;
  }

  public static Future<Void> createOrUpdateJobFromDbConfig(Context vertxContext, String tenantId) {
    return PeriodicConfigPgUtil.get(vertxContext, tenantId)
        .<Void>map(
            config -> {
              createOrUpdateJob(config, tenantId);
              return null;
            })
        .onFailure(
            cause ->
                LOGGER.error(
                    "Tenant: {}, failed scheduling job from PeriodicConfig: {}",
                    tenantId,
                    cause.getMessage()));
  }

  public static void createOrUpdateJob(PeriodicConfig config, String tenantId) {
    try {
      createOrUpdateJob(StdSchedulerFactory.getDefaultScheduler(), config, tenantId);
    } catch (SchedulerException e) {
      LOGGER.error("Tenant: {}, unable to get default scheduler: {}", tenantId, e.getMessage());
    }
  }

  public static void createOrUpdateJob(
      Scheduler scheduler, PeriodicConfig config, String tenantId) {
    if (config == null) {
      LOGGER.info("Tenant: {}, No PeriodicConfig present", tenantId);
      return;
    }
    JobKey jobKey = new JobKey(PERIODIC_JOB_KEY, tenantId);
    JobDetail jobDetail = createJobDetail(HarvestTenantPeriodicJob.class, jobKey, null);

    Trigger trigger = createTrigger(tenantId, config);
    if (trigger != null) {
      try {
        if (scheduler.checkExists(jobKey)) {
          scheduler.rescheduleJob(new TriggerKey(PERIODIC_JOB_KEY, tenantId), trigger);
          LOGGER.info(
              "Tenant: {}, Updated job trigger, next trigger: {}",
              tenantId,
              trigger.getNextFireTime());
        } else {
          scheduler.scheduleJob(jobDetail, trigger);
          LOGGER.info(
              "Tenant: {}, Scheduled new job, next trigger: {}",
              tenantId,
              trigger.getNextFireTime());
        }
      } catch (SchedulerException e) {
        LOGGER.error("Tenant: {}, Error scheduling job for tenant, {}", tenantId, e.getMessage());
      }
    } else {
      LOGGER.error("Tenant: {}, Error creating job trigger", tenantId);
    }
  }

  /**
   * Deletes the tenant's periodic job, e.g. when its periodic config is deleted. Pending retries
   * are kept and still run. Never throws, errors are logged.
   */
  public static void deleteJob(String tenantId) {
    try {
      deleteJob(StdSchedulerFactory.getDefaultScheduler(), tenantId);
    } catch (SchedulerException e) {
      LOGGER.error("Tenant: {}, unable to get default scheduler: {}", tenantId, e.getMessage());
    }
  }

  /**
   * Deletes the tenant's periodic job and its retry jobs, e.g. when the tenant is disabled. Runs in
   * progress aren't stopped. Never throws, errors are logged.
   */
  public static void deleteAllJobs(String tenantId) {
    try {
      deleteAllJobs(StdSchedulerFactory.getDefaultScheduler(), tenantId);
    } catch (SchedulerException e) {
      LOGGER.error("Tenant: {}, unable to get default scheduler: {}", tenantId, e.getMessage());
    }
  }

  /**
   * @see #deleteAllJobs(String)
   */
  public static void deleteAllJobs(Scheduler scheduler, String tenantId) {
    deleteJob(scheduler, tenantId);
    try {
      RetryScheduler.deleteRetryJobs(scheduler, tenantId);
    } catch (SchedulerException e) {
      LOGGER.error("Tenant: {}, error deleting retry jobs: {}", tenantId, e.getMessage());
    }
  }

  /**
   * @see #deleteJob(String)
   */
  public static void deleteJob(Scheduler scheduler, String tenantId) {
    JobKey jobKey = new JobKey(PERIODIC_JOB_KEY, tenantId);
    try {
      if (scheduler.checkExists(jobKey)) {
        scheduler.deleteJob(jobKey);
        LOGGER.info("Tenant: {}, removed job from schedule", tenantId);
      } else LOGGER.warn("Tenant: {}, no scheduled job found", tenantId);
    } catch (SchedulerException e) {
      LOGGER.error("Tenant: {}, error deleting job: {}", tenantId, e.getMessage());
    }
  }

  private static Trigger createTrigger(String tenantId, PeriodicConfig config) {
    LocalDateTime start =
        config.getStartAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    CronScheduleBuilder cronScheduleBuilder;
    switch (config.getPeriodicInterval()) {
      case DAILY:
        cronScheduleBuilder =
            CronScheduleBuilder.dailyAtHourAndMinute(start.getHour(), start.getMinute());
        break;
      case WEEKLY:
        cronScheduleBuilder =
            CronScheduleBuilder.cronSchedule(
                String.format(
                    "0 %d %d ? * %s",
                    start.getMinute(),
                    start.getHour(),
                    start.getDayOfWeek().toString().substring(0, 3)));
        break;
      case MONTHLY:
        if (start.getDayOfMonth() > 28) {
          cronScheduleBuilder =
              CronScheduleBuilder.cronSchedule(
                  String.format("0 %d %d L * ?", start.getMinute(), start.getHour()));
        } else {
          cronScheduleBuilder =
              CronScheduleBuilder.monthlyOnDayAndHourAndMinute(
                  start.getDayOfMonth(), start.getHour(), start.getMinute());
        }
        break;
      default:
        return null;
    }

    Date startAt =
        (config.getLastTriggeredAt() != null
                && config.getLastTriggeredAt().compareTo(config.getStartAt()) >= 0)
            ? DateBuilder.nextGivenSecondDate(config.getLastTriggeredAt(), 1)
            : config.getStartAt();

    return TriggerBuilder.newTrigger()
        .startAt(startAt)
        .withIdentity(new TriggerKey(PERIODIC_JOB_KEY, tenantId))
        .withSchedule(cronScheduleBuilder.withMisfireHandlingInstructionFireAndProceed())
        .build();
  }

  private SchedulingUtil() {}
}
