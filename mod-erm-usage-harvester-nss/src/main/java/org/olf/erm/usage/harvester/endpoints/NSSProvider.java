package org.olf.erm.usage.harvester.endpoints;

import java.util.Arrays;
import java.util.List;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.UsageDataProvider;

public class NSSProvider implements ServiceEndpointProvider {

  @Override
  public String getServiceType() {
    return "NSS";
  }

  @Override
  public String getServiceName() {
    return "Nationaler Statistikserver";
  }

  @Override
  public String getServiceDescription() {
    return "Implementation for Germanys National Statistics Server (https://sushi.redi-bw.de).";
  }

  @Override
  public Boolean isAggregator() {
    return true;
  }

  @Override
  public List<String> getConfigurationParameters() {
    return Arrays.asList("apiKey", "requestorId", "customerId", "reportRelease");
  }

  @Override
  public ServiceEndpoint create(UsageDataProvider provider, AggregatorSetting aggregator) {
    return new NSS(provider, aggregator);
  }

}
