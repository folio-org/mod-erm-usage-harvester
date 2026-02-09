package org.olf.erm.usage.harvester.endpoints;

import org.folio.rest.jaxrs.model.AggregatorSetting;
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
  public ServiceEndpoint create(UsageDataProvider provider, AggregatorSetting aggregator) {
    return new TestProviderImpl();
  }
}
