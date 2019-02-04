package org.olf.erm.usage.harvester.endpoints;

import java.util.Collections;
import java.util.List;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import io.vertx.core.json.JsonObject;

public interface ServiceEndpointProvider {

  String getServiceType();

  String getServiceName();

  default String getServiceDescription() {
    return "";
  }

  default Boolean isAggregator() {
    return false;
  }

  ServiceEndpoint create(UsageDataProvider provider, AggregatorSetting aggregator);

  default List<String> getConfigurationParameters() {
    return Collections.emptyList();
  }

  default JsonObject toJson() {
    JsonObject result =
        new JsonObject()
            .put("name", getServiceName())
            .put("description", getServiceDescription())
            .put("type", getServiceType())
            .put("isAggregator", isAggregator());
    if (!getConfigurationParameters().isEmpty())
      result.put("configurationParameters", getConfigurationParameters());
    return result;
  }
}
