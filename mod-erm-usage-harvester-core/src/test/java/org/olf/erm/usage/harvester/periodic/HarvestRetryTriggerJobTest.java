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
  private static final JobKey PROVIDER_JOB_KEY = new JobKey(PROVIDER, TENANT);

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

  @Test
  void testSchedulesRetryRun() throws SchedulerException {
    createJob().trigger(scheduler);

    final var jobDetail = scheduler.getJobDetail(PROVIDER_JOB_KEY);
    assertThat(jobDetail).as("... a provider job was scheduled").isNotNull();
    assertThat(jobDetail.getJobClass()).isEqualTo(HarvestProviderJob.class);
    assertThat(jobDetail.getJobDataMap().getString(DATAKEY_RETRY_ITEMS))
        .as("... it carries the retry items")
        .isEqualTo(RETRY_ITEMS);
  }

  @Test
  void testDropsItemsIfProviderRunIsInProgress() throws SchedulerException {
    SchedulingUtil.scheduleProviderJob(scheduler, TENANT, PROVIDER);

    createJob().trigger(scheduler);

    assertThat(scheduler.getJobDetail(PROVIDER_JOB_KEY).getJobDataMap())
        .as("... the run in progress is left as it is")
        .doesNotContainKey(DATAKEY_RETRY_ITEMS);
  }
}
