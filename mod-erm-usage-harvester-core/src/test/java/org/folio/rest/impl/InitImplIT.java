package org.folio.rest.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.rest.RestVerticle;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.olf.erm.usage.harvester.WebClientProvider;

@ExtendWith(VertxExtension.class)
class InitImplIT {

  private static Vertx vertx;

  @BeforeAll
  static void beforeAll() {
    vertx = Vertx.vertx();
  }

  @AfterAll
  static void afterAll() {
    vertx.close();
  }

  @AfterEach
  void afterEach() {
    WebClientProvider.reset();
  }

  @Test
  void testDeploymentFailsWithoutOkapiUrl(VertxTestContext context) {
    int port = NetworkUtils.nextFreePort();
    DeploymentOptions options =
        new DeploymentOptions()
            .setConfig(new JsonObject().put("http.port", port).put("testing", false));

    vertx
        .deployVerticle(RestVerticle.class.getName(), options)
        .onComplete(
            context.failing(
                throwable -> {
                  context.verify(
                      () ->
                          assertThat(throwable.getMessage())
                              .contains("okapiUrl configuration is required"));
                  context.completeNow();
                }));
  }

  @Test
  void testDeploymentSucceedsWithOkapiUrlInConfig(VertxTestContext context) {
    int port = NetworkUtils.nextFreePort();
    DeploymentOptions options =
        new DeploymentOptions()
            .setConfig(
                new JsonObject()
                    .put("http.port", port)
                    .put("okapiUrl", "http://localhost:9130")
                    .put("testing", false));

    vertx
        .deployVerticle(RestVerticle.class.getName(), options)
        .onComplete(
            context.succeeding(
                deploymentId -> {
                  context.verify(() -> assertThat(deploymentId).isNotNull());
                  vertx.undeploy(deploymentId).onComplete(context.succeedingThenComplete());
                }));
  }

  @Test
  @SetEnvironmentVariable(key = "OKAPI_URL", value = "http://localhost:9999")
  void testDeploymentSucceedsWithOkapiUrlEnvVar(VertxTestContext context) {
    int port = NetworkUtils.nextFreePort();
    DeploymentOptions options =
        new DeploymentOptions()
            .setConfig(new JsonObject().put("http.port", port).put("testing", false));

    vertx
        .deployVerticle(RestVerticle.class.getName(), options)
        .onComplete(
            context.succeeding(
                deploymentId -> {
                  context.verify(() -> assertThat(deploymentId).isNotNull());
                  vertx.undeploy(deploymentId).onComplete(context.succeedingThenComplete());
                }));
  }

  @Test
  void testDeploymentSkipsValidationWithTestingFlag(VertxTestContext context) {
    int port = NetworkUtils.nextFreePort();
    DeploymentOptions options =
        new DeploymentOptions()
            .setConfig(new JsonObject().put("http.port", port).put("testing", true));

    vertx
        .deployVerticle(RestVerticle.class.getName(), options)
        .onComplete(
            context.succeeding(
                deploymentId -> {
                  context.verify(() -> assertThat(deploymentId).isNotNull());
                  vertx.undeploy(deploymentId).onComplete(context.succeedingThenComplete());
                }));
  }
}
