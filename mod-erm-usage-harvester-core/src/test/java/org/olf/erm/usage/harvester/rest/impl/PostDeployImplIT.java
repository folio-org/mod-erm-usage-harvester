package org.olf.erm.usage.harvester.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.sql.Date;
import java.time.Instant;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.PeriodicConfig;
import org.folio.rest.jaxrs.model.PeriodicConfig.PeriodicInterval;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.olf.erm.usage.harvester.PostgresContainerRule;
import org.olf.erm.usage.harvester.periodic.PeriodicConfigPgUtil;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.listeners.SchedulerListenerSupport;

@RunWith(VertxUnitRunner.class)
public class PostDeployImplIT {
  private static final String TENANT = "testtenant";
  private static final String TENANT2 = "tenant2";
  private static Vertx vertx = Vertx.vertx();

  @ClassRule
  public static WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

  @ClassRule
  public static PostgresContainerRule pgRule = new PostgresContainerRule(vertx, TENANT, TENANT2);

  private static DeploymentOptions options = new DeploymentOptions();

  @BeforeClass
  public static void beforeClass(TestContext context) {
    Async async = context.async();

    int port = NetworkUtils.nextFreePort();
    options.setConfig(
        new JsonObject()
            .put("http.port", port)
            .put("okapiUrl", "http://localhost:" + wireMockRule.port())
            .put("tenantsPath", "/_/proxy/tenants")
            .put("testing", true));

    JsonArray tenantsResponseBody =
        new JsonArray()
            .add(new JsonObject().put("id", TENANT))
            .add(new JsonObject().put("id", TENANT2));
    stubFor(
        get(urlEqualTo("/_/proxy/tenants"))
            .willReturn(aResponse().withBody(tenantsResponseBody.encodePrettily())));

    PeriodicConfig config =
        new PeriodicConfig()
            .withStartAt(Date.from(Instant.now().plusSeconds(3600)))
            .withPeriodicInterval(PeriodicInterval.MONTHLY);

    PeriodicConfigPgUtil.upsert(vertx.getOrCreateContext(), TENANT, config)
        .compose(s -> PeriodicConfigPgUtil.upsert(vertx.getOrCreateContext(), TENANT2, config))
        .onComplete(
            ar -> {
              if (ar.succeeded()) {
                async.complete();
              } else {
                context.fail(ar.cause());
              }
            });
    async.awaitSuccess(2000);
  }

  @Test
  public void testThatPostDeployImplIsSchedulingJobs(TestContext context)
      throws SchedulerException {
    Async async = context.async(3);

    Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
    scheduler.getListenerManager().addSchedulerListener(new SchedulerListenerAsyncCountdown(async));
    options.getConfig().put("testing", false);
    vertx.deployVerticle(
        RestVerticle.class.getName(),
        options,
        ar -> {
          if (ar.succeeded()) {
            async.countDown();
          } else {
            context.fail(ar.cause());
          }
        });

    async.awaitSuccess(5000);
    assertThat(scheduler.checkExists(new JobKey(TENANT))).isTrue();
    assertThat(scheduler.checkExists(new TriggerKey(TENANT))).isTrue();
    assertThat(scheduler.checkExists(new JobKey(TENANT2))).isTrue();
    assertThat(scheduler.checkExists(new TriggerKey(TENANT2))).isTrue();
  }

  private static class SchedulerListenerAsyncCountdown extends SchedulerListenerSupport {
    private Async async;

    @Override
    public void jobAdded(JobDetail jobDetail) {
      async.countDown();
    }

    public SchedulerListenerAsyncCountdown(Async async) {
      this.async = async;
    }
  }
}
