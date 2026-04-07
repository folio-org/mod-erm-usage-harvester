package org.olf.erm.usage.harvester.client;

import io.vertx.core.Future;
import java.util.List;

public interface MgrClient {

  Future<List<String>> getEntitledTenantNames();
}
