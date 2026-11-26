package org.olf.erm.usage.harvester.periodic;

import java.util.Properties;
import java.util.UUID;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.simpl.RAMJobStore;
import org.quartz.simpl.SimpleThreadPool;

/** Creates Quartz schedulers for tests. */
final class TestSchedulers {

  /**
   * @return a new scheduler with its own in-memory job store. It is not started, so no job fires;
   *     tests only inspect what was stored. Shut it down after the test.
   */
  static Scheduler newUnstartedScheduler() throws SchedulerException {
    final var properties = new Properties();
    properties.setProperty(
        StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, "test-" + UUID.randomUUID());
    properties.setProperty(StdSchedulerFactory.PROP_JOB_STORE_CLASS, RAMJobStore.class.getName());
    properties.setProperty(
        StdSchedulerFactory.PROP_THREAD_POOL_CLASS, SimpleThreadPool.class.getName());
    properties.setProperty("org.quartz.threadPool.threadCount", "1");
    return new StdSchedulerFactory(properties).getScheduler();
  }

  private TestSchedulers() {}
}
