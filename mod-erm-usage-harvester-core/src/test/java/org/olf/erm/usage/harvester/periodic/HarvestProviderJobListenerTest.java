package org.olf.erm.usage.harvester.periodic;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.test.appender.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/** Test class for {@link HarvestProviderJobListener} */
class HarvestProviderJobListenerTest {

  private static final String LOGGER_NAME = HarvestProviderJobListener.class.getName();
  private static final String JOB_FAILURE = "Provider: someProvider, Received status code 404";

  private final HarvestProviderJobListener listener = new HarvestProviderJobListener();

  private ListAppender listAppender;
  private Level previousLevel;

  @BeforeEach
  void setUp() {
    listAppender = new ListAppender("harvestProviderJobListenerTest");
    listAppender.start();
    coreLogger().addAppender(listAppender);
    previousLevel = coreLogger().getLevel();
    Configurator.setLevel(LOGGER_NAME, Level.INFO);
  }

  @AfterEach
  void tearDown() {
    coreLogger().removeAppender(listAppender);
    listAppender.stop();
    Configurator.setLevel(LOGGER_NAME, previousLevel);
  }

  @Test
  void testFailedHarvestProviderJobIsLogged() {
    listener.jobWasExecuted(
        contextFor(HarvestProviderJob.class), new JobExecutionException(JOB_FAILURE));

    assertThat(errorEvents())
        .singleElement()
        .satisfies(e -> assertThat(e.getMessage().getFormattedMessage()).contains(JOB_FAILURE));
  }

  @Test
  void testSuccessfulHarvestProviderJobIsNotLogged() {
    listener.jobWasExecuted(contextFor(HarvestProviderJob.class), null);

    assertThat(errorEvents()).isEmpty();
  }

  @Test
  void testFailedJobOfOtherClassIsNotLogged() {
    listener.jobWasExecuted(
        contextFor(HarvestTenantJob.class), new JobExecutionException(JOB_FAILURE));

    assertThat(errorEvents()).isEmpty();
  }

  private List<LogEvent> errorEvents() {
    return listAppender.getEvents().stream().filter(e -> e.getLevel().equals(Level.ERROR)).toList();
  }

  /** The listener only reads the job detail, so a proxy answering just that is enough. */
  private static JobExecutionContext contextFor(final Class<? extends Job> jobClass) {
    final var jobDetail = JobBuilder.newJob(jobClass).withIdentity("job", "tenant").build();
    return (JobExecutionContext)
        Proxy.newProxyInstance(
            JobExecutionContext.class.getClassLoader(),
            new Class<?>[] {JobExecutionContext.class},
            (proxy, method, args) -> {
              if (method.getName().equals("getJobDetail")) {
                return jobDetail;
              }
              throw new UnsupportedOperationException(method.getName());
            });
  }

  private static org.apache.logging.log4j.core.Logger coreLogger() {
    return (org.apache.logging.log4j.core.Logger) LogManager.getLogger(LOGGER_NAME);
  }
}
