package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.periodic.SchedulingUtil.PERIODIC_JOB_KEY;

import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.PeriodicConfig;
import org.folio.rest.jaxrs.model.PeriodicConfig.PeriodicInterval;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.rest.tools.utils.TenantInit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.olf.erm.usage.harvester.PostgresContainerRule;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;

@RunWith(VertxUnitRunner.class)
public class TenantAPIImplIT {

  private static final String TENANT = "tenantapitest";
  private static final PeriodicConfig PERIODIC_CONFIG =
      new PeriodicConfig()
          .withStartAt(
              Date.from(
                  LocalDateTime.of(2099, 1, 1, 8, 0).atZone(ZoneId.systemDefault()).toInstant()))
          .withPeriodicInterval(PeriodicInterval.DAILY);
  private static final TenantAttributes ENABLE_ATTRS =
      new TenantAttributes().withModuleTo("mod-erm-usage-harvester-1.0.0");
  private static final TenantAttributes DISABLE_ATTRS =
      new TenantAttributes().withModuleFrom("mod-erm-usage-harvester-1.0.0");
  private static final TenantAttributes UPGRADE_ATTRS =
      new TenantAttributes()
          .withModuleFrom("mod-erm-usage-harvester-1.0.0")
          .withModuleTo("mod-erm-usage-harvester-2.0.0");
  private static final int TENANT_INIT_TIMEOUT = 10000;

  private static final Vertx vertx = Vertx.vertx();
  private static WebClient webClient;
  private static Scheduler scheduler;
  private static TenantClient tenantClient;
  private static int port;

  @ClassRule public static PostgresContainerRule pgRule = new PostgresContainerRule(vertx, TENANT);

  @BeforeClass
  public static void beforeClass(TestContext context) throws SchedulerException {
    scheduler = StdSchedulerFactory.getDefaultScheduler();
    scheduler.start();
    port = NetworkUtils.nextFreePort();

    RestAssured.reset();
    RestAssured.port = port;
    RestAssured.defaultParser = Parser.JSON;

    webClient = WebClient.create(vertx);
    tenantClient = new TenantClient("http://localhost:" + port, TENANT, null, webClient);

    DeploymentOptions options = new DeploymentOptions();
    options.setConfig(new JsonObject().put("http.port", port).put("testing", true));
    vertx
        .deployVerticle(RestVerticle.class.getName(), options)
        .onComplete(context.asyncAssertSuccess());
  }

  @AfterClass
  public static void afterClass() throws SchedulerException {
    scheduler.shutdown();
    vertx.close();
    RestAssured.reset();
  }

  private static void postPeriodicConfig(String tenant) {
    given()
        .header(XOkapiHeaders.TENANT, tenant)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
        .basePath("erm-usage-harvester/periodic")
        .body(PERIODIC_CONFIG)
        .post()
        .then()
        .statusCode(201);
  }

  private static void assertJobExists(String tenant, boolean exists) throws SchedulerException {
    assertThat(scheduler.checkExists(new JobKey(PERIODIC_JOB_KEY, tenant))).isEqualTo(exists);
    if (exists) {
      assertThat(scheduler.checkExists(new TriggerKey(PERIODIC_JOB_KEY, tenant))).isTrue();
    }
  }

  @Test
  public void testEnableCreatesScheduledJob(TestContext context) throws SchedulerException {
    postPeriodicConfig(TENANT);

    scheduler.clear();
    assertJobExists(TENANT, false);

    TenantInit.exec(tenantClient, ENABLE_ATTRS, TENANT_INIT_TIMEOUT)
        .onComplete(
            context.asyncAssertSuccess(
                v -> {
                  try {
                    assertJobExists(TENANT, true);
                  } catch (SchedulerException e) {
                    context.fail(e);
                  }
                }));
  }

  @Test
  public void testEnableWithoutPeriodicConfigDoesNotCreateJob(TestContext context)
      throws SchedulerException {
    String tenant = "tenantnoperiodicconfig";
    TenantClient noConfigTenantClient =
        new TenantClient("http://localhost:" + port, tenant, null, webClient);

    scheduler.clear();

    TenantInit.exec(noConfigTenantClient, ENABLE_ATTRS, TENANT_INIT_TIMEOUT)
        .onComplete(
            context.asyncAssertSuccess(
                v -> {
                  try {
                    assertJobExists(tenant, false);
                  } catch (SchedulerException e) {
                    context.fail(e);
                  }
                }));
  }

  @Test
  public void testReenableAfterDisableRestoresScheduledJob(TestContext context)
      throws SchedulerException {
    postPeriodicConfig(TENANT);
    assertJobExists(TENANT, true);

    TenantInit.exec(tenantClient, DISABLE_ATTRS, TENANT_INIT_TIMEOUT)
        .compose(
            v -> {
              try {
                assertJobExists(TENANT, false);
              } catch (SchedulerException e) {
                context.fail(e);
              }
              return TenantInit.exec(tenantClient, ENABLE_ATTRS, TENANT_INIT_TIMEOUT);
            })
        .onComplete(
            context.asyncAssertSuccess(
                v -> {
                  try {
                    assertJobExists(TENANT, true);
                  } catch (SchedulerException e) {
                    context.fail(e);
                  }
                }));
  }

  @Test
  public void testUpgradeRestoresScheduledJob(TestContext context) throws SchedulerException {
    postPeriodicConfig(TENANT);

    scheduler.clear();
    assertJobExists(TENANT, false);

    TenantInit.exec(tenantClient, UPGRADE_ATTRS, TENANT_INIT_TIMEOUT)
        .onComplete(
            context.asyncAssertSuccess(
                v -> {
                  try {
                    assertJobExists(TENANT, true);
                  } catch (SchedulerException e) {
                    context.fail(e);
                  }
                }));
  }

  @Test
  public void testDisableRemovesScheduledJob(TestContext context) throws SchedulerException {
    postPeriodicConfig(TENANT);
    assertJobExists(TENANT, true);

    TenantInit.exec(tenantClient, DISABLE_ATTRS, TENANT_INIT_TIMEOUT)
        .onComplete(
            context.asyncAssertSuccess(
                v -> {
                  try {
                    assertJobExists(TENANT, false);
                  } catch (SchedulerException e) {
                    context.fail(e);
                  }
                }));
  }
}
