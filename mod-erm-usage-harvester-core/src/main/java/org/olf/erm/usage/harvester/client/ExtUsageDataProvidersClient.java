package org.olf.erm.usage.harvester.client;

import io.vertx.core.Future;
import java.util.Date;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.folio.rest.jaxrs.model.UsageDataProviders;

public interface ExtUsageDataProvidersClient {

  Future<Void> updateUDPLastHarvestingDate(UsageDataProvider udp, Date date);

  Future<UsageDataProviders> getActiveProviders();

  Future<UsageDataProvider> getActiveProviderById(String providerId);
}
