package org.olf.erm.usage.harvester.client;

import static java.lang.Boolean.TRUE;
import static java.lang.Boolean.parseBoolean;
import static org.olf.erm.usage.harvester.Messages.ERR_MSG_DECODE;
import static org.olf.erm.usage.harvester.Messages.ERR_MSG_STATUS;
import static org.olf.erm.usage.harvester.Messages.ERR_MSG_STATUS_WITH_URL;

import com.google.common.net.HttpHeaders;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.ws.rs.core.MediaType;
import org.folio.okapi.common.XOkapiHeaders;
import org.olf.erm.usage.harvester.SystemUser;

public class OkapiClientImpl implements OkapiClient {

  public static final String PATH_LOGIN = "/authn/login"; 
  public static final String PATH_LOGIN_EXPIRY = "/authn/login-with-expiry"; 
  public static final String PATH_HARVESTER_START = "/erm-usage-harvester/start"; 
  public static final String PATH_TENANTS = "/_/proxy/tenants"; 
  public static final String MSG_SYSTEM_USER_LOGIN_DISABLED = "System User Login is Disabled";

  private static final String ENV_SYSTEM_USER_ENABLED = "SYSTEM_USER_ENABLED";

  private final String okapiUrl;
  private final WebClient client;
  private final boolean isSystemUserEnabled;

  public OkapiClientImpl(WebClient webClient, JsonObject cfg) {
    Objects.requireNonNull(cfg);
    this.okapiUrl = cfg.getString("okapiUrl");
    this.client = webClient;
    this.isSystemUserEnabled = isSystemUserEnabled();
  }

  public OkapiClientImpl(WebClient webClient, String okapiUrl, boolean isSystemUserEnabled) {
    Objects.requireNonNull(okapiUrl);
    this.okapiUrl = okapiUrl;
    this.client = webClient;
    this.isSystemUserEnabled = isSystemUserEnabled;
  }

  private HttpResponse<Buffer> throwIfStatusCodeNot201(HttpResponse<Buffer> response) {
    if (response.statusCode() != 201) {
      throw new OkapiClientException(
          String.format(
              "Error logging in with system user: " + ERR_MSG_STATUS,
              response.statusCode(),
              response.statusMessage()));
    }
    return response;
  }

  private String getTokenFromResponse(HttpResponse<Buffer> response) {
    return Optional.ofNullable(
            response.cookies().stream()
                .filter(s -> s.startsWith("folioAccessToken"))
                .findFirst()
                .map(
                    cookie -> {
                      try {
                        return cookie.split(";", 2)[0].split("=", 2)[1];
                      } catch (Exception e) {
                        return null;
                      }
                    })
                .orElse(response.getHeader(XOkapiHeaders.TOKEN)))
        .orElseThrow(() -> new OkapiClientException("Unable to extract token from login response"));
  }

  @Override
  public Future<String> loginSystemUser(String tenantId, SystemUser systemUser) {
    if (!isSystemUserEnabled) {
      return Future.failedFuture(MSG_SYSTEM_USER_LOGIN_DISABLED);
    }

    String loginUrl = okapiUrl + PATH_LOGIN;
    String loginWithExpiryUrl = okapiUrl + PATH_LOGIN_EXPIRY;

    MultiMap headers =
        MultiMap.caseInsensitiveMultiMap()
            .add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .add(XOkapiHeaders.TENANT, tenantId);

    return client
        .postAbs(loginWithExpiryUrl)
        .putHeaders(headers)
        .sendJson(systemUser.toJsonObject())
        .map(this::throwIfStatusCodeNot201)
        .recover(
            t ->
                client
                    .postAbs(loginUrl)
                    .putHeaders(headers)
                    .sendJson(systemUser.toJsonObject())
                    .map(this::throwIfStatusCodeNot201))
        .map(this::getTokenFromResponse);
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

  public Future<HttpResponse<Buffer>> sendRequest(
      HttpMethod method, String path, String tenantId, String token) {
    String uri = okapiUrl + path;
    return client
        .requestAbs(method, uri)
        .putHeader(XOkapiHeaders.TENANT, tenantId)
        .putHeader(XOkapiHeaders.TOKEN, token)
        .send();
  }

  private static class OkapiClientException extends RuntimeException {
    public OkapiClientException(String message) {
      super(message);
    }
  }

  private static boolean isSystemUserEnabled() {
    return parseBoolean(Objects.toString(System.getenv(ENV_SYSTEM_USER_ENABLED), TRUE.toString()));
  }
}
