package org.olf.erm.usage.harvester.client;

import static org.olf.erm.usage.harvester.Messages.ERR_MSG_DECODE;
import static org.olf.erm.usage.harvester.Messages.ERR_MSG_STATUS_WITH_URL;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import java.util.List;
import java.util.Objects;
import org.folio.rest.tools.utils.ModuleName;

public class OkapiClientImpl implements OkapiClient {

  @SuppressWarnings(
      "java:S1075") // suppress "URIs should not be hardcoded" because this is an internal API path
  static final String PATH_ENTITLEMENTS_MODULES = "/entitlements/modules/";

  private final String okapiUrl;
  private final WebClient client;

  public OkapiClientImpl(WebClient webClient, JsonObject cfg) {
    Objects.requireNonNull(cfg);
    this.okapiUrl = cfg.getString("okapiUrl");
    this.client = webClient;
  }

  private static String getModuleId() {
    return ModuleName.getModuleName().replace('_', '-') + "-" + ModuleName.getModuleVersion();
  }

  @Override
  public Future<List<String>> getTenants() {
    if (okapiUrl == null) {
      return Future.failedFuture("okapiUrl must be configured for tenant discovery");
    }
    String url = okapiUrl + PATH_ENTITLEMENTS_MODULES + getModuleId();
    return client
        .getAbs(url)
        .send()
        .transform(
            ar -> {
              if (ar.failed()) {
                return Future.failedFuture(
                    "Failed getting entitled tenants: " + ar.cause().getMessage());
              }
              HttpResponse<?> resp = ar.result();
              if (resp.statusCode() != 200) {
                return Future.failedFuture(
                    String.format(
                        ERR_MSG_STATUS_WITH_URL, resp.statusCode(), resp.statusMessage(), url));
              }
              try {
                JsonArray body = resp.bodyAsJsonArray();
                if (body.stream().anyMatch(e -> !(e instanceof String))) {
                  return Future.failedFuture(
                      String.format(ERR_MSG_DECODE, url, "expected JSON array of strings"));
                }
                return Future.succeededFuture(body.stream().map(String.class::cast).toList());
              } catch (Exception e) {
                return Future.failedFuture(String.format(ERR_MSG_DECODE, url, e.getMessage()));
              }
            });
  }
}
