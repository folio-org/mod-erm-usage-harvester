package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.parsing.Parser;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.IOException;
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
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;

@RunWith(VertxUnitRunner.class)
public class ErmUsageHarvesterPeriodicAPIIT {

  private static final String TENANT = "testtenant";
  private static Vertx vertx;
  private static int port;

  @BeforeClass
  public static void beforeClass(TestContext context) throws IOException {
    Async async = context.async();
    vertx = Vertx.vertx();
    PostgresClient instance = PostgresClient.getInstance(vertx);
    instance.startEmbeddedPostgres();

    port = NetworkUtils.nextFreePort();

    RestAssured.reset();
    RestAssured.port = port;
    RestAssured.basePath = "erm-usage-harvester/periodic";
    RestAssured.defaultParser = Parser.JSON;

    TenantClient tenantClient = new TenantClient("http://localhost:" + port, TENANT, TENANT);
    DeploymentOptions options = new DeploymentOptions();
    options.setConfig(new JsonObject().put("http.port", port).put("testing", true));
    vertx.deployVerticle(
        RestVerticle.class.getName(),
        options,
        h -> {
          try {
            tenantClient.postTenant(null, res -> async.complete());
          } catch (Exception e) {
            context.fail(e);
          }
        });
  }

  @AfterClass
  public static void afterClass() {
    PostgresClient.stopEmbeddedPostgres();
    vertx.close();
  }

  @Test
  public void testThatWeCanPostGetUpdateAndDelete() throws SchedulerException {
    RequestSpecification baseReq =
        new RequestSpecBuilder()
            .addHeader(XOkapiHeaders.TENANT, TENANT)
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .build();
    PeriodicConfig periodicConfig =
        new PeriodicConfig()
            .withStartAt(
                Date.from(
                    LocalDateTime.of(2019, 1, 1, 8, 0).atZone(ZoneId.systemDefault()).toInstant()))
            .withPeriodicInterval(PeriodicInterval.DAILY);
    PeriodicConfig periodicConfig2 =
        new PeriodicConfig()
            .withStartAt(
                Date.from(
                    LocalDateTime.of(2019, 1, 1, 8, 5).atZone(ZoneId.systemDefault()).toInstant()))
            .withPeriodicInterval(PeriodicInterval.DAILY);

    Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
    JobKey jobKey = new JobKey(TENANT);
    TriggerKey triggerKey = new TriggerKey(TENANT);

    given().spec(baseReq).get().then().statusCode(404);
    given().spec(baseReq).delete().then().statusCode(404);

    // POST
    given().spec(baseReq).body(periodicConfig).post().then().statusCode(201);
    assertThat(
            given()
                .spec(baseReq)
                .get()
                .then()
                .statusCode(200)
                .extract()
                .as(PeriodicConfig.class)
                .withId(null))
        .isEqualToComparingFieldByFieldRecursively(periodicConfig);

    assertThat(scheduler.checkExists(jobKey)).isTrue();
    assertThat(scheduler.checkExists(triggerKey)).isTrue();
    assertThat(scheduler.getTrigger(triggerKey).getStartTime())
        .isEqualTo(periodicConfig.getStartAt());

    // UPDATE
    given().spec(baseReq).body(periodicConfig2).post().then().statusCode(201);
    assertThat(
            given()
                .spec(baseReq)
                .get()
                .then()
                .statusCode(200)
                .extract()
                .as(PeriodicConfig.class)
                .withId(null))
        .isEqualToComparingFieldByFieldRecursively(periodicConfig2);

    assertThat(scheduler.checkExists(jobKey)).isTrue();
    assertThat(scheduler.checkExists(triggerKey)).isTrue();
    assertThat(scheduler.getTrigger(triggerKey).getStartTime())
        .isEqualTo(periodicConfig2.getStartAt());

    // DELETE
    given().spec(baseReq).delete().then().statusCode(204);
    given().spec(baseReq).get().then().statusCode(404);

    assertThat(scheduler.checkExists(jobKey)).isFalse();
    assertThat(scheduler.checkExists(triggerKey)).isFalse();

    PostgresClient.stopEmbeddedPostgres();
    given().spec(baseReq).body(periodicConfig).post().then().statusCode(500);
    given().spec(baseReq).get().then().statusCode(500);
    given().spec(baseReq).delete().then().statusCode(500);
  }
}
