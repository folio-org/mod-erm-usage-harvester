package org.olf.erm.usage.harvester.client;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.folio.rest.jaxrs.model.Aggregator;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.HarvestingConfig.HarvestVia;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceEndpointFactory {

  private static final Logger log = LoggerFactory.getLogger(ServiceEndpointFactory.class);
  private final ExtAggregatorSettingsClient aggregatorSettingsClient;

  public ServiceEndpointFactory(ExtAggregatorSettingsClient aggregatorSettingsClient) {
    this.aggregatorSettingsClient = aggregatorSettingsClient;
  }

  public Future<ServiceEndpoint> createServiceEndpoint(UsageDataProvider usageDataProvider) {
    Promise<AggregatorSetting> aggrPromise = Promise.promise();
    Promise<ServiceEndpoint> sepPromise = Promise.promise();

    boolean useAggregator =
        usageDataProvider.getHarvestingConfig().getHarvestVia().equals(HarvestVia.AGGREGATOR);
    Aggregator aggregator = usageDataProvider.getHarvestingConfig().getAggregator();
    // Complete aggrPromise if aggregator is not set.. aka skip it
    if (useAggregator && aggregator != null && aggregator.getId() != null) {
      aggregatorSettingsClient
          .getAggregatorSetting(usageDataProvider)
          .onSuccess(result -> log.info("got AggregatorSetting for id: {}", result.getId()))
          .onComplete(aggrPromise);
    } else {
      aggrPromise.complete(null);
    }

    return aggrPromise
        .future()
        .compose(
            as -> {
              ServiceEndpoint sep = ServiceEndpoint.create(usageDataProvider, as);
              if (sep != null) {
                sepPromise.complete(sep);
              } else {
                sepPromise.fail("No service implementation available");
              }
              return sepPromise.future();
            });
  }
}
