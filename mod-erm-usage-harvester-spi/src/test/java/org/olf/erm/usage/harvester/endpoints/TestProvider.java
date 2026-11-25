package org.olf.erm.usage.harvester.endpoints;

import io.vertx.core.Vertx;
import org.folio.rest.jaxrs.model.UsageDataProvider;

public class TestProvider implements ServiceEndpointProvider {

  @Override
  public String getServiceType() {
    return "TestProviderType";
  }

  @Override
  public String getServiceName() {
    return "TestProviderName";
  }

  @Override
  public ServiceEndpoint create(Vertx vertx, UsageDataProvider provider) {
    return new TestProviderImpl();
  }
}
