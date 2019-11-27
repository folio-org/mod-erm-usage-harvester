package org.olf.erm.usage.harvester;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.ddlgen.Schema;
import org.folio.rest.persist.ddlgen.SchemaMaker;
import org.folio.rest.persist.ddlgen.TenantOperation;
import org.folio.rest.tools.utils.VertxUtils;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmbeddedPostgresRule implements TestRule {

  private static final Logger log = LoggerFactory.getLogger(EmbeddedPostgresRule.class);
  Vertx vertx = VertxUtils.getVertxFromContextOrNew();
  List<String> tenants = new ArrayList<>();

  public EmbeddedPostgresRule(String... tenants) {
    this.tenants = Arrays.stream(tenants).collect(Collectors.toCollection(ArrayList::new));
  }

  public EmbeddedPostgresRule() {}

  private Future<List<String>> createSchema(String tenant) {
    log.info("Creating schema for tenant: {}", tenant);
    Promise<List<String>> createSchema = Promise.promise();
    try {
      String tableInput =
          Resources.toString(
              Resources.getResource("templates/db_scripts/schema.json"), StandardCharsets.UTF_8);
      SchemaMaker sMaker =
          new SchemaMaker(
              tenant, PostgresClient.getModuleName(), TenantOperation.CREATE, null, null);
      sMaker.setSchema(new ObjectMapper().readValue(tableInput, Schema.class));
      String sqlFile = sMaker.generateDDL();

      PostgresClient.getInstance(vertx)
          .runSQLFile(
              sqlFile,
              true,
              ar -> {
                if (ar.succeeded()) {
                  if (ar.result().size() == 0) createSchema.complete(ar.result());
                  else createSchema.fail(tenant + ": " + ar.result().get(0));
                } else {
                  createSchema.fail(ar.cause());
                }
              });
    } catch (Exception e) {
      createSchema.fail(e);
    }

    return createSchema.future();
  }

  private Future<List<String>> createSchemas(Future<List<String>> start, List<String> tenantList) {
    if (tenantList.size() >= 1) {
      String tenant = tenantList.remove(0);
      return createSchemas(start.compose(v -> createSchema(tenant)), tenantList);
    } else {
      return start;
    }
  }

  @Override
  public Statement apply(Statement base, Description description) {
    try {
      PostgresClient.getInstance(vertx).startEmbeddedPostgres();

      CompletableFuture<List<String>> future = new CompletableFuture<>();
      createSchemas(Future.succeededFuture(), new ArrayList<>(tenants))
          .setHandler(
              ar -> {
                if (ar.succeeded()) {
                  future.complete(ar.result());
                } else {
                  future.completeExceptionally(ar.cause());
                }
              });
      future.get();
    } catch (Exception e) {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          throw e;
        }
      };
    }

    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        try {
          base.evaluate();
        } finally {
          PostgresClient.stopEmbeddedPostgres();
        }
      }
    };
  }
}
