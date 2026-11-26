package org.olf.erm.usage.harvester.periodic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_PROVIDER_ID;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_RETRY_ITEMS;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_TENANT;
import static org.olf.erm.usage.harvester.periodic.RetryScheduler.COALESCE_WINDOW;
import static org.olf.erm.usage.harvester.periodic.RetryScheduler.retryJobKey;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.worker.WorkerVerticle.ParkedItem;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;

/** Test class for {@link RetryScheduler} */
class RetrySchedulerTest {

  private static final String TENANT = "tenant";
  private static final String PROVIDER = "provider";
  private static final Instant NOW = Instant.parse("2025-02-01T10:00:00Z");
  private static final FetchItem JAN = new FetchItem("TR", "2025-01-01", "2025-01-31");
  private static final FetchItem FEB = new FetchItem("TR", "2025-02-01", "2025-02-28");
  private static final FetchItem MAR = new FetchItem("TR", "2025-03-01", "2025-03-31");

  private Scheduler scheduler;
  private RetryScheduler retryScheduler;

  @BeforeEach
  void setUp() throws SchedulerException {
    scheduler = TestSchedulers.newUnstartedScheduler();
    retryScheduler = new RetryScheduler(scheduler, TENANT, PROVIDER);
  }

  @AfterEach
  void tearDown() throws SchedulerException {
    scheduler.shutdown();
  }

  private static ParkedItem parked(final FetchItem item, final int retryCount, final Instant due) {
    return new ParkedItem(item, retryCount, due, "reason");
  }

  @Test
  void testParkFiresAtEndOfCluster() throws SchedulerException {
    retryScheduler.park(
        List.of(
            parked(JAN, 1, NOW.plus(Duration.ofMinutes(30))),
            parked(FEB, 1, NOW.plus(Duration.ofMinutes(35)))));

    assertThat(fireTime())
        .as("... the cluster's latest due time")
        .isEqualTo(NOW.plus(Duration.ofMinutes(35)));
  }

  @Test
  void testParkIgnoresItemsOutsideCluster() throws SchedulerException {
    final var first = NOW.plus(Duration.ofMinutes(30));

    retryScheduler.park(
        List.of(
            parked(JAN, 1, first),
            parked(FEB, 1, first.plus(COALESCE_WINDOW)),
            parked(MAR, 1, first.plus(COALESCE_WINDOW).plusSeconds(1))));

    assertThat(fireTime())
        .as("... the window's end is part of the cluster, items after it are not")
        .isEqualTo(first.plus(COALESCE_WINDOW));
    assertThat(retryItems()).as("... all items are kept").hasSize(3);
  }

  @Test
  void testParkMergesPerItem() throws SchedulerException {
    final var early = NOW.plus(Duration.ofMinutes(30));
    final var late = NOW.plus(Duration.ofMinutes(60));

    retryScheduler.park(List.of(parked(JAN, 1, early), parked(FEB, 2, late)));
    retryScheduler.park(
        List.of(new ParkedItem(FEB, 1, early, "newer"), new ParkedItem(MAR, 1, late, "new")));

    assertThat(retryItems())
        .as(
            "... per item, the higher retry count, earlier due time and newer reason win, new items"
                + " last")
        .containsExactly(
            parked(JAN, 1, early),
            new ParkedItem(FEB, 2, early, "newer"),
            new ParkedItem(MAR, 1, late, "new"));
  }

  @Test
  void testGetDueAndParkRest() throws SchedulerException {
    final var due = parked(JAN, 1, NOW);
    final var notDue = parked(FEB, 1, NOW.plusSeconds(1));

    assertThat(retryScheduler.getDueAndParkRest(List.of(due, notDue), NOW))
        .as("... due exactly now counts as due")
        .containsExactly(due);
    assertThat(retryItems()).as("... the other item is parked again").containsExactly(notDue);
  }

  @Test
  void testGetDueAndParkRestWithAllItemsDue() throws SchedulerException {
    final var items = List.of(parked(JAN, 1, NOW), parked(FEB, 1, NOW.minusSeconds(1)));

    assertThat(retryScheduler.getDueAndParkRest(items, NOW)).containsExactlyElementsOf(items);
    assertThat(scheduler.checkExists(retryJobKey(TENANT, PROVIDER)))
        .as("... nothing is parked")
        .isFalse();
  }

  @Test
  void testParkCreatesRetryJob() throws SchedulerException {
    // Quartz keeps the start time in milliseconds
    final var due = Instant.now().plus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.MILLIS);
    final var items = List.of(parked(JAN, 1, due));

    retryScheduler.park(items);

