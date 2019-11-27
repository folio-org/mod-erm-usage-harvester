package org.olf.erm.usage.harvester;

import static org.olf.erm.usage.harvester.Messages.ERR_MSG_DECODE;
import static org.olf.erm.usage.harvester.Messages.ERR_MSG_STATUS;

import io.vertx.core.Future;
import io.vertx.core.Promise;
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
  public static final String INTERFACE_NAME = "erm-usage-harvester";
  public static final String INTERFACE_VER = "1.2";

  private final String okapiUrl;
  private final String tenantsPath;
  private final Vertx vertx;

  public OkapiClient(Vertx vertx, JsonObject cfg) {
    Objects.requireNonNull(vertx);
    Objects.requireNonNull(cfg);
    this.okapiUrl = cfg.getString("okapiUrl");
    this.tenantsPath = cfg.getString("tenantsPath");
    this.vertx = vertx;
  }

  public Future<List<String>> getTenants() {
    Promise<List<String>> promise = Promise.promise();

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
                    promise.complete(tenants);
                  } catch (Exception e) {
                    promise.fail(String.format(ERR_MSG_DECODE, url, e.getMessage()));
                  }
                } else {
                  promise.fail(
                      String.format(
                          ERR_MSG_STATUS,
                          ar.result().statusCode(),
                          ar.result().statusMessage(),
                          url));
                }
              } else {
                promise.fail("Failed getting tenants: " + ar.cause());
              }
            });
    return promise.future();
  }

  public Future<Void> hasHarvesterInterface(String tenantId) {
    final String interfacesUrl = okapiUrl + tenantsPath + "/" + tenantId + "/interfaces";

    Promise<Void> promise = Promise.promise();
    WebClient client = WebClient.create(vertx);
    client
        .getAbs(interfacesUrl)
        .send(
            ar -> {
              if (ar.succeeded()) {
                if (ar.result().statusCode() == 200) {
                  try {
                    List<JsonObject> interfaces =
                        ar.result().bodyAsJsonArray().stream()
                            .map(o -> (JsonObject) o)
                            .collect(Collectors.toList());

                    long count =
                        interfaces.stream()
                            .filter(
                                i ->
                                    i.getString("id", "").equals(INTERFACE_NAME)
                                        && i.getString("version", "").equals(INTERFACE_VER))
                            .count();

                    if (count == 1) {
                      promise.complete();
                    } else {
                      promise.fail(
                          String.format("Tenant: %s, required interface not found", tenantId));
                    }
                  } catch (Exception e) {
                    promise.fail(
                        String.format(
                            "Tenant: %s, %s",
                            tenantId,
                            String.format(ERR_MSG_DECODE, interfacesUrl, e.getMessage())));
                  }
                } else {
                  promise.fail(
                      String.format(
                          "Tenant: %s, failed retrieving interfaces: %s",
                          tenantId,
                          String.format(
                              ERR_MSG_STATUS,
                              ar.result().statusCode(),
                              ar.result().statusMessage(),
                              interfacesUrl)));
                }
              } else {
                promise.fail(
                    String.format(
                        "Tenant: %s, failed retrieving interfaces: %s",
                        tenantId, ar.cause().getMessage()));
              }
            });
    return promise.future();
  }
}
