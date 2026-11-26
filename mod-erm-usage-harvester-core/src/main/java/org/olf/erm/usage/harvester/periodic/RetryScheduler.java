package org.olf.erm.usage.harvester.periodic;

import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_PROVIDER_ID;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_RETRY_ITEMS;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_TENANT;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.worker.WorkerVerticle.ParkedItem;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.TriggerBuilder;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps parked items as a Quartz job per provider, the retry job {@code retry::<providerId>}. When
 * it fires, a {@link HarvestRetryTriggerJob} starts a retry run with these items (or waits for the
 * run in progress) and deletes the retry job. The retry job only triggers and never harvests
 * itself, so it holds no Quartz thread while it waits.
 *
 * <p>One instance per provider. It is cheap to create and holds nothing but the scheduler and the
 * provider's IDs; the retry job itself lives in Quartz.
 */
final class RetryScheduler {

  private static final Logger LOGGER = LoggerFactory.getLogger(RetryScheduler.class);

  /**
   * Items due within this window after the earliest one are retried together, in one retry run. An
   * item waits at most this long longer than it has to.
   */
  static final Duration COALESCE_WINDOW = Duration.ofMinutes(15);

  private static final String RETRY_KEY_PREFIX = "retry::";

  /**
   * @return the key of the provider's retry job
   */
  static JobKey retryJobKey(final String tenantId, final String providerId) {
    return new JobKey(RETRY_KEY_PREFIX + providerId, tenantId);
  }

  /**
   * @return {@code true} if the key is the key of a retry job
   */
  static boolean isRetryJobKey(final JobKey jobKey) {
    return jobKey.getName().startsWith(RETRY_KEY_PREFIX);
  }

  /**
   * @return {@code true} if the job is a provider job of a retry run, i.e. it carries retry items
   */
  static boolean isRetryRun(final JobDetail jobDetail) {
    return jobDetail != null && jobDetail.getJobDataMap().containsKey(DATAKEY_RETRY_ITEMS);
  }

  /**
   * Deletes all retry jobs of the tenant, e.g. when the tenant is disabled.
   *
   * @throws SchedulerException if the jobs can't be read or deleted
   */
  static void deleteRetryJobs(final Scheduler scheduler, final String tenantId)
      throws SchedulerException {
    final List<JobKey> retryJobKeys =
        scheduler.getJobKeys(GroupMatcher.jobGroupEquals(tenantId)).stream()
            .filter(RetryScheduler::isRetryJobKey)
            .toList();
    if (!retryJobKeys.isEmpty()) {
      scheduler.deleteJobs(retryJobKeys);
      LOGGER.info("Tenant: {}, deleted {} retry job(s)", tenantId, retryJobKeys.size());
    }
  }

  private final Scheduler scheduler;
  private final String tenantId;
  private final String providerId;
  private final JobKey jobKey;

  /**
   * @param scheduler the Quartz scheduler that keeps the retry job
   * @param tenantId the tenant ID, used as the job group
   * @param providerId the ID of the usage data provider
   */
  RetryScheduler(final Scheduler scheduler, final String tenantId, final String providerId) {
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
    this.providerId = Objects.requireNonNull(providerId, "providerId");
    this.jobKey = retryJobKey(tenantId, providerId);
  }

  /**
   * Adds the items to the provider's retry job, creating it if needed. The job fires at {@link
   * #fireAt(List)} of all its items, which is recomputed on every call.
   *
   * <p>Never throws: a failure is logged, so that it doesn't hide the outcome of the run. Items
   * that can't be parked are lost; the next regular run fetches them.
   */
  void park(final List<ParkedItem> items) {
    if (items.isEmpty()) {
      return;
    }

    try {
      scheduleRetryJob(items);
    } catch (SchedulerException | RuntimeException e) {
      LOGGER.error(
          "Tenant: {}, Provider: {}, failed to park {} item(s)",
          tenantId,
          providerId,
          items.size(),
          e);
    }
  }

  /**
   * Returns the items that are due and parks the others again. Some may not be due yet after
   * merging: a retry run only takes the cluster it was triggered for.
   *
   * @param items the retry items of a retry run
   * @param now the current time; an item due exactly now counts as due
   * @return the due items, in their original order
   */
  List<ParkedItem> getDueAndParkRest(final List<ParkedItem> items, final Instant now) {
    final var partition = partitionDue(items, now);
    park(partition.notDueItems());
    return partition.dueItems();
  }

  /**
   * Deletes the provider's retry job, if there is one, because a regular run was requested. Its
   * items are dropped: the regular run's fetch list covers them, and they aren't counted.
   *
   * @throws SchedulerException if the job can't be read or deleted
   */
  void deleteRetryJob() throws SchedulerException {
    final var retryJob = scheduler.getJobDetail(jobKey);
    if (retryJob == null) {
      return;
    }

    scheduler.deleteJob(jobKey);
    LOGGER.info(
        "Tenant: {}, Provider: {}, regular run requested, dropped {} pending retry item(s)",
        tenantId,
        providerId,
        countItems(retryJob));
  }

