package org.olf.erm.usage.harvester.periodic;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fires when a provider's parked items are due (see {@link RetryScheduler}) and starts a retry run
 * of {@link HarvestProviderJob} with them. It never harvests itself and finishes in milliseconds.
 */
public class HarvestRetryTriggerJob extends AbstractHarvestJob {

  private static final Logger LOGGER = LoggerFactory.getLogger(HarvestRetryTriggerJob.class);

  @Override
  public void execute(final JobExecutionContext context) throws JobExecutionException {
    try {
      trigger(context.getScheduler());
    } catch (SchedulerException e) {
      throw new JobExecutionException(e);
    }
  }

  /**
   * Schedules the retry run. If a run of the provider is already in progress, the items are
   * dropped: that run covers their months.
   *
   * @param scheduler the Quartz scheduler
   * @throws SchedulerException if the retry run can't be scheduled
   */
  void trigger(final Scheduler scheduler) throws SchedulerException {
    if (scheduler.checkExists(new JobKey(getProviderId(), getTenantId()))) {
      LOGGER.info(
          "Tenant: {}, Provider: {}, a run is in progress, dropping the retry items",
          getTenantId(),
          getProviderId());
      return;
    }

    SchedulingUtil.scheduleProviderJob(scheduler, getTenantId(), getProviderId(), getRetryItems());
    LOGGER.info("Tenant: {}, Provider: {}, retry run scheduled", getTenantId(), getProviderId());
  }
}
