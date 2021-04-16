package org.olf.erm.usage.harvester;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.impl.TenantAPI;
import org.folio.rest.persist.PostgresClient;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmbeddedPostgresRule implements TestRule {

  private static final Logger log = LoggerFactory.getLogger(EmbeddedPostgresRule.class);
  Vertx vertx;
  List<String> tenants = new ArrayList<>();

  public EmbeddedPostgresRule(Vertx vertx, String... tenants) {
    this(vertx);
    this.tenants = Arrays.asList(tenants);
  }

  public EmbeddedPostgresRule(Vertx vertx) {
    this.vertx = vertx;
    PostgresClient.setPostgresTester(new PostgresTesterContainer());
  }

  private Future<List<String>> createSchema(String tenant) {
    log.info("Creating schema for tenant: {}", tenant);
    Promise<List<String>> createSchema = Promise.promise();
    try {
      String[] sqlFile = new TenantAPI().sqlFile(tenant, false, null, null);
      PostgresClient.getInstance(vertx)
          .runSQLFile(
              String.join("\n", sqlFile),
              true,
              ar -> {
                if (ar.succeeded()) {
                  if (ar.result().size() == 0) {
                    createSchema.complete(ar.result());
                  } else createSchema.fail(tenant + ": " + ar.result().get(0));
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
      CompletableFuture<List<String>> future = new CompletableFuture<>();
      createSchemas(Future.succeededFuture(), new ArrayList<>(tenants))
          .onComplete(
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
          PostgresClient.stopPostgresTester();
        }
      }
    };
  }
}
