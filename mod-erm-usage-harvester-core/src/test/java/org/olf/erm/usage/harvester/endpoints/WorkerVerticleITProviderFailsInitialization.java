package org.olf.erm.usage.harvester.endpoints;

import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.UsageDataProvider;

public class WorkerVerticleITProviderFailsInitialization implements ServiceEndpointProvider {

  @Override
  public String getServiceType() {
    return "wvitpfailinit";
  }

  @Override
  public String getServiceName() {
    return "WorkerVerticleITProviderFailsInitialization";
  }

  @Override
  public String getServiceDescription() {
    return "Test Provider fails to initialize";
  }

  @Override
  public ServiceEndpoint create(UsageDataProvider provider, AggregatorSetting aggregator) {
    throw new RuntimeException("Initialization error");
  }
}
