package org.olf.erm.usage.harvester;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides cached WebClient instances per Vertx instance for internal Okapi communication.
 *
 * <p>WebClient is thread-safe and should be reused. This class ensures exactly one instance exists
 * per Vertx instance.
 *
 * <p><strong>Lifecycle:</strong> Cached clients are never evicted or closed; each one lives for the
 * lifetime of the JVM, even after its Vertx instance is closed. This suits the module's single
 * launcher-created Vertx instance. Code that repeatedly creates and closes Vertx instances in one
 * JVM, such as tests, accumulates one cached client per instance, each keeping its closed Vertx
 * instance reachable.
 *
 * <p><strong>Thread Safety:</strong> All methods are thread-safe and can be called from any thread.
 */
public final class WebClientProvider {

  // bounds connections without traffic so that callers blocking on a response cannot
  // stall indefinitely on an unresponsive service
  static final int IDLE_TIMEOUT_SECONDS = 60;

  private static final Map<Vertx, WebClient> CLIENTS = new ConcurrentHashMap<>();

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
    return CLIENTS.computeIfAbsent(
        vertx,
        v -> WebClient.create(v, new WebClientOptions().setIdleTimeout(IDLE_TIMEOUT_SECONDS)));
  }
}
