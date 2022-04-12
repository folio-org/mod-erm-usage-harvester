package org.olf.erm.usage.harvester.client;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.UsageDataProvider;

public interface ExtAggregatorSettingsClient {

  Future<AggregatorSetting> getAggregatorSetting(UsageDataProvider provider);
}
