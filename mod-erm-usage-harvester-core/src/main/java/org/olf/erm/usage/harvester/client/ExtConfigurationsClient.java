package org.olf.erm.usage.harvester.client;

import io.vertx.core.Future;

public interface ExtConfigurationsClient {

  Future<String> getModConfigurationValue(String module, String configName);
}
