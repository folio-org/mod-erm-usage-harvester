package org.olf.erm.usage.harvester;

import static org.olf.erm.usage.harvester.Messages.ERR_MSG_DECODE;
import static org.olf.erm.usage.harvester.Messages.ERR_MSG_STATUS_WITH_URL;

import com.google.common.net.HttpHeaders;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.ws.rs.core.MediaType;
import org.folio.okapi.common.XOkapiHeaders;

public class OkapiClient {

  private final String okapiUrl;
  private final String tenantsPath;
  private final WebClient client;

  public OkapiClient(WebClient webClient, JsonObject cfg) {
    Objects.requireNonNull(cfg);
    this.okapiUrl = cfg.getString("okapiUrl");
    this.tenantsPath = cfg.getString("tenantsPath");
    this.client = webClient;
  }

  public Future<String> loginSystemUser(String tenantId, SystemUser systemUser) {
    String loginUrl = okapiUrl + "/authn/login";

    return client
        .postAbs(loginUrl)
        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
        .putHeader(XOkapiHeaders.TENANT, tenantId)
        .sendJson(systemUser.toJsonObject())
        .compose(
            resp -> {
              if (resp.statusCode() == 201) {
                return Future.succeededFuture(resp.headers().get(XOkapiHeaders.TOKEN));
              } else {
                return Future.failedFuture(
                    String.format(
                        ERR_MSG_STATUS_WITH_URL,
                        resp.statusCode(),
                        resp.statusMessage(),
                        loginUrl));
              }
            });
  }

  public Future<HttpResponse<Buffer>> startHarvester(String tenantId, String token) {
    String startUrl = okapiUrl + "/erm-usage-harvester/start";
    return client
        .getAbs(startUrl)
        .putHeader(XOkapiHeaders.TENANT, tenantId)
        .putHeader(XOkapiHeaders.TOKEN, token)
        .send();
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
                    promise.complete(tenants);
                  } catch (Exception e) {
                    promise.fail(String.format(ERR_MSG_DECODE, url, e.getMessage()));
                  }
                } else {
                  promise.fail(
                      String.format(
                          ERR_MSG_STATUS_WITH_URL,
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