  /**
   * @return the number of items of the retry job, or "?" if they can't be decoded
   */
  private static String countItems(final JobDetail retryJob) {
    try {
      return String.valueOf(
          RetryItemsCodec.decode(retryJob.getJobDataMap().getString(DATAKEY_RETRY_ITEMS)).size());
    } catch (IllegalArgumentException e) {
      return "?";
    }
  }

  /**
   * Merges the items into the retry job and (re)schedules its trigger; {@code items} isn't empty.
   */
  private void scheduleRetryJob(final List<ParkedItem> items) throws SchedulerException {

    final var existingJob = scheduler.getJobDetail(jobKey);
    final var existing =
        existingJob == null
            ? List.<ParkedItem>of()
            : RetryItemsCodec.decode(existingJob.getJobDataMap().getString(DATAKEY_RETRY_ITEMS));
    final var merged = merge(existing, items);
    // 'items' isn't empty, so neither is 'merged'
    final var fireAt =
        fireAt(merged).orElseThrow(() -> new IllegalStateException("No items to schedule"));

    final var jobDetail =
        JobBuilder.newJob(HarvestRetryTriggerJob.class)
            .withIdentity(jobKey)
            .usingJobData(DATAKEY_TENANT, tenantId)
            .usingJobData(DATAKEY_PROVIDER_ID, providerId)
            .usingJobData(DATAKEY_RETRY_ITEMS, RetryItemsCodec.encode(merged))
            .build();
    final var trigger =
        TriggerBuilder.newTrigger()
            .withIdentity(jobKey.getName(), jobKey.getGroup())
            .forJob(jobKey)
            .startAt(Date.from(fireAt))
            .withSchedule(
                // repeats while a run of the provider is in progress, see HarvestRetryTriggerJob
                SimpleScheduleBuilder.simpleSchedule()
                    .withIntervalInMilliseconds(SchedulingUtil.CHECK_INTERVAL.toMillis())
                    .repeatForever()
                    .withMisfireHandlingInstructionFireNow())
            .build();
    scheduler.scheduleJob(jobDetail, Set.of(trigger), true);

    LOGGER.info(
        "Tenant: {}, Provider: {}, parked {} item(s), {} waiting in total, retry at {}",
        tenantId,
        providerId,
        items.size(),
        merged.size(),
        fireAt);
  }

  /**
   * Determines when the retry job fires: at the end of the earliest group of due times (all items
   * due within {@link #COALESCE_WINDOW} after the earliest one). So no item of that group is
   * retried before it is due, and the whole group is retried in one run. Items due later are parked
   * again by that run.
   *
   * @return the time the retry job fires, or empty if there are no items
   */
  private static Optional<Instant> fireAt(final List<ParkedItem> items) {
    return items.stream()
        .map(ParkedItem::dueAt)
        .min(Comparator.naturalOrder())
        .map(first -> latestDueAtUntil(items, first, first.plus(COALESCE_WINDOW)));
  }

  /**
   * @return the latest due time not after {@code clusterEnd}; {@code first} is the earliest due
   *     time, so it is the result if no other item is in the cluster
   */
  private static Instant latestDueAtUntil(
      final List<ParkedItem> items, final Instant first, final Instant clusterEnd) {
    return items.stream()
        .map(ParkedItem::dueAt)
        .filter(dueAt -> !dueAt.isAfter(clusterEnd))
        .reduce(first, (a, b) -> a.isAfter(b) ? a : b);
  }

  /**
   * @return the merged items: the existing ones first, then the new ones
   */
  private static List<ParkedItem> merge(
      final List<ParkedItem> existing, final List<ParkedItem> added) {
    final Map<FetchItem, ParkedItem> merged = new LinkedHashMap<>();
    existing.forEach(item -> merged.put(item.item(), item));
    added.forEach(item -> merged.merge(item.item(), item, RetryScheduler::mergeItem));
    return new ArrayList<>(merged.values());
  }

  /**
   * @return the items split into those that are due (due time not after {@code now}) and those that
   *     are not; both lists keep the original order
   */
  private static DuePartition partitionDue(final List<ParkedItem> items, final Instant now) {
    final var partition =
        items.stream().collect(Collectors.partitioningBy(item -> !item.dueAt().isAfter(now)));
    return new DuePartition(partition.get(true), partition.get(false));
  }

  private static ParkedItem mergeItem(final ParkedItem older, final ParkedItem newer) {
    return new ParkedItem(
        newer.item(),
        Math.max(older.retryCount(), newer.retryCount()),
        older.dueAt().isBefore(newer.dueAt()) ? older.dueAt() : newer.dueAt(),
        newer.reason());
  }

  /** The result of {@link #partitionDue(List, Instant)}. */
  private record DuePartition(List<ParkedItem> dueItems, List<ParkedItem> notDueItems) {}
}
