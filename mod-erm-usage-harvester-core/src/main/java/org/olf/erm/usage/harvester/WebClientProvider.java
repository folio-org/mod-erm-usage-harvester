package org.olf.erm.usage.harvester;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Provides cached WebClient instances per Vertx instance for internal Okapi communication.
 *
 * <p>WebClient is thread-safe and should be reused. This class ensures exactly one instance exists
 * per Vertx instance.
 *
 * <p><strong>Lifecycle Management:</strong> This class uses weak references to Vertx instances.
 * When a Vertx instance is closed and garbage collected, the associated WebClient becomes eligible
 * for garbage collection automatically.
 *
 * <p><strong>Thread Safety:</strong> All methods are thread-safe and can be called from any thread.
 */
public final class WebClientProvider {

  private static final Map<Vertx, WebClient> CLIENTS =
      Collections.synchronizedMap(new WeakHashMap<>());

  private WebClientProvider() {}

  /**
   * Returns the shared WebClient for internal Okapi communication.
   *
   * @param vertx the Vertx instance to associate with the client
   * @return the shared WebClient for the given Vertx instance
   * @throws NullPointerException if vertx is null
   */
  public static WebClient get(Vertx vertx) {
    Objects.requireNonNull(vertx, "vertx must not be null");
    return CLIENTS.computeIfAbsent(vertx, WebClient::create);
  }
}
