package org.olf.erm.usage.harvester.client;

import io.vertx.core.Future;
import java.util.List;

public interface OkapiClient {

  Future<List<String>> getTenants();
}
