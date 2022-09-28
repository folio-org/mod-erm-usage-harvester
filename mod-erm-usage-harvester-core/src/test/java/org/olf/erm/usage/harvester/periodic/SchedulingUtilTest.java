package org.olf.erm.usage.harvester.periodic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.folio.rest.jaxrs.model.PeriodicConfig.PeriodicInterval.WEEKLY;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_PROVIDER_ID;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_TENANT;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_TOKEN;
import static org.olf.erm.usage.harvester.periodic.SchedulingUtil.PERIODIC_JOB_KEY;
import static org.olf.erm.usage.harvester.periodic.SchedulingUtil.TENANT_JOB_KEY;
import static org.olf.erm.usage.harvester.periodic.SchedulingUtil.createOrUpdateJob;
import static org.olf.erm.usage.harvester.periodic.SchedulingUtil.scheduleProviderJob;
import static org.olf.erm.usage.harvester.periodic.SchedulingUtil.scheduleTenantJob;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.folio.rest.jaxrs.model.PeriodicConfig;
import org.folio.rest.jaxrs.model.PeriodicConfig.PeriodicInterval;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;

public class SchedulingUtilTest {

  private static final String TENANT = "testtenant";
  private static final String TOKEN = "someToken";
  private static final String PROVIDER_ID = "someid-123";
  private static final TriggerKey triggerKey = new TriggerKey(PERIODIC_JOB_KEY, TENANT);
  private static final JobKey jobKey = new JobKey(PERIODIC_JOB_KEY, TENANT);
  private static Scheduler defaultScheduler;

  @BeforeClass
  public static void beforeClass() throws SchedulerException {
    defaultScheduler = StdSchedulerFactory.getDefaultScheduler();
  }

  @AfterClass
  public static void afterClass() throws SchedulerException {
    defaultScheduler.shutdown();
  }

  @Before
  public void before() throws SchedulerException {
    defaultScheduler.clear();
  }

  private Date toDate(int year, int month, int day) {
    return toDate(LocalDateTime.of(year, month, day, 8, 0));
  }

  private Date toDate(int year, int month, int day, int hour, int minute) {
    return toDate(LocalDateTime.of(year, month, day, hour, minute));
  }

  private Date toDate(LocalDateTime datetime) {
    return Date.from(datetime.atZone(ZoneId.systemDefault()).toInstant());
  }

  private Trigger getTrigger() throws SchedulerException {
    return defaultScheduler.getTrigger(triggerKey);
  }

  private void assertCreateOrUpdateJob(PeriodicConfig config) throws SchedulerException {
    assertThat(defaultScheduler.checkExists(jobKey)).isFalse();
    assertThat(defaultScheduler.checkExists(triggerKey)).isFalse();
    SchedulingUtil.createOrUpdateJob(config, TENANT);
    assertThat(defaultScheduler.checkExists(jobKey)).isTrue();
    assertThat(defaultScheduler.checkExists(triggerKey)).isTrue();
  }

  @Test
  public void testDaily() throws SchedulerException {
    Date startDate = toDate(2019, 1, 2);
    PeriodicConfig config =
        new PeriodicConfig().withStartAt(startDate).withPeriodicInterval(PeriodicInterval.DAILY);

    assertCreateOrUpdateJob(config);
    assertThat(getTrigger().getStartTime()).isEqualTo(startDate);
    assertThat(getTrigger().getFireTimeAfter(toDate(2019, 1, 1))).isEqualTo(startDate);
    assertThat(getTrigger().getFireTimeAfter(startDate)).isEqualTo(toDate(2019, 1, 3));
    assertThat(getTrigger().getFireTimeAfter(toDate(2020, 2, 1))).isEqualTo(toDate(2020, 2, 2));
    assertThat(getTrigger().getFireTimeAfter(toDate(2020, 2, 23))).isEqualTo(toDate(2020, 2, 24));
    assertThat(getTrigger().getFireTimeAfter(toDate(2020, 2, 29))).isEqualTo(toDate(2020, 3, 1));
  }

