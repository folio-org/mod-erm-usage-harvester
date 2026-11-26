package org.olf.erm.usage.harvester.periodic;

import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_PROVIDER_ID;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_TENANT;
import static org.quartz.impl.matchers.GroupMatcher.jobGroupEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobBuilder;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.TriggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A regular run that was requested while a retry run of the same provider was in progress. Its key
 * {@code start::<providerId>} tells the retry run to cancel itself (see {@link
 * HarvestProviderJob}). The job checks every {@link SchedulingUtil#CHECK_INTERVAL} whether the
 * provider's run has ended, and then starts the regular run. It gives up after {@link
 * #GIVE_UP_AFTER}, in case the cancellation never takes effect.
 *
 * <p>Polling instead of interrupting: Quartz's {@code interrupt} only reaches jobs on the same
 * module instance, but the key lives in the shared job store, so every instance sees it.
 */
@DisallowConcurrentExecution
public class HarvestDeferredStartJob extends AbstractHarvestJob {

  private static final Logger LOGGER = LoggerFactory.getLogger(HarvestDeferredStartJob.class);

  /** How long the job waits for the retry run to end before it gives up. */
  static final Duration GIVE_UP_AFTER = Duration.ofMinutes(10);

  static final String DATAKEY_REQUESTED_AT = "requestedAt";

  private static final String START_KEY_PREFIX = "start::";

  private long requestedAt;

  /**
   * @return the key of the provider's deferred start
   */
  static JobKey startJobKey(final String tenantId, final String providerId) {
    return new JobKey(START_KEY_PREFIX + providerId, tenantId);
  }

  /**
   * Creates the provider's deferred start. Its first check is one {@link
   * SchedulingUtil#CHECK_INTERVAL} from now, when the retry run has had the chance to see it.
   *
   * @throws SchedulerException if the job can't be stored, e.g. because it exists already
   */
  static void schedule(final Scheduler scheduler, final String tenantId, final String providerId)
      throws SchedulerException {
    final var jobKey = startJobKey(tenantId, providerId);
    final var now = Instant.now();
    final var jobDetail =
        JobBuilder.newJob(HarvestDeferredStartJob.class)
            .withIdentity(jobKey)
            .usingJobData(DATAKEY_TENANT, tenantId)
            .usingJobData(DATAKEY_PROVIDER_ID, providerId)
            .usingJobData(DATAKEY_REQUESTED_AT, now.toEpochMilli())
            .build();
    final var trigger =
        TriggerBuilder.newTrigger()
            .withIdentity(jobKey.getName(), jobKey.getGroup())
            .forJob(jobKey)
            .startAt(Date.from(now.plus(SchedulingUtil.CHECK_INTERVAL)))
            .withSchedule(
                SimpleScheduleBuilder.simpleSchedule()
                    .withIntervalInMilliseconds(SchedulingUtil.CHECK_INTERVAL.toMillis())
                    .repeatForever()
                    .withMisfireHandlingInstructionNextWithRemainingCount())
            .build();
    scheduler.scheduleJob(jobDetail, trigger);
    LOGGER.info(
        "Tenant: {}, Provider: {}, regular run requested, cancelling the retry run in progress",
        tenantId,
        providerId);
  }

  /**
   * Deletes all deferred starts of the tenant, e.g. when the tenant is disabled.
   *
   * @throws SchedulerException if the jobs can't be read or deleted
   */
  static void deleteDeferredStarts(final Scheduler scheduler, final String tenantId)
      throws SchedulerException {
    final List<JobKey> keys =
        scheduler.getJobKeys(jobGroupEquals(tenantId)).stream()
            .filter(jobKey -> jobKey.getName().startsWith(START_KEY_PREFIX))
            .toList();
    if (!keys.isEmpty()) {
      scheduler.deleteJobs(keys);
      LOGGER.info("Tenant: {}, deleted {} deferred start(s)", tenantId, keys.size());
    }
  }

  public void setRequestedAt(final long requestedAt) {
    this.requestedAt = requestedAt;
  }

  @Override
  public void execute(final JobExecutionContext context) throws JobExecutionException {
    try {
      tryStart(context.getScheduler(), Instant.now());
    } catch (SchedulerException e) {
      throw new JobExecutionException(e);
    }
  }

  /**
   * Starts the regular run if the provider is free, or gives up if it waited too long. Otherwise it
   * does nothing and checks again on the next fire.
   *
   * @param scheduler the Quartz scheduler
   * @param now the current time
   * @throws SchedulerException if the job store can't be accessed
   */
  void tryStart(final Scheduler scheduler, final Instant now) throws SchedulerException {
    final var tenantId = getTenantId();
    final var providerId = getProviderId();
    final var startJobKey = startJobKey(tenantId, providerId);

    if (!scheduler.checkExists(new JobKey(providerId, tenantId))) {
      // Delete this job first, otherwise the regular start rejects the run because of its key.
      // The regular start applies all rules again, e.g. if a new retry run has started meanwhile.
      scheduler.deleteJob(startJobKey);
      try {
        SchedulingUtil.scheduleProviderJob(scheduler, tenantId, providerId);
        LOGGER.info(
            "Tenant: {}, Provider: {}, deferred regular run scheduled", tenantId, providerId);
      } catch (SchedulerException e) {
        LOGGER.info(
            "Tenant: {}, Provider: {}, deferred regular run not scheduled: {}",
            tenantId,
            providerId,
            e.getMessage());
      }
      return;
    }

    if (now.isAfter(Instant.ofEpochMilli(requestedAt).plus(GIVE_UP_AFTER))) {
      scheduler.deleteJob(startJobKey);
      LOGGER.error(
          "Tenant: {}, Provider: {}, the run in progress didn't end within {}, the requested"
              + " regular run is dropped",
          tenantId,
          providerId,
          GIVE_UP_AFTER);
    }
  }
}
