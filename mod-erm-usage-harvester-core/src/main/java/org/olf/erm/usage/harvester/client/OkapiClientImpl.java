package org.olf.erm.usage.harvester.client;

import static io.vertx.core.Future.succeededFuture;
import static org.olf.erm.usage.harvester.Messages.ERR_MSG_DECODE;
import static org.olf.erm.usage.harvester.Messages.ERR_MSG_STATUS_WITH_URL;

import com.google.common.net.HttpHeaders;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.ws.rs.core.MediaType;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.tools.utils.VertxUtils;
import org.olf.erm.usage.harvester.SystemUser;

public class OkapiClientImpl implements OkapiClient {

  public static final String PATH_LOGIN = "/authn/login"; // NOSONAR
  public static final String PATH_HARVESTER_START = "/erm-usage-harvester/start"; // NOSONAR
  public static final String PATH_TENANTS = "/_/proxy/tenants"; // NOSONAR
  private final String okapiUrl;
  private final WebClient client;

  public OkapiClientImpl(WebClient webClient, JsonObject cfg) {
    Objects.requireNonNull(cfg);
    this.okapiUrl = cfg.getString("okapiUrl");
    this.client = webClient;
  }

  public OkapiClientImpl(WebClient webClient, String okapiUrl) {
    Objects.requireNonNull(okapiUrl);
    this.okapiUrl = okapiUrl;
    this.client = webClient;
  }

  public OkapiClientImpl(String okapiUrl) {
    this(WebClient.create(VertxUtils.getVertxFromContextOrNew()), okapiUrl);
  }

  @Override
  public Future<String> loginSystemUser(String tenantId, SystemUser systemUser) {
    String loginUrl = okapiUrl + PATH_LOGIN;

    return client
        .postAbs(loginUrl)
        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
        .putHeader(XOkapiHeaders.TENANT, tenantId)
        .sendJson(systemUser.toJsonObject())
        .compose(
            resp -> {
              if (resp.statusCode() == 201) {
                return succeededFuture(resp.headers().get(XOkapiHeaders.TOKEN));
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

  @Override
  public Future<HttpResponse<Buffer>> startHarvester(String tenantId, String token) {
    String startUrl = okapiUrl + PATH_HARVESTER_START;
    return client
        .getAbs(startUrl)
        .putHeader(XOkapiHeaders.TENANT, tenantId)
        .putHeader(XOkapiHeaders.TOKEN, token)
        .send();
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
                        jsonArray.stream()
                            .map(o -> ((JsonObject) o).getString("id"))
                            .collect(Collectors.toList());
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

  public Future<HttpResponse<Buffer>> sendRequest(
      HttpMethod method, String path, String tenantId, String token) {
    String uri = okapiUrl + path;
    return client
        .requestAbs(method, uri)
        .putHeader(XOkapiHeaders.TENANT, tenantId)
        .putHeader(XOkapiHeaders.TOKEN, token)
        .send();
  }
}
