package org.olf.erm.usage.harvester.endpoints;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.Collections;
import java.util.List;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.UsageDataProvider;

public class Test2Provider implements ServiceEndpointProvider {

  @Override
  public String getServiceType() {
    return "test2";
  }

  @Override
  public String getServiceName() {
    return "test2";
  }

  @Override
  public ServiceEndpoint create(Vertx vertx, UsageDataProvider provider) {
    return new ServiceEndpoint() {
      @Override
      public Future<List<CounterReport>> fetchReport(
          String report, String beginDate, String endDate) {
        return Future.succeededFuture(Collections.emptyList());
      }

      @Override
      public void close() {
        // nothing to release
      }
    };
  }
}
