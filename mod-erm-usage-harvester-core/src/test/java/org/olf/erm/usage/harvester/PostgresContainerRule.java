package org.olf.erm.usage.harvester;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.impl.TenantAPI;
import org.folio.rest.persist.PostgresClient;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostgresContainerRule implements TestRule {

  private static final Logger log = LoggerFactory.getLogger(PostgresContainerRule.class);
  Vertx vertx;
  List<String> tenants = new ArrayList<>();

  public PostgresContainerRule(Vertx vertx, String... tenants) {
    this(vertx);
    this.tenants = Arrays.asList(tenants);
  }

  public PostgresContainerRule(Vertx vertx) {
    this.vertx = vertx;
  }

  private Future<List<String>> createSchema(String tenant) {
    log.info("Creating schema for tenant: {}", tenant);
    Promise<List<String>> createSchema = Promise.promise();
    try {
      String[] sqlFile = new TenantAPI().sqlFile(tenant, false, null, null, null);
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
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        // set tester here instead of the constructor so it cannot be cleared by other test
        // classes' stopPostgresTester() when this class gets initialized early
        PostgresClient.setPostgresTester(new PostgresTesterContainer());
        try {
          createSchemas(Future.succeededFuture(), new ArrayList<>(tenants))
              .toCompletionStage()
              .toCompletableFuture()
              .get();
          base.evaluate();
        } finally {
          PostgresClient.stopPostgresTester();
        }
      }
    };
  }
}
