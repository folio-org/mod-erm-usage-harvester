package org.olf.erm.usage.harvester.client;

import static org.olf.erm.usage.harvester.Messages.ERR_MSG_DECODE;
import static org.olf.erm.usage.harvester.Messages.ERR_MSG_STATUS_WITH_URL;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import java.util.List;
import java.util.Objects;

public class OkapiClientImpl implements OkapiClient {

  @SuppressWarnings(
      "java:S1075") // suppress "URIs should not be hardcoded" because this is an internal API path
  public static final String PATH_TENANTS = "/_/proxy/tenants";

  private final String okapiUrl;
  private final WebClient client;

  public OkapiClientImpl(WebClient webClient, JsonObject cfg) {
    Objects.requireNonNull(cfg);
    this.okapiUrl = cfg.getString("okapiUrl");
    this.client = webClient;
  }

  @Override
  public Future<List<String>> getTenants() {
    String url = okapiUrl + PATH_TENANTS;
    return client
        .getAbs(url)
        .send()
        .transform(
            ar -> {
              if (ar.succeeded()) {
                if (ar.result().statusCode() == 200) {
                  JsonArray jsonArray;
                  try {
                    jsonArray = ar.result().bodyAsJsonArray();
                    List<String> tenants =
                        jsonArray.stream().map(o -> ((JsonObject) o).getString("id")).toList();
                    return Future.succeededFuture(tenants);
                  } catch (Exception e) {
                    return Future.failedFuture(String.format(ERR_MSG_DECODE, url, e.getMessage()));
                  }
                } else {
                  return Future.failedFuture(
                      String.format(
                          ERR_MSG_STATUS_WITH_URL,
                          ar.result().statusCode(),
                          ar.result().statusMessage(),
                          url));
                }
              } else {
                return Future.failedFuture("Failed getting tenants: " + ar.cause());
              }
            });
  }
}