  @Test
  public void testWeeklySUN() throws SchedulerException {
    Date startDate = toDate(2019, 1, 6);
    PeriodicConfig config =
        new PeriodicConfig().withStartAt(startDate).withPeriodicInterval(WEEKLY);

    assertCreateOrUpdateJob(config);
    assertThat(getTrigger().getStartTime()).isEqualTo(startDate);
    assertThat(getTrigger().getFireTimeAfter(toDate(2019, 1, 1))).isEqualTo(startDate);
    assertThat(getTrigger().getFireTimeAfter(startDate)).isEqualTo(toDate(2019, 1, 13));
    assertThat(getTrigger().getFireTimeAfter(toDate(2020, 2, 1))).isEqualTo(toDate(2020, 2, 2));
    assertThat(getTrigger().getFireTimeAfter(toDate(2020, 2, 23))).isEqualTo(toDate(2020, 3, 1));
  }

  @Test
  public void testWeeklyWED() throws SchedulerException {
    Date startDate = toDate(2019, 1, 2);
    PeriodicConfig config =
        new PeriodicConfig().withStartAt(startDate).withPeriodicInterval(WEEKLY);

    assertCreateOrUpdateJob(config);
    assertThat(getTrigger().getStartTime()).isEqualTo(startDate);
    assertThat(getTrigger().getFireTimeAfter(toDate(2019, 1, 1))).isEqualTo(startDate);
    assertThat(getTrigger().getFireTimeAfter(startDate)).isEqualTo(toDate(2019, 1, 9));
    assertThat(getTrigger().getFireTimeAfter(toDate(2020, 2, 1))).isEqualTo(toDate(2020, 2, 5));
    assertThat(getTrigger().getFireTimeAfter(toDate(2020, 2, 29))).isEqualTo(toDate(2020, 3, 4));
  }

  @Test
  public void testMonthly() throws SchedulerException {
    Date startDate = toDate(2019, 1, 12, 9, 5);
    PeriodicConfig config =
        new PeriodicConfig().withStartAt(startDate).withPeriodicInterval(PeriodicInterval.MONTHLY);

    assertCreateOrUpdateJob(config);
    assertThat(getTrigger().getStartTime()).isEqualTo(startDate);
    assertThat(getTrigger().getFireTimeAfter(toDate(2019, 1, 1))).isEqualTo(startDate);
    assertThat(getTrigger().getFireTimeAfter(startDate)).isEqualTo(toDate(2019, 2, 12, 9, 5));
    assertThat(getTrigger().getFireTimeAfter(toDate(2020, 3, 24, 9, 5)))
        .isEqualTo(toDate(2020, 4, 12, 9, 5));
  }

  @Test
  public void testLastMonthly() throws SchedulerException {
    Date startDate = toDate(2019, 1, 29);
    PeriodicConfig config =
        new PeriodicConfig().withStartAt(startDate).withPeriodicInterval(PeriodicInterval.MONTHLY);

    assertCreateOrUpdateJob(config);
    assertThat(getTrigger().getStartTime()).isEqualTo(startDate);
    assertThat(getTrigger().getFireTimeAfter(toDate(2019, 1, 1))).isEqualTo(toDate(2019, 1, 31));
    assertThat(getTrigger().getFireTimeAfter(startDate)).isEqualTo(toDate(2019, 1, 31));
    assertThat(getTrigger().getFireTimeAfter(toDate(2019, 2, 1))).isEqualTo(toDate(2019, 2, 28));
    assertThat(getTrigger().getFireTimeAfter(toDate(2020, 2, 1))).isEqualTo(toDate(2020, 2, 29));
    assertThat(getTrigger().getFireTimeAfter(toDate(2020, 3, 1))).isEqualTo(toDate(2020, 3, 31));
    assertThat(getTrigger().getFireTimeAfter(toDate(2020, 4, 1))).isEqualTo(toDate(2020, 4, 30));
  }

