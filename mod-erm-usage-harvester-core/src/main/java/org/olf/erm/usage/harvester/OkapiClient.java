package org.olf.erm.usage.harvester;

import static org.olf.erm.usage.harvester.Messages.ERR_MSG_DECODE;
import static org.olf.erm.usage.harvester.Messages.ERR_MSG_STATUS;

import io.vertx.core.Future;
import io.vertx.core.Promise;
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
  private final WebClient client;

  public OkapiClient(WebClient webClient, JsonObject cfg) {
    Objects.requireNonNull(cfg);
    this.okapiUrl = cfg.getString("okapiUrl");
    this.tenantsPath = cfg.getString("tenantsPath");
    this.client = webClient;
  }

  public Future<List<String>> getTenants() {
    Promise<List<String>> promise = Promise.promise();

    final String url = okapiUrl + tenantsPath;
    client
        .getAbs(url)
        .send(
            ar -> {
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
}
