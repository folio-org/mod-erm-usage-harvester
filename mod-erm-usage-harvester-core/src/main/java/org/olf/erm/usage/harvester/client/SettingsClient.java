package org.olf.erm.usage.harvester.client;

import io.vertx.core.Future;
import java.util.Optional;

public interface SettingsClient {

  /**
   * Retrieves a value from the settings API thats associated with the specified scope and key.
   *
   * @param scope the scope within which the key is defined
   * @param key the key for which the value is to be retrieved
   * @return a Future containing an Optional with the value if present, or an empty Optional if not
   */
  Future<Optional<Object>> getValue(String scope, String key);
}
