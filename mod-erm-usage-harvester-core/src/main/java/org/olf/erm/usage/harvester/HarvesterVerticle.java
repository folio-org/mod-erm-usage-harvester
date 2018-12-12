package org.olf.erm.usage.harvester;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.log4j.Logger;
import org.folio.okapi.common.XOkapiHeaders;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpointProvider;
import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class HarvesterVerticle extends AbstractVerticle {

  private static final Logger LOG = Logger.getLogger(HarvesterVerticle.class);

  public void processAllTenants() {
    OkapiClient okapiClient = new OkapiClient(vertx, config());
    okapiClient.getTenants().setHandler(ar -> {
      if (ar.succeeded()) {
        List<String> tenantList = ar.result();
        tenantList.forEach(t -> {
          okapiClient.hasEnabledUsageModules(t).compose(en -> {
            if (en) {
              return okapiClient.getAuthToken(t, "diku_admin", "admin", "ermusage.all");
            } else {
              return Future.failedFuture("Module not enabled for Tenant " + t);
            }
          }).setHandler(h -> {
            if (h.succeeded()) {
              // deploy WorkerVerticle for tenant
              System.out.println(config().encodePrettily());
              vertx.deployVerticle(new WorkerVerticle(h.result()),
                  new DeploymentOptions().setConfig(config()));
            } else {
              LOG.error(h.cause().getMessage());
            }
          });
        });
      } else {
        LOG.error("Failed to get tenants: " + ar.cause().getMessage());
      }
    });
  }

  public void processSingleTenant(String tenantId) {
    OkapiClient okapiClient = new OkapiClient(vertx, config());
    okapiClient.hasEnabledUsageModules(tenantId).compose(en -> {
      if (en) {
        return okapiClient.getAuthToken(tenantId, "diku_admin", "admin", "ermusage.all");
      } else {
        return Future.failedFuture("Module not enabled for Tenant " + tenantId);
      }
    }).setHandler(h -> {
      if (h.succeeded()) {
        // deploy WorkerVerticle for tenant
        vertx.deployVerticle(new WorkerVerticle(h.result()),
            new DeploymentOptions().setConfig(config()));
      } else {
        LOG.error(h.cause().getMessage());
      }
    });
  }

  public Router createRouter() {
    Router router = Router.router(vertx);
    router.route("/harvester/start").handler(h -> {
      String tenantId = h.request().getHeader(XOkapiHeaders.TENANT);
      if (Strings.isNullOrEmpty(tenantId)) {
        String msg = "No " + XOkapiHeaders.TENANT + " header present.";
        LOG.error(msg);
        h.response().setStatusCode(403).end(msg);
      } else {
        String msg = "Processing of tenant " + tenantId + " requested.";
        LOG.info(msg);
        processSingleTenant(tenantId);
        h.response()
            .setStatusCode(200)
            .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString())
            .end(new JsonObject().put("message", msg).toString());
      }
    });
    router.route("/harvester/impl").handler(h -> {
      String param = h.queryParams().get("aggregator");
      Boolean paramValue = Boolean.valueOf(param);

      List<JsonObject> collect = ServiceEndpoint.getAvailableProviders()
          .stream()
          .filter(provider -> param == null
              || (param != null && provider.isAggregator().equals(paramValue)))
          .sorted(Comparator.comparing(ServiceEndpointProvider::getServiceName))
          .map(ServiceEndpointProvider::toJson)
          .collect(Collectors.toList());
      h.response()
          .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString())
          .setStatusCode(200)
          .end(new JsonObject().put("implementations", new JsonArray(collect)).toString());
    });
    return router;
  }

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    int port = config().getInteger("http.port", 8081);
    vertx.createHttpServer().requestHandler(createRouter()::accept).listen(port, h -> {
      if (h.failed()) {
        startFuture.fail("Unable to start HttpServer on port " + port);
      } else {
        // TODO: do this periodically
        if (!config().getBoolean("testing", false)) {
          processAllTenants();
        } else {
          LOG.info("TEST ENV");
        }

        startFuture.complete();
      }
    });

    // vertx.setPeriodic(5000, h2 -> LOG.info("Deployed Verticles: " + vertx.deploymentIDs()));
  }
}
