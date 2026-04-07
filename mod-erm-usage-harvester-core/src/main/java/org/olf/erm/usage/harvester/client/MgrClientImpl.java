package org.olf.erm.usage.harvester.client;

import static org.olf.erm.usage.harvester.Messages.ERR_MSG_DECODE;
import static org.olf.erm.usage.harvester.Messages.ERR_MSG_STATUS_WITH_URL;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.folio.rest.tools.utils.ModuleName;

public class MgrClientImpl implements MgrClient {

  @SuppressWarnings("java:S1075")
  static final String PATH_ENTITLEMENTS_MODULES = "/entitlements/modules/";

  @SuppressWarnings("java:S1075")
  static final String PATH_TENANTS = "/tenants";

  private final String teClientUrl;
  private final String tmClientUrl;
  private final WebClient client;

  public MgrClientImpl(WebClient webClient, JsonObject cfg) {
    this.teClientUrl = cfg.getString("teClientUrl");
    this.tmClientUrl = cfg.getString("tmClientUrl");
    this.client = webClient;
  }

  private static String getModuleId() {
    return ModuleName.getModuleName().replace('_', '-') + "-" + ModuleName.getModuleVersion();
  }

  @Override
  public Future<List<String>> getEntitledTenantNames() {
    if (teClientUrl == null || tmClientUrl == null) {
      return Future.failedFuture(
          "TE_CLIENT_URL and TM_CLIENT_URL must be configured for tenant discovery");
    }
    return getEntitledTenantIds()
        .compose(
            ids -> ids.isEmpty() ? Future.succeededFuture(List.of()) : resolveTenantsById(ids));
  }

  private Future<Set<String>> getEntitledTenantIds() {
    String url = teClientUrl + PATH_ENTITLEMENTS_MODULES + getModuleId();
    return client
        .getAbs(url)
        .addQueryParam("limit", "500")
        .send()
        .transform(
            ar -> {
              if (ar.succeeded()) {
                HttpResponse<?> resp = ar.result();
                if (resp.statusCode() == 200) {
                  try {
                    JsonObject body = resp.bodyAsJsonObject();
                    Set<String> tenantIds =
                        body.getJsonArray("entitlements").stream()
                            .map(o -> ((JsonObject) o).getString("tenantId"))
                            .collect(Collectors.toSet());
                    return Future.succeededFuture(tenantIds);
                  } catch (Exception e) {
                    return Future.failedFuture(String.format(ERR_MSG_DECODE, url, e.getMessage()));
                  }
                } else {
                  return Future.failedFuture(
                      String.format(
                          ERR_MSG_STATUS_WITH_URL, resp.statusCode(), resp.statusMessage(), url));
                }
              } else {
                return Future.failedFuture(
                    "Failed getting entitlements: " + ar.cause().getMessage());
              }
            });
  }

  private Future<List<String>> resolveTenantsById(Set<String> tenantIds) {
    String url = tmClientUrl + PATH_TENANTS;
    return client
        .getAbs(url)
        .addQueryParam("limit", "500")
        .send()
        .transform(
            ar -> {
              if (ar.succeeded()) {
                HttpResponse<?> resp = ar.result();
                if (resp.statusCode() == 200) {
                  try {
                    JsonObject body = resp.bodyAsJsonObject();
                    List<String> tenants =
                        body.getJsonArray("tenants").stream()
                            .map(o -> (JsonObject) o)
                            .filter(o -> tenantIds.contains(o.getString("id")))
                            .map(o -> o.getString("name"))
                            .toList();
                    return Future.succeededFuture(tenants);
                  } catch (Exception e) {
                    return Future.failedFuture(String.format(ERR_MSG_DECODE, url, e.getMessage()));
                  }
                } else {
                  return Future.failedFuture(
                      String.format(
                          ERR_MSG_STATUS_WITH_URL, resp.statusCode(), resp.statusMessage(), url));
                }
              } else {
                return Future.failedFuture("Failed getting tenants: " + ar.cause().getMessage());
              }
            });
  }
}
