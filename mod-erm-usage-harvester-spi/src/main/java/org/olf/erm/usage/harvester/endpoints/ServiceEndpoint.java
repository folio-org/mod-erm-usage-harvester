package org.olf.erm.usage.harvester.endpoints;

import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import io.vertx.core.Future;

public interface ServiceEndpoint {

  boolean isValidReport(String report);

  Future<String> fetchSingleReport(String report, String beginDate, String endDate);

  public static List<ServiceEndpointProvider> getAvailableProviders() {
    ServiceLoader<ServiceEndpointProvider> loader =
        ServiceLoader.load(ServiceEndpointProvider.class);
    return Lists.newArrayList(loader.iterator());
  }

  public static ServiceEndpoint create(UsageDataProvider provider, AggregatorSetting aggregator) {
    Objects.requireNonNull(provider);

    final Logger LOG = LoggerFactory.getLogger(ServiceEndpoint.class);

    String serviceType =
        (aggregator == null)
            ? provider.getHarvestingConfig().getSushiConfig().getServiceType()
            : aggregator.getServiceType();

    if (Strings.isNullOrEmpty(serviceType)) {
      LOG.error("serviceType is null or empty");
      return null;
    }

    ServiceLoader<ServiceEndpointProvider> loader =
        ServiceLoader.load(ServiceEndpointProvider.class);
    for (ServiceEndpointProvider p : loader) {
      if (p.getServiceType().equals(serviceType)) {
        return p.create(provider, aggregator);
      }
    }

    LOG.error("No implementation found for serviceType '" + serviceType + "'");
    return null;
  }
}