  @Test
  public void testDeleteJob() throws SchedulerException {
    Date startDate = toDate(2019, 1, 1);
    PeriodicConfig config =
        new PeriodicConfig().withStartAt(startDate).withPeriodicInterval(PeriodicInterval.MONTHLY);
    assertCreateOrUpdateJob(config);
    SchedulingUtil.deleteJob(TENANT);
    assertThat(defaultScheduler.checkExists(jobKey)).isFalse();
    assertThat(defaultScheduler.checkExists(triggerKey)).isFalse();
  }

  @Test
  public void testLastTriggeredIsAfterStart() throws SchedulerException {
    Date startDate = toDate(2019, 1, 1);
    Date lastTriggered = toDate(2019, 3, 24);
    PeriodicConfig config =
        new PeriodicConfig()
            .withStartAt(startDate)
            .withLastTriggeredAt(lastTriggered)
            .withPeriodicInterval(PeriodicInterval.DAILY);

    assertCreateOrUpdateJob(config);
    assertThat(getTrigger().getStartTime())
        .isEqualTo(toDate(LocalDateTime.of(2019, 3, 24, 8, 0, 1)));
    assertThat(getTrigger().getNextFireTime()).isEqualTo(toDate(2019, 3, 25));
    assertThat(getTrigger().getFireTimeAfter(toDate(2018, 1, 1))).isEqualTo(toDate(2019, 3, 25));
    assertThat(getTrigger().getFireTimeAfter(startDate)).isEqualTo(toDate(2019, 3, 25));
    assertThat(getTrigger().getFireTimeAfter(lastTriggered)).isEqualTo(toDate(2019, 3, 25));
    assertThat(getTrigger().getFireTimeAfter(toDate(2019, 4, 1))).isEqualTo(toDate(2019, 4, 2));
    assertThat(getTrigger().getFireTimeAfter(toDate(2019, 4, 1, 7, 0)))
        .isEqualTo(toDate(2019, 4, 1));
  }

  @Test
  public void testLastTriggeredIsEqualStart() throws SchedulerException {
    Date startDate = toDate(2019, 1, 1);
    Date lastTriggered = toDate(2019, 1, 1);
    PeriodicConfig config =
        new PeriodicConfig()
            .withStartAt(startDate)
            .withLastTriggeredAt(lastTriggered)
            .withPeriodicInterval(PeriodicInterval.DAILY);

    assertCreateOrUpdateJob(config);
    assertThat(getTrigger().getStartTime())
        .isEqualTo(toDate(LocalDateTime.of(2019, 1, 1, 8, 0, 1)));
    assertThat(getTrigger().getNextFireTime()).isEqualTo(toDate(2019, 1, 2));
    assertThat(getTrigger().getFireTimeAfter(toDate(2018, 1, 1))).isEqualTo(toDate(2019, 1, 2));
    assertThat(getTrigger().getFireTimeAfter(startDate)).isEqualTo(toDate(2019, 1, 2));
    assertThat(getTrigger().getFireTimeAfter(toDate(2019, 4, 1))).isEqualTo(toDate(2019, 4, 2));
    assertThat(getTrigger().getFireTimeAfter(toDate(2019, 4, 1, 7, 0)))
        .isEqualTo(toDate(2019, 4, 1));
  }

  @Test
  public void testLastTriggeredIsBeforeStart() throws SchedulerException {
    Date startDate = toDate(2019, 1, 1);
    Date lastTriggered = toDate(2018, 3, 24);
    PeriodicConfig config =
        new PeriodicConfig()
            .withStartAt(startDate)
            .withLastTriggeredAt(lastTriggered)
            .withPeriodicInterval(PeriodicInterval.DAILY);

    assertCreateOrUpdateJob(config);
    assertThat(getTrigger().getStartTime()).isEqualTo(startDate);
    assertThat(getTrigger().getNextFireTime()).isEqualTo(startDate);
    assertThat(getTrigger().getFireTimeAfter(toDate(2018, 1, 1))).isEqualTo(startDate);
    assertThat(getTrigger().getFireTimeAfter(startDate)).isEqualTo(toDate(2019, 1, 2));
  }

