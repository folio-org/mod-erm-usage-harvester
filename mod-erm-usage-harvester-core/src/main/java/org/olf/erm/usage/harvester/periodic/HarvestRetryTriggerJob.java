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
 *
 * <p>While a run of the provider is in progress, it fires again every {@link
 * SchedulingUtil#CHECK_INTERVAL}. That run is usually the one that parked the items: it holds the
 * provider's key until it has parked them, so an item that is already due when it is parked finds
 * the key still taken.
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
   * Starts the retry run and deletes the retry job, unless a run of the provider is in progress;
   * then it does nothing and fires again later. If a regular run is pending, the items are dropped:
   * that run covers their months.
   *
   * @param scheduler the Quartz scheduler
   * @throws SchedulerException if the job store can't be accessed
   */
  void trigger(final Scheduler scheduler) throws SchedulerException {
    final var tenantId = getTenantId();
    final var providerId = getProviderId();
    final var retryJobKey = RetryScheduler.retryJobKey(tenantId, providerId);

    if (scheduler.checkExists(HarvestDeferredStartJob.startJobKey(tenantId, providerId))) {
      scheduler.deleteJob(retryJobKey);
      LOGGER.info(
          "Tenant: {}, Provider: {}, a regular run is pending, dropping the retry items",
          tenantId,
          providerId);
      return;
    }

    if (scheduler.checkExists(new JobKey(providerId, tenantId))) {
      LOGGER.debug(
          "Tenant: {}, Provider: {}, a run is in progress, retrying later", tenantId, providerId);
      return;
    }

    // Delete the retry job first: the retry run parks its items into a new one.
    scheduler.deleteJob(retryJobKey);
    try {
      SchedulingUtil.scheduleProviderJob(scheduler, tenantId, providerId, getRetryItems());
      LOGGER.info("Tenant: {}, Provider: {}, retry run scheduled", tenantId, providerId);
    } catch (SchedulerException e) {
      // A run has started meanwhile: park the items again, so they wait for it
      LOGGER.info(
          "Tenant: {}, Provider: {}, retry run not scheduled, parking the items again: {}",
          tenantId,
          providerId,
          e.getMessage());
      new RetryScheduler(scheduler, tenantId, providerId).park(getRetryItems());
    }
  }
}
