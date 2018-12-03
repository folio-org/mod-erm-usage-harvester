package org.olf.erm.usage.harvester;

import java.util.List;
import org.apache.log4j.Logger;
import org.folio.okapi.common.XOkapiHeaders;
import com.google.common.base.Strings;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
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
              return okapiClient.getAuthToken(t, "harvester", "harvester", "ermusage.all");
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
        return okapiClient.getAuthToken(tenantId, "harvester", "harvester", "ermusage.all");
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
        h.response().setStatusCode(500).end(msg);
      } else {
        String msg = "Processing of tenant " + tenantId + " requested.";
        LOG.info(msg);
        processSingleTenant(tenantId);
        h.response().setStatusCode(200).end(msg);
      }
    });
    return router;
  }

  @Override
  public void start() throws Exception {
    if (config().getBoolean("testing", false)) {
      return;
    }

    int port = config().getInteger("http.port", 8081);
    vertx.createHttpServer().requestHandler(createRouter()::accept).listen(port, h -> {
      if (h.failed()) {
        LOG.error("Unable to start HttpServer");
      }
    });

    // TODO: do this periodically
    processAllTenants();

    vertx.setPeriodic(5000, h -> {
      System.out.println("Deployed Verticles: " + vertx.deploymentIDs());
    });
  }
}
