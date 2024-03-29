package org.olf.erm.usage.harvester.periodic;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.client.OkapiClientImpl.PATH_LOGIN;
import static org.olf.erm.usage.harvester.client.OkapiClientImpl.PATH_LOGIN_EXPIRY;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_TENANT;
import static org.olf.erm.usage.harvester.periodic.AbstractHarvestJob.DATAKEY_TIMESTAMP;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.sql.Date;
import java.time.Instant;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.PeriodicConfig;
import org.folio.rest.jaxrs.model.PeriodicConfig.PeriodicInterval;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.olf.erm.usage.harvester.PostgresContainerRule;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.KeyMatcher;
import org.quartz.listeners.JobListenerSupport;

@RunWith(VertxUnitRunner.class)
public class HarvestTenantPeriodicJobIT {

  public static final String START_PATH = "/erm-usage-harvester/start";
  private static final Vertx vertx = Vertx.vertx();
  private static Context vertxContext;
  private static Scheduler scheduler;
  private static final String TENANT = "tnanet";

  private static final PeriodicConfig config =
      new PeriodicConfig()
          .withStartAt(Date.from(Instant.now()))
          .withPeriodicInterval(PeriodicInterval.DAILY);

  private static final JobKey jobKey = new JobKey(TENANT);
  private static final JobDetail job =
      JobBuilder.newJob()
          .ofType(HarvestTenantPeriodicJob.class)
          .usingJobData(DATAKEY_TENANT, TENANT)
          .usingJobData(DATAKEY_TIMESTAMP, Instant.now().toEpochMilli())
          .withIdentity(jobKey)
          .storeDurably(true)
          .build();

  @ClassRule
  public static WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

  @ClassRule public static PostgresContainerRule pgRule = new PostgresContainerRule(vertx, TENANT);

  @Rule public Timeout timeout = Timeout.seconds(5);

  @BeforeClass
  public static void beforeClass(TestContext context) {
    vertxContext = vertx.getOrCreateContext();
    vertxContext.config().put("okapiUrl", "http://localhost:" + wireMockRule.port());

    PeriodicConfigPgUtil.upsert(vertxContext, TENANT, config)
        .onComplete(context.asyncAssertSuccess());
  }

  @AfterClass
  public static void afterClass() throws SchedulerException {
    scheduler.shutdown();
  }

  @Before
  public void before() throws SchedulerException {
    stubFor(post(PATH_LOGIN_EXPIRY).willReturn(aResponse().withStatus(404)));
    stubFor(
        post(PATH_LOGIN)
            .willReturn(aResponse().withStatus(201).withHeader(XOkapiHeaders.TOKEN, "someToken")));
    WireMock.resetAllRequests();
    scheduler = StdSchedulerFactory.getDefaultScheduler();
    scheduler.clear();
    scheduler.getContext().put("vertxContext", vertxContext);
    scheduler.addJob(job, true);
    scheduler.start();
  }

  @Test
  public void testContextNull(TestContext context) throws SchedulerException {
    Async async = context.async();
    scheduler.getContext().put("vertxContext", null);
    scheduler
        .getListenerManager()
        .addJobListener(
            new JobWasExecutedListener(
                je -> {
                  context.verify(v -> assertThat(je.getMessage()).contains("vert.x context"));
                  async.complete();
                }),
            KeyMatcher.keyEquals(jobKey));
    scheduler.triggerJob(jobKey);
  }

  @Test
  public void testStartFailedLogin(TestContext context) throws SchedulerException {
    stubFor(post(PATH_LOGIN).willReturn(aResponse().withStatus(422)));
    Async async = context.async();
    scheduler
        .getListenerManager()
        .addJobListener(
            new JobWasExecutedListener(
                je -> {
                  context.verify(
                      v -> {
                        assertThat(je.getMessage()).contains("error starting");
                        wireMockRule.verify(0, getRequestedFor(urlPathEqualTo(START_PATH)));
                      });
                  async.complete();
                }),
            KeyMatcher.keyEquals(jobKey));
    scheduler.triggerJob(jobKey);
  }

  @Test
  public void testStartNoConnection(TestContext context) throws SchedulerException {
    stubFor(
        get(urlPathEqualTo(START_PATH))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    Async async = context.async();
    scheduler
        .getListenerManager()
        .addJobListener(
            new JobWasExecutedListener(
                je -> {
                  context.verify(
                      v -> {
                        assertThat(je.getMessage()).contains("error starting");
                        wireMockRule.verify(1, getRequestedFor(urlPathEqualTo(START_PATH)));
                      });
                  async.complete();
                }),
            KeyMatcher.keyEquals(jobKey));
    scheduler.triggerJob(jobKey);
  }

  @Test
  public void testStartError(TestContext context) throws SchedulerException {
    stubFor(get(urlPathEqualTo(START_PATH)).willReturn(notFound()));
    Async async = context.async();
    scheduler
        .getListenerManager()
        .addJobListener(
            new JobWasExecutedListener(
                je -> {
                  context.verify(
                      v -> {
                        assertThat(je.getMessage()).contains("received 404");
                        wireMockRule.verify(1, getRequestedFor(urlPathEqualTo(START_PATH)));
                      });
                  PeriodicConfigPgUtil.get(vertxContext, TENANT)
                      .onComplete(
                          ar -> {
                            if (ar.succeeded()) {
                              context.verify(
                                  v ->
                                      assertThat(ar.result().withId(null))
                                          .usingRecursiveComparison()
                                          .isEqualTo(config));
                              async.complete();
                            } else {
                              context.fail(ar.cause());
                            }
                          });
                }),
            KeyMatcher.keyEquals(jobKey));
    scheduler.triggerJob(jobKey);
  }

  @Test
  public void testStartOk(TestContext context) throws SchedulerException {
    stubFor(get(urlPathEqualTo(START_PATH)).willReturn(ok()));
    Async async = context.async();
    scheduler
        .getListenerManager()
        .addJobListener(
            new JobWasExecutedListener(
                je -> {
                  context.verify(
                      v -> wireMockRule.verify(1, getRequestedFor(urlPathEqualTo(START_PATH))));
                  PeriodicConfigPgUtil.get(vertxContext, TENANT)
                      .onComplete(
                          ar -> {
                            if (ar.succeeded()) {
                              context.verify(
                                  v -> assertThat(ar.result().getLastTriggeredAt()).isNotNull());
                              async.complete();
                            } else {
                              context.fail(ar.cause());
                            }
                          });
                }),
            KeyMatcher.keyEquals(jobKey));
    scheduler.triggerJob(jobKey);
  }

  private static class JobWasExecutedListener extends JobListenerSupport {

    private final Handler<JobExecutionException> handler;

    @Override
    public String getName() {
      return "JobWasExecutedListener";
    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
      handler.handle(jobException);
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {
      System.out.println("veto");
    }

    public JobWasExecutedListener(Handler<JobExecutionException> handler) {
      this.handler = handler;
    }
  }
}
