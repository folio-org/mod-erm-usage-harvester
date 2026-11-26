package org.olf.erm.usage.harvester.periodic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_RETRY_ITEMS;
import static org.olf.erm.usage.harvester.periodic.HarvestDeferredStartJob.GIVE_UP_AFTER;
import static org.olf.erm.usage.harvester.periodic.HarvestDeferredStartJob.startJobKey;
import static org.olf.erm.usage.harvester.periodic.SchedulingUtil.CHECK_INTERVAL;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;

/** Test class for {@link HarvestDeferredStartJob} */
class HarvestDeferredStartJobTest {

  private static final String TENANT = "tenant";
  private static final String PROVIDER = "provider";
  private static final JobKey PROVIDER_JOB_KEY = new JobKey(PROVIDER, TENANT);
  private static final JobKey START_JOB_KEY = startJobKey(TENANT, PROVIDER);
  private static final Instant REQUESTED_AT = Instant.parse("2025-02-01T10:00:00Z");

  private Scheduler scheduler;

  @BeforeEach
  void setUp() throws SchedulerException {
    scheduler = TestSchedulers.newUnstartedScheduler();
  }

  @AfterEach
  void tearDown() throws SchedulerException {
    scheduler.shutdown();
  }

  private static HarvestDeferredStartJob createJob() {
    final var job = new HarvestDeferredStartJob();
    job.setTenantId(TENANT);
    job.setProviderId(PROVIDER);
    job.setRequestedAt(REQUESTED_AT.toEpochMilli());
    return job;
  }

  private void startRetryRun() throws SchedulerException {
    SchedulingUtil.scheduleProviderJob(scheduler, TENANT, PROVIDER, List.of());
  }

  @Test
  void testSchedule() throws SchedulerException {
    final var before = Instant.now();

    HarvestDeferredStartJob.schedule(scheduler, TENANT, PROVIDER);

    assertThat(scheduler.getJobDetail(START_JOB_KEY).getJobClass())
        .isEqualTo(HarvestDeferredStartJob.class);
    final var trigger =
        scheduler.getTrigger(new TriggerKey(START_JOB_KEY.getName(), START_JOB_KEY.getGroup()));
    assertThat(trigger.getStartTime().toInstant())
        .as("... the first check gives the retry run time to cancel itself")
        .isAfterOrEqualTo(before.plus(CHECK_INTERVAL).minusMillis(1));
    assertThatCode(() -> HarvestDeferredStartJob.schedule(scheduler, TENANT, PROVIDER))
        .as("... at most one deferred start per provider")
        .isInstanceOf(SchedulerException.class);
  }

  @Test
  void testTryStartWaitsWhileRunInProgress() throws SchedulerException {
    startRetryRun();
    HarvestDeferredStartJob.schedule(scheduler, TENANT, PROVIDER);

    createJob().tryStart(scheduler, REQUESTED_AT.plus(CHECK_INTERVAL));

    assertThat(scheduler.checkExists(START_JOB_KEY)).as("... it checks again later").isTrue();
    assertThat(scheduler.getJobDetail(PROVIDER_JOB_KEY).getJobDataMap())
        .as("... the retry run is left as it is")
        .containsKey(DATAKEY_RETRY_ITEMS);
  }

  @Test
  void testTryStartSchedulesRegularRun() throws SchedulerException {
    HarvestDeferredStartJob.schedule(scheduler, TENANT, PROVIDER);

    createJob().tryStart(scheduler, REQUESTED_AT.plus(CHECK_INTERVAL));

    assertThat(scheduler.checkExists(START_JOB_KEY)).isFalse();
    assertThat(scheduler.getJobDetail(PROVIDER_JOB_KEY).getJobDataMap())
        .as("... a regular run is scheduled")
        .doesNotContainKey(DATAKEY_RETRY_ITEMS);
  }

  @Test
  void testTryStartGivesUp() throws SchedulerException {
    startRetryRun();
    HarvestDeferredStartJob.schedule(scheduler, TENANT, PROVIDER);

    createJob().tryStart(scheduler, REQUESTED_AT.plus(GIVE_UP_AFTER).plusSeconds(1));

    assertThat(scheduler.checkExists(START_JOB_KEY)).isFalse();
    assertThat(scheduler.getJobDetail(PROVIDER_JOB_KEY).getJobDataMap())
        .containsKey(DATAKEY_RETRY_ITEMS);
  }

  @Test
  void testDeleteDeferredStarts() throws SchedulerException {
    HarvestDeferredStartJob.schedule(scheduler, TENANT, PROVIDER);
    HarvestDeferredStartJob.schedule(scheduler, "otherTenant", PROVIDER);
    startRetryRun();

    HarvestDeferredStartJob.deleteDeferredStarts(scheduler, TENANT);

    assertThat(scheduler.checkExists(START_JOB_KEY)).isFalse();
    assertThat(scheduler.checkExists(startJobKey("otherTenant", PROVIDER))).isTrue();
    assertThat(scheduler.checkExists(PROVIDER_JOB_KEY)).isTrue();
  }
}
