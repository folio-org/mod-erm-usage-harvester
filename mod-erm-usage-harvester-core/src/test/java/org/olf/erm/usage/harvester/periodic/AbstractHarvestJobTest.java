package org.olf.erm.usage.harvester.periodic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_PROVIDER_ID;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_RETRY_ITEMS;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_TENANT;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.worker.WorkerVerticle.ParkedItem;
import org.quartz.JobBuilder;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.simpl.PropertySettingJobFactory;
import org.quartz.spi.OperableTrigger;
import org.quartz.spi.TriggerFiredBundle;

/** Test class for {@link AbstractHarvestJob} */
class AbstractHarvestJobTest {

  private static final List<ParkedItem> ITEMS =
      List.of(
          new ParkedItem(
              new FetchItem("TR", "2025-01-01", "2025-01-31"),
              1,
              Instant.parse("2025-02-01T10:30:00Z"),
              "reason"));

  private Scheduler scheduler;

  @BeforeEach
  void setUp() throws SchedulerException {
    scheduler = TestSchedulers.newUnstartedScheduler();
  }

  @AfterEach
  void tearDown() throws SchedulerException {
    scheduler.shutdown();
  }

  /** Creates the job the way Quartz does: the job data is set via the setters. */
  private AbstractHarvestJob createJobFromJobData(final String retryItems)
      throws SchedulerException {
    final var jobBuilder =
        JobBuilder.newJob(HarvestProviderJob.class)
            .usingJobData(DATAKEY_TENANT, "tenant")
            .usingJobData(DATAKEY_PROVIDER_ID, "provider");
    if (retryItems != null) {
      jobBuilder.usingJobData(DATAKEY_RETRY_ITEMS, retryItems);
    }
    final var trigger = (OperableTrigger) TriggerBuilder.newTrigger().startNow().build();
    final var bundle =
        new TriggerFiredBundle(jobBuilder.build(), trigger, null, false, null, null, null, null);
    return (AbstractHarvestJob) new PropertySettingJobFactory().newJob(bundle, scheduler);
  }

  @Test
  void testQuartzDecodesRetryItems() throws SchedulerException {
    final var job = createJobFromJobData(RetryItemsCodec.encode(ITEMS));

    assertThat(job.getRetryItems())
        .as("... Quartz sets the encoded items, the getter returns them decoded")
        .isEqualTo(ITEMS);
    assertThat(job.isRetryRun()).isTrue();
  }

  @Test
  void testRegularRunHasNoRetryItems() throws SchedulerException {
    final var job = createJobFromJobData(null);

    assertThat(job.getRetryItems()).isNull();
    assertThat(job.isRetryRun()).isFalse();
  }

  @Test
  void testUndecodableRetryItemsAreDropped() throws SchedulerException {
    final var job = createJobFromJobData("not json");

    assertThat(job.getRetryItems())
        .as("... the job can still be created and runs as a retry run with nothing to do")
        .isEmpty();
    assertThat(job.isRetryRun()).isTrue();
  }
}