  @Test
  public void testScheduleProviderJob() throws SchedulerException {
    assertThatCode(
            () -> {
              scheduleProviderJob(defaultScheduler, TENANT, TOKEN, PROVIDER_ID);
              JobDataMap jobDataMap =
                  defaultScheduler.getJobDetail(new JobKey(PROVIDER_ID, TENANT)).getJobDataMap();
              assertThat(jobDataMap.getString(DATAKEY_TENANT)).isEqualTo(TENANT);
              assertThat(jobDataMap.getString(DATAKEY_TOKEN)).isEqualTo(TOKEN);
              assertThat(jobDataMap.getString(DATAKEY_PROVIDER_ID)).isEqualTo(PROVIDER_ID);
            })
        .doesNotThrowAnyException();
    assertThat(defaultScheduler.checkExists(new JobKey(PROVIDER_ID, TENANT))).isTrue();
    assertThatCode(() -> scheduleProviderJob(defaultScheduler, TENANT, TOKEN, PROVIDER_ID))
        .hasMessageContaining("already scheduled/running")
        .hasMessageContaining(PROVIDER_ID);
  }

  @Test
  public void testScheduleTenantJob() throws SchedulerException {
    assertThatCode(
            () -> {
              scheduleTenantJob(defaultScheduler, TENANT, TOKEN);
              JobDataMap jobDataMap =
                  defaultScheduler.getJobDetail(new JobKey(TENANT_JOB_KEY, TENANT)).getJobDataMap();
              assertThat(jobDataMap.getString(DATAKEY_TENANT)).isEqualTo(TENANT);
              assertThat(jobDataMap.getString(DATAKEY_TOKEN)).isEqualTo(TOKEN);
              assertThat(jobDataMap.getString(DATAKEY_PROVIDER_ID)).isNull();
            })
        .doesNotThrowAnyException();
    assertThat(defaultScheduler.checkExists(new JobKey(TENANT_JOB_KEY, TENANT))).isTrue();
    assertThatCode(() -> scheduleTenantJob(defaultScheduler, TENANT, TOKEN))
        .hasMessageContaining("already in progress")
        .hasMessageContaining(TENANT);
  }

  @Test
  public void testScheduleTenantJobSucceedsWithPeriodicJobPresent() throws SchedulerException {
    assertThatCode(
            () -> {
              PeriodicConfig periodicConfig =
                  new PeriodicConfig()
                      .withPeriodicInterval(WEEKLY)
                      .withStartAt(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)));
              createOrUpdateJob(periodicConfig, TENANT);
            })
        .doesNotThrowAnyException();
    assertThat(defaultScheduler.checkExists(new JobKey(PERIODIC_JOB_KEY, TENANT))).isTrue();
    assertThatCode(() -> scheduleTenantJob(defaultScheduler, TENANT, TOKEN))
        .doesNotThrowAnyException();
    assertThat(defaultScheduler.checkExists(new JobKey(TENANT_JOB_KEY, TENANT))).isTrue();
  }

  @Test
  public void testScheduleTenantJobFailsWithProviderJobPresent() throws SchedulerException {
    assertThatCode(() -> scheduleProviderJob(defaultScheduler, TENANT, TOKEN, PROVIDER_ID))
        .doesNotThrowAnyException();
    assertThat(defaultScheduler.checkExists(new JobKey(PROVIDER_ID, TENANT))).isTrue();
    assertThatCode(() -> scheduleTenantJob(defaultScheduler, TENANT, TOKEN))
        .hasMessageContaining("already in progress")
        .hasMessageContaining(TENANT);
    assertThat(defaultScheduler.checkExists(new JobKey(TENANT_JOB_KEY, TENANT))).isFalse();
  }
}
