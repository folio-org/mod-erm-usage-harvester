package org.olf.erm.usage.harvester;

import static org.olf.erm.usage.harvester.Messages.ERR_MSG_DECODE;
import static org.olf.erm.usage.harvester.Messages.ERR_MSG_STATUS;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.log4j.Logger;
import org.folio.okapi.common.XOkapiHeaders;
import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

public class OkapiClient {

  private static final Logger LOG = Logger.getLogger(OkapiClient.class);

  private final String okapiUrl;
  private final String tenantsPath;
  private final String loginPath;
  private final List<String> moduleIds;
  private final Vertx vertx;

  public OkapiClient(Vertx vertx, JsonObject cfg) {
    // TODO: check for null values
    this.okapiUrl = cfg.getString("okapiUrl");
    this.tenantsPath = cfg.getString("tenantsPath");
    this.loginPath = cfg.getString("loginPath");
    this.moduleIds =
        cfg.getJsonArray("moduleIds").stream().map(Object::toString).collect(Collectors.toList());
    this.vertx = vertx;
  }

  public Future<List<String>> getTenants() {
    Future<List<String>> future = Future.future();

    final String url = okapiUrl + tenantsPath;
    WebClient client = WebClient.create(vertx);
    client.getAbs(url).send(ar -> {
      client.close();
      if (ar.succeeded()) {
        if (ar.result().statusCode() == 200) {
          JsonArray jsonArray;
          try {
            jsonArray = ar.result().bodyAsJsonArray();
            List<String> tenants = jsonArray.stream()
                .map(o -> ((JsonObject) o).getString("id"))
                .collect(Collectors.toList());
            LOG.info("Found tenants: " + tenants);
            future.complete(tenants);
          } catch (Exception e) {
            future.fail(String.format(ERR_MSG_DECODE, url, e.getMessage()));
          }
        } else {
          future.fail(String.format(ERR_MSG_STATUS, ar.result().statusCode(),
              ar.result().statusMessage(), url));
        }
      } else {
        future.fail(ar.cause());
      }
    });
    return future;
  }

  Future<Boolean> hasEnabledUsageModules(String tenantId) {
    final String logprefix = "Tenant: " + tenantId + ", ";
    final String modulesUrl = okapiUrl + tenantsPath + "/" + tenantId + "/modules";

    Future<Boolean> future = Future.future();
    WebClient client = WebClient.create(vertx);
    client.getAbs(modulesUrl).send(ar -> {
      client.close();
      if (ar.succeeded()) {
        if (ar.result().statusCode() == 200) {
          try {
            List<String> modules = ar.result()
                .bodyAsJsonArray()
                .stream()
                .map(o -> ((JsonObject) o).getString("id"))
                .collect(Collectors.toList());
            future.complete(modules.containsAll(moduleIds));
          } catch (Exception e) {
            future.fail(logprefix + String.format(ERR_MSG_DECODE, modulesUrl, e.getMessage()));
          }
        } else if (ar.result().statusCode() == 404) {
          future.complete(false);
        } else {
          future.fail(logprefix + String.format(ERR_MSG_STATUS, ar.result().statusCode(),
              ar.result().statusMessage(), modulesUrl));
        }
      } else {
        future.fail(ar.cause());
      }
    });
    return future;
  }

  public Future<Token> getAuthToken(String tenantId, String username, String password,
      String requiredPerm) {
    JsonObject userCred = new JsonObject().put("username", username).put("password", password);
    WebClient client = WebClient.create(vertx);
    Future<Token> future = Future.future();
    client.postAbs(okapiUrl + loginPath)
        .addQueryParam("expandPermissions", "false")
        .addQueryParam("fullPermissions", "false")
        .putHeader(XOkapiHeaders.TENANT, tenantId)
        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString())
        .putHeader(HttpHeaders.ACCEPT, MediaType.JSON_UTF_8.toString())
        .sendJson(userCred, h -> {
          if (h.succeeded()) {
            String token = h.result().getHeader(XOkapiHeaders.TOKEN);
            if (h.result().statusCode() != 201) {
              future.fail("Could not authenticate for Tenant " + tenantId + ": "
                  + h.result().statusCode() + " " + h.result().statusMessage() + " "
                  + h.result().bodyAsJsonObject().getString("errorMessage"));
            } else if (Strings.isNullOrEmpty(token)) {
              future.fail("No token received: " + h.result().statusCode() + " "
                  + h.result().statusMessage());
            } else if (h.result()
                .bodyAsJsonObject()
                .getJsonArray("permissions.permissions", new JsonArray())
                .contains(requiredPerm)) {
              future.fail("Required permission not found");
            } else {
              future.complete(new Token(token));
            }
          } else {
            future.fail(h.cause());
          }
        });
    return future;
  }
}
