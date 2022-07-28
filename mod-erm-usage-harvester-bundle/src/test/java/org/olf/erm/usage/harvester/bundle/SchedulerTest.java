package org.olf.erm.usage.harvester.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rest.jaxrs.model.PeriodicConfig.PeriodicInterval.WEEKLY;
import static org.olf.erm.usage.harvester.periodic.SchedulingUtil.PERIODIC_JOB_KEY;
import static org.olf.erm.usage.harvester.periodic.SchedulingUtil.TENANT_JOB_KEY;
import static org.quartz.impl.matchers.GroupMatcher.anyGroup;
import static org.quartz.impl.matchers.GroupMatcher.groupEquals;

import java.sql.Date;
import java.time.Instant;
import org.folio.rest.jaxrs.model.PeriodicConfig;
import org.junit.Test;
import org.olf.erm.usage.harvester.periodic.SchedulingUtil;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

public class SchedulerTest {

  private static final String TENANT = "testtenant";
  private static final String TOKEN = "someToken";
  private static final String PROVIDER_ID = "1234";

  @Test
  public void testScheduler() throws SchedulerException {
    Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
    SchedulingUtil.createOrUpdateJob(
        scheduler,
        new PeriodicConfig().withPeriodicInterval(WEEKLY).withStartAt(Date.from(Instant.now())),
        TENANT);
    SchedulingUtil.scheduleTenantJob(scheduler, TENANT, TOKEN);
    SchedulingUtil.scheduleProviderJob(scheduler, TENANT, TOKEN, PROVIDER_ID);

    assertThat(scheduler.getJobKeys(groupEquals(TENANT))).hasSize(3);
    assertThat(scheduler.getTriggerKeys(anyGroup())).hasSize(3);
    assertThat(scheduler.checkExists(new JobKey(PERIODIC_JOB_KEY, TENANT))).isTrue();
    assertThat(scheduler.checkExists(new JobKey(TENANT_JOB_KEY, TENANT))).isTrue();
    assertThat(scheduler.checkExists(new JobKey(PROVIDER_ID, TENANT))).isTrue();
  }
}
