package org.olf.erm.usage.harvester.periodic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_RETRY_ITEMS;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

/** Test class for {@link HarvestRetryTriggerJob} */
class HarvestRetryTriggerJobTest {

  private static final String TENANT = "tenant";
  private static final String PROVIDER = "provider";
  private static final String RETRY_ITEMS = "{\"version\":1,\"items\":[]}";
  private static final String RETRY_ITEMS_ONE =
      "{\"version\":1,\"items\":[{\"reportType\":\"TR\",\"begin\":\"2025-01-01\","
          + "\"end\":\"2025-01-31\",\"retryCount\":1,\"dueAt\":\"2025-02-01T10:30:00Z\"}]}";
  private static final JobKey PROVIDER_JOB_KEY = new JobKey(PROVIDER, TENANT);
  private static final JobKey RETRY_JOB_KEY = RetryScheduler.retryJobKey(TENANT, PROVIDER);

  private Scheduler scheduler;

  @BeforeEach
  void setUp() throws SchedulerException {
    scheduler = TestSchedulers.newUnstartedScheduler();
  }

  @AfterEach
  void tearDown() throws SchedulerException {
    scheduler.shutdown();
  }

  private static HarvestRetryTriggerJob createJob() {
    final var job = new HarvestRetryTriggerJob();
    job.setTenantId(TENANT);
    job.setProviderId(PROVIDER);
    job.setEncodedRetryItems(RETRY_ITEMS);
    return job;
  }

  private void park() {
    new RetryScheduler(scheduler, TENANT, PROVIDER).park(RetryItemsCodec.decode(RETRY_ITEMS_ONE));
  }

  @Test
  void testSchedulesRetryRun() throws SchedulerException {
    park();

    createJob().trigger(scheduler);

    final var jobDetail = scheduler.getJobDetail(PROVIDER_JOB_KEY);
    assertThat(jobDetail).as("... a provider job was scheduled").isNotNull();
    assertThat(jobDetail.getJobClass()).isEqualTo(HarvestProviderJob.class);
    assertThat(jobDetail.getJobDataMap().getString(DATAKEY_RETRY_ITEMS))
        .as("... it carries the retry items")
        .isEqualTo(RETRY_ITEMS);
    assertThat(scheduler.checkExists(RETRY_JOB_KEY)).as("... the retry job is done").isFalse();
  }

  @Test
  void testWaitsWhileProviderRunIsInProgress() throws SchedulerException {
    SchedulingUtil.scheduleProviderJob(scheduler, TENANT, PROVIDER);
    park();

    createJob().trigger(scheduler);

    assertThat(scheduler.getJobDetail(PROVIDER_JOB_KEY).getJobDataMap())
        .as("... the run in progress is left as it is")
        .doesNotContainKey(DATAKEY_RETRY_ITEMS);
    assertThat(scheduler.checkExists(RETRY_JOB_KEY))
        .as("... the retry job fires again later")
        .isTrue();
  }
}