    final var jobKey = retryJobKey(TENANT, PROVIDER);
    final var jobDetail = scheduler.getJobDetail(jobKey);
    assertThat(jobDetail).as("... a retry job exists").isNotNull();
    assertThat(jobKey.getGroup()).isEqualTo(TENANT);
    assertThat(jobDetail.getJobClass()).isEqualTo(HarvestRetryTriggerJob.class);
    assertThat(jobDetail.getJobDataMap().getString(DATAKEY_TENANT)).isEqualTo(TENANT);
    assertThat(jobDetail.getJobDataMap().getString(DATAKEY_PROVIDER_ID)).isEqualTo(PROVIDER);
    assertThat(RetryItemsCodec.decode(jobDetail.getJobDataMap().getString(DATAKEY_RETRY_ITEMS)))
        .containsExactlyElementsOf(items);
    assertThat(fireTime()).as("... the trigger fires when the item is due").isEqualTo(due);
  }

  @Test
  void testParkMergesAndMovesTrigger() throws SchedulerException {
    final var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    final var late = now.plus(Duration.ofHours(2));
    final var early = now.plus(Duration.ofMinutes(30));

    retryScheduler.park(List.of(parked(JAN, 1, late)));
    retryScheduler.park(List.of(parked(FEB, 2, early)));

    final var jobDetail = scheduler.getJobDetail(retryJobKey(TENANT, PROVIDER));
    assertThat(RetryItemsCodec.decode(jobDetail.getJobDataMap().getString(DATAKEY_RETRY_ITEMS)))
        .as("... one retry job with both items")
        .containsExactly(parked(JAN, 1, late), parked(FEB, 2, early));
    assertThat(fireTime()).as("... the trigger moved to the earlier item").isEqualTo(early);
    assertThat(scheduler.getTriggersOfJob(retryJobKey(TENANT, PROVIDER))).hasSize(1);
  }

  @Test
  void testParkNothing() throws SchedulerException {
    retryScheduler.park(List.of());

    assertThat(scheduler.checkExists(retryJobKey(TENANT, PROVIDER))).isFalse();
  }

  @Test
  void testParkDoesNotThrow() throws SchedulerException {
    final var items = List.of(parked(JAN, 1, NOW));
    scheduler.shutdown();

    assertThatCode(() -> retryScheduler.park(items))
        .as("... the failure is only logged")
        .doesNotThrowAnyException();
  }

  @Test
  void testDeleteRetryJob() throws SchedulerException {
    retryScheduler.park(List.of(parked(JAN, 1, NOW)));

    retryScheduler.deleteRetryJob();

    assertThat(scheduler.checkExists(retryJobKey(TENANT, PROVIDER))).isFalse();
  }

  @Test
  void testDeleteRetryJobWithoutRetryJob() {
    assertThatCode(() -> retryScheduler.deleteRetryJob()).doesNotThrowAnyException();
  }

  @Test
  void testDeleteRetryJobs() throws SchedulerException {
    retryScheduler.park(List.of(parked(JAN, 1, NOW)));
    new RetryScheduler(scheduler, TENANT, "other").park(List.of(parked(JAN, 1, NOW)));
    new RetryScheduler(scheduler, "otherTenant", PROVIDER).park(List.of(parked(JAN, 1, NOW)));
    SchedulingUtil.scheduleProviderJob(scheduler, TENANT, "running", List.of());

    RetryScheduler.deleteRetryJobs(scheduler, TENANT);

    assertThat(scheduler.checkExists(retryJobKey(TENANT, PROVIDER))).isFalse();
    assertThat(scheduler.checkExists(retryJobKey(TENANT, "other"))).isFalse();
    assertThat(scheduler.checkExists(retryJobKey("otherTenant", PROVIDER)))
        .as("... another tenant's retry job is kept")
        .isTrue();
    assertThat(scheduler.checkExists(new JobKey("running", TENANT)))
        .as("... provider jobs are kept")
        .isTrue();
  }

  @Test
  void testIsRetryJobKey() {
    assertThat(RetryScheduler.isRetryJobKey(retryJobKey(TENANT, PROVIDER))).isTrue();
    assertThat(RetryScheduler.isRetryJobKey(new JobKey(PROVIDER, TENANT))).isFalse();
  }

  @Test
  void testIsRetryRun() throws SchedulerException {
    SchedulingUtil.scheduleProviderJob(scheduler, TENANT, "retry", List.of(parked(JAN, 1, NOW)));
    SchedulingUtil.scheduleProviderJob(scheduler, TENANT, "regular");

    assertThat(RetryScheduler.isRetryRun(scheduler.getJobDetail(new JobKey("retry", TENANT))))
        .isTrue();
    assertThat(RetryScheduler.isRetryRun(scheduler.getJobDetail(new JobKey("regular", TENANT))))
        .isFalse();
    assertThat(RetryScheduler.isRetryRun(null)).isFalse();
  }

  private List<ParkedItem> retryItems() throws SchedulerException {
    return RetryItemsCodec.decode(
        scheduler
            .getJobDetail(retryJobKey(TENANT, PROVIDER))
            .getJobDataMap()
            .getString(DATAKEY_RETRY_ITEMS));
  }

  private Instant fireTime() throws SchedulerException {
    final var key = retryJobKey(TENANT, PROVIDER);
    return scheduler
        .getTrigger(new TriggerKey(key.getName(), key.getGroup()))
        .getStartTime()
        .toInstant();
  }
}
