package org.olf.erm.usage.harvester.endpoints;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.Collections;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.UsageDataProvider;

public class Test2Provider implements ServiceEndpointProvider {

  @Override
  public Boolean isAggregator() {
    return true;
  }

  @Override
  public String getServiceType() {
    return "test2";
  }

  @Override
  public String getServiceName() {
    return "test2";
  }

  @Override
  public ServiceEndpoint create(
      UsageDataProvider provider, AggregatorSetting aggregator, Vertx vertx) {
    return (report, beginDate, endDate) -> Future.succeededFuture(Collections.emptyList());
  }
}
