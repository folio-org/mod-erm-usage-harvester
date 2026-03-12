package org.olf.erm.usage.harvester.endpoints;

import java.util.List;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.UsageDataProvider;

public class CS41Provider implements ServiceEndpointProvider {

  @Override
  public String getServiceType() {
    return "cs41";
  }

  @Override
  public String getServiceName() {
    return "Counter-Sushi 4.1";
  }

  @Override
  public String getServiceDescription() {
    return "SOAP-based implementation for CounterSushi 4.1";
  }

  @Override
  public Boolean isAggregator() {
    return false;
  }

  @Override
  public String getReportRelease() {
    return "4";
  }

  @Override
  public List<String> getSupportedReports() {
    return List.of(
        "BR1",
        "BR2",
        "BR3",
        "BR4",
        "BR5",
        "BR7",
        "DB1",
        "DB2",
        "JR1",
        "JR1 GOA",
        "JR1a",
        "JR2",
        "JR3",
        "JR3 Mobile",
        "JR4",
        "JR5",
        "MR1",
        "MR1 Mobile",
        "PR1",
        "TR1",
        "TR2",
        "TR3",
        "TR3 Mobile");
  }

  @Override
  public ServiceEndpoint create(UsageDataProvider provider, AggregatorSetting aggregator) {
    return new CS41Impl(provider);
  }
}
