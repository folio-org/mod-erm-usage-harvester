package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.IOException;
import java.sql.Date;
import java.time.Instant;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.PeriodicConfig;
import org.folio.rest.jaxrs.model.PeriodicConfig.PeriodicInterval;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
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
  private static Vertx vertx;

  @ClassRule
  public static WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

  private static DeploymentOptions options = new DeploymentOptions();

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

  @BeforeClass
  public static void beforeClass(TestContext context) throws IOException {
    Async async = context.async();
    vertx = Vertx.vertx();
    PostgresClient instance = PostgresClient.getInstance(vertx);
    instance.startEmbeddedPostgres();

    int port = NetworkUtils.nextFreePort();
    options.setConfig(
        new JsonObject()
            .put("http.port", port)
            .put("okapiUrl", "http://localhost:" + wireMockRule.port())
            .put("tenantsPath", "/_/proxy/tenants")
            .put("testing", true));

    Future<String> deployFuture1 = Future.future();
    vertx.deployVerticle(
        RestVerticle.class.getName(),
        options,
        h -> {
          try {
            PeriodicConfig config =
                new PeriodicConfig()
                    .withStartAt(Date.from(Instant.now().plusSeconds(3600)))
                    .withPeriodicInterval(PeriodicInterval.MONTHLY);

            Future<String> tenantFuture1 = Future.future();
            Future<String> tenantFuture2 = Future.future();
            new TenantClient("http://localhost:" + port, TENANT, TENANT)
                .postTenant(
                    null,
                    res ->
                        PeriodicConfigPgUtil.upsert(vertx.getOrCreateContext(), TENANT, config)
                            .setHandler(tenantFuture1.completer()));
            new TenantClient("http://localhost:" + port, TENANT2, TENANT2)
                .postTenant(
                    null,
                    res ->
                        PeriodicConfigPgUtil.upsert(vertx.getOrCreateContext(), TENANT2, config)
                            .setHandler(tenantFuture2.completer()));
            CompositeFuture.all(tenantFuture1, tenantFuture2)
                .setHandler(
                    ar -> {
                      if (ar.succeeded()) {
                        deployFuture1.complete(h.result());
                      } else {
                        deployFuture1.fail(ar.cause());
                      }
                    });
          } catch (Exception e) {
            context.fail(e);
          }
        });

    deployFuture1
        .compose(
            id -> {
              Future<Void> undeployFuture = Future.future();
              vertx.undeploy(id, undeployFuture.completer());
              return undeployFuture;
            })
        .setHandler(
            ar -> {
              if (ar.succeeded()) {
                try {
                  Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
                  assertThat(scheduler.checkExists(new JobKey(TENANT))).isFalse();
                  assertThat(scheduler.checkExists(new JobKey(TENANT2))).isFalse();
                } catch (SchedulerException e) {
                  context.fail(e.getMessage());
                }
                async.complete();
              } else {
                context.fail(ar.cause());
              }
            });

    JsonArray tenantsResponseBody =
        new JsonArray()
            .add(new JsonObject().put("id", TENANT))
            .add(new JsonObject().put("id", TENANT2));
    stubFor(
        get(urlEqualTo("/_/proxy/tenants"))
            .willReturn(aResponse().withBody(tenantsResponseBody.encodePrettily())));
  }

  @AfterClass
  public static void afterClass(TestContext context) {
    PostgresClient.stopEmbeddedPostgres();
    vertx.close();
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
}
