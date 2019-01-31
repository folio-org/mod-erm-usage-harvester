package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import java.security.NoSuchAlgorithmException;
import javax.crypto.SecretKey;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.HarvesterSetting;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.security.AES;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.parsing.Parser;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class ErmUsageHarvesterAPIIT {

  private static Vertx vertx;
  private static final String BASE_URI = "http://localhost";
  private static final String BASE_PATH = "/erm-usage-harvester";
  private static final String TENANT = "diku";
  private static final HarvesterSetting SETTINGS =
      new HarvesterSetting().withUsername("user").withPassword("1234");

  @Rule public Timeout timeout = Timeout.seconds(10);

  @BeforeClass
  public static void setUp(TestContext ctx) {
    vertx = Vertx.vertx();
    try {
      PostgresClient.setIsEmbedded(true);
      PostgresClient pgClient = PostgresClient.getInstance(vertx);
      pgClient.startEmbeddedPostgres();
      System.out.println(pgClient.getConnectionConfig().encodePrettily());
    } catch (Exception e) {
      e.printStackTrace();
      ctx.fail(e);
      return;
    }

    int port = NetworkUtils.nextFreePort();
    RestAssured.reset();
    RestAssured.baseURI = BASE_URI;
    RestAssured.basePath = BASE_PATH;
    RestAssured.port = port;
    RestAssured.defaultParser = Parser.JSON;
    RestAssured.filters(new ResponseLoggingFilter(), new RequestLoggingFilter());
    RestAssured.requestSpecification =
        new RequestSpecBuilder()
            .addHeader(XOkapiHeaders.TENANT, TENANT)
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString())
            .build();

    TenantClient tenantClient = new TenantClient(BASE_URI + ":" + port, TENANT, TENANT);
    DeploymentOptions options =
        new DeploymentOptions().setConfig(new JsonObject().put("http.port", port)).setWorker(true);

    Async async = ctx.async(1);
    vertx.deployVerticle(
        "org.folio.rest.RestVerticle",
        options,
        res -> {
          try {
            tenantClient.postTenant(
                null,
                res2 -> {
                  if (res2.statusCode() == 201) {
                    async.complete();
                  } else {
                    ctx.fail("postTenant did not return 201");
                  }
                });
          } catch (Exception e) {
            ctx.fail(e);
          }
        });
  }

  @AfterClass
  public static void teardown(TestContext context) {
    RestAssured.reset();
    Async async = context.async();
    vertx.close(
        context.asyncAssertSuccess(
            res -> {
              PostgresClient.stopEmbeddedPostgres();
              async.complete();
            }));
  }

  private void testPostAndGetSettings(TestContext ctx, SecretKey secretKey) {
    ErmUsageHarvesterAPI.setSecretKey(secretKey);

    String password = "";
    try {
      if (secretKey != null) {
        password = AES.encryptPasswordAsBase64(SETTINGS.getPassword(), secretKey);
      } else {
        password = SETTINGS.getPassword();
      }
    } catch (Exception e) {
      ctx.fail();
      e.printStackTrace();
    }

    HarvesterSetting postResponse =
        given()
            .body(SETTINGS)
            .post("/settings")
            .then()
            .statusCode(200)
            .extract()
            .as(HarvesterSetting.class);
    assertThat(postResponse.getUsername()).isEqualTo(SETTINGS.getUsername());
    assertThat(postResponse.getPassword()).isEqualTo(password);

    HarvesterSetting getResponse =
        given().get("/settings").then().statusCode(200).extract().as(HarvesterSetting.class);
    assertThat(getResponse.getUsername()).isEqualTo(SETTINGS.getUsername());
    assertThat(getResponse.getPassword()).isEqualTo(password);
  }

  @Test
  public void testPostAndGetWithoutSecretKey(TestContext ctx) {
    testPostAndGetSettings(ctx, null);
  }

  @Test
  public void testPostAndGetWithSecretKey(TestContext ctx) {
    try {
      testPostAndGetSettings(ctx, AES.generateSecretKey());
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
      ctx.fail();
    }
  }
}
