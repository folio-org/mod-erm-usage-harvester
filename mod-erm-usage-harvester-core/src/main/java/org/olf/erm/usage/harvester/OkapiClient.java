package org.olf.erm.usage.harvester;

import static org.olf.erm.usage.harvester.Messages.ERR_MSG_DECODE;
import static org.olf.erm.usage.harvester.Messages.ERR_MSG_STATUS;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OkapiClient {

  private static final Logger LOG = LoggerFactory.getLogger(OkapiClient.class);

  private final String okapiUrl;
  private final String tenantsPath;
  private final List<String> moduleIds;
  private final Vertx vertx;

  public OkapiClient(Vertx vertx, JsonObject cfg) {
    Objects.requireNonNull(vertx);
    Objects.requireNonNull(cfg);
    this.okapiUrl = cfg.getString("okapiUrl");
    this.tenantsPath = cfg.getString("tenantsPath");
    this.moduleIds =
        cfg.getJsonArray("moduleIds").stream().map(Object::toString).collect(Collectors.toList());
    this.vertx = vertx;
  }

  public Future<List<String>> getTenants() {
    Future<List<String>> future = Future.future();

    final String url = okapiUrl + tenantsPath;
    WebClient client = WebClient.create(vertx);
    client
        .getAbs(url)
        .send(
            ar -> {
              client.close();
              if (ar.succeeded()) {
                if (ar.result().statusCode() == 200) {
                  JsonArray jsonArray;
                  try {
                    jsonArray = ar.result().bodyAsJsonArray();
                    List<String> tenants =
                        jsonArray.stream()
                            .map(o -> ((JsonObject) o).getString("id"))
                            .collect(Collectors.toList());
                    LOG.info("Found tenants: {}", tenants);
                    future.complete(tenants);
                  } catch (Exception e) {
                    future.fail(String.format(ERR_MSG_DECODE, url, e.getMessage()));
                  }
                } else {
                  future.fail(
                      String.format(
                          ERR_MSG_STATUS,
                          ar.result().statusCode(),
                          ar.result().statusMessage(),
                          url));
                }
              } else {
                future.fail("Failed getting tenants: " + ar.cause());
              }
            });
    return future;
  }

  public Future<Void> hasEnabledUsageModules(String tenantId) {
    final String modulesUrl = okapiUrl + tenantsPath + "/" + tenantId + "/modules";

    Future<Void> future = Future.future();
    WebClient client = WebClient.create(vertx);
    client
        .getAbs(modulesUrl)
        .send(
            ar -> {
              if (ar.succeeded()) {
                if (ar.result().statusCode() == 200) {
                  try {
                    List<String> modules =
                        ar.result().bodyAsJsonArray().stream()
                            .map(o -> ((JsonObject) o).getString("id"))
                            .collect(Collectors.toList());
                    if (modules.containsAll(moduleIds)) {
                      future.complete();
                    } else {
                      future.fail(
                          String.format("Tenant: %s, required module not enabled", tenantId));
                    }
                  } catch (Exception e) {
                    future.fail(
                        String.format(
                            "Tenant: %s, %s",
                            tenantId, String.format(ERR_MSG_DECODE, modulesUrl, e.getMessage())));
                  }
                } else {
                  future.fail(
                      String.format(
                          "Tenant: %s, failed retrieving enabled modules: %s",
                          tenantId,
                          String.format(
                              ERR_MSG_STATUS,
                              ar.result().statusCode(),
                              ar.result().statusMessage(),
                              modulesUrl)));
                }
              } else {
                future.fail(
                    String.format(
                        "Tenant: %s, failed retrieving enabled modules: %s",
                        tenantId, ar.cause().getMessage()));
              }
            });
    return future;
  }
}
