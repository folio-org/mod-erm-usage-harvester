package org.folio.rest.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.TestUtil.shutdownSchedulers;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.rest.RestVerticle;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.quartz.SchedulerException;

@ExtendWith(VertxExtension.class)
class InitImplIT {

  @BeforeAll
  static void setUp() throws SchedulerException {
    // start from a fresh default scheduler, whatever earlier ITs left behind
    shutdownSchedulers();
  }

  @AfterEach
  void tearDown() throws SchedulerException {
    // removes the listeners PostDeployImpl bound to this test's (closed) Vert.x
    shutdownSchedulers();
  }

  @Test
  void testDeploymentFailsWithoutOkapiUrl(Vertx vertx, VertxTestContext context) {
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
  void testDeploymentSucceedsWithOkapiUrlInConfig(Vertx vertx, VertxTestContext context) {
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
  void testDeploymentSucceedsWithOkapiUrlEnvVar(Vertx vertx, VertxTestContext context) {
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
  void testDeploymentSkipsValidationWithTestingFlag(Vertx vertx, VertxTestContext context) {
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
