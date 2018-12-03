package org.olf.erm.usage.harvester.util;

import org.apache.log4j.Logger;
import org.folio.rest.jaxrs.model.CounterReports;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.WebClient;

public class Utils {

  private static final String url = "http://localhost:8081/counter-reports";
  private static final Logger LOG = Logger.getLogger(Utils.class);

  public static void clearCounterReports(int i) {
    WebClient client = WebClient.create(Vertx.vertx());
    client.requestAbs(HttpMethod.GET, url)
        .putHeader("x-okapi-tenant", "diku")
        .putHeader("accept", "application/json")
        .addQueryParam("limit", String.valueOf(i))
        .send(ar -> {
          if (ar.succeeded()) {
            CounterReports reportsCollection = ar.result().bodyAsJson(CounterReports.class);
            reportsCollection.getCounterReports()
                .forEach(r -> client.requestAbs(HttpMethod.DELETE, url + "/" + r.getId())
                    .putHeader("x-okapi-tenant", "diku")
                    .putHeader("accept", "text/plain")
                    .send(ar2 -> {
                      if (ar2.succeeded()) {
                        LOG.info(String.format("%s: %s, %s", r.getId(), ar2.result().statusCode(),
                            ar2.result().statusMessage()));
                      } else {
                        LOG.error(ar.cause());
                      }
                    }));
          } else {
            LOG.error(ar.cause());
          }
        });
  }

  public static void main(String[] args) {
    clearCounterReports(100);
  }

}
