package org.olf.erm.usage.harvester.endpoints;

import io.vertx.core.Vertx;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides singleton WebClient instances per Vertx instance with proper lifecycle management.
 *
 * <p>WebClient is thread-safe and should be reused. This class ensures exactly two instances exist
 * per Vertx instance:
 *
 * <ul>
 *   <li>{@link #internal(Vertx)}: for Okapi communication (no proxy)
 *   <li>{@link #external(Vertx)}: for SUSHI endpoints (with proxy if configured)
 * </ul>
 *
 * <p><strong>Lifecycle Management:</strong> This class uses weak references to Vertx instances.
 * When a Vertx instance is closed and garbage collected, the associated WebClients become eligible
 * for garbage collection automatically. This prevents memory leaks in long-running applications.
 *
 * <p><strong>Thread Safety:</strong> All methods are thread-safe and can be called from any thread.
 */
public final class WebClients {

  private static final Logger LOG = LoggerFactory.getLogger(WebClients.class);

  /** Idle timeout in seconds for external SUSHI connections. */
  public static final int EXTERNAL_IDLE_TIMEOUT_SECONDS = 60;

  /** System property to override the URI used for proxy detection. */
  public static final String PROXY_DETECTION_URI_PROPERTY = "harvester.proxy.detection.uri";

  /** Default URI used for proxy detection (IANA reserved domain). */
  public static final String DEFAULT_PROXY_DETECTION_URI = "https://example.com";

  private static final Map<Vertx, WebClient> INTERNAL_CLIENTS =
      Collections.synchronizedMap(new WeakHashMap<>());
  private static final Map<Vertx, WebClient> EXTERNAL_CLIENTS =
      Collections.synchronizedMap(new WeakHashMap<>());

  private WebClients() {}

  /**
   * Closes and clears all cached clients. For testing only.
   *
   * <p>This method should only be used in tests to reset state between test cases. In production,
   * clients are automatically cleaned up when Vertx instances are garbage collected.
   */
  public static void reset() {
    INTERNAL_CLIENTS.values().forEach(WebClient::close);
    EXTERNAL_CLIENTS.values().forEach(WebClient::close);
    INTERNAL_CLIENTS.clear();
    EXTERNAL_CLIENTS.clear();
  }

  /**
   * Returns the shared WebClient for internal Okapi communication.
   *
   * <p>This client has no proxy settings applied, as Okapi communication is typically within the
   * same network.
   *
   * @param vertx the Vertx instance to associate with the client
   * @return the shared internal WebClient for the given Vertx instance
   * @throws NullPointerException if vertx is null
   */
  public static WebClient internal(Vertx vertx) {
    Objects.requireNonNull(vertx, "vertx must not be null");
    return INTERNAL_CLIENTS.computeIfAbsent(vertx, WebClients::createInternalClient);
  }

  /**
   * Returns the shared WebClient for external SUSHI endpoints.
   *
   * <p>This client includes:
   *
   * <ul>
   *   <li>System proxy settings (if configured via JVM proxy properties)
   *   <li>60 second idle timeout to handle slow SUSHI endpoints
   * </ul>
   *
   * @param vertx the Vertx instance to associate with the client
   * @return the shared external WebClient for the given Vertx instance
   * @throws NullPointerException if vertx is null
   */
  public static WebClient external(Vertx vertx) {
    Objects.requireNonNull(vertx, "vertx must not be null");
    return EXTERNAL_CLIENTS.computeIfAbsent(vertx, WebClients::createExternalClient);
  }

  private static WebClient createInternalClient(Vertx vertx) {
    return WebClient.create(vertx);
  }

  private static WebClient createExternalClient(Vertx vertx) {
    WebClientOptions options = new WebClientOptions().setIdleTimeout(EXTERNAL_IDLE_TIMEOUT_SECONDS);
    getProxyOptions().ifPresent(options::setProxyOptions);
    return WebClient.create(vertx, options);
  }

  /**
   * Detects system proxy settings using Java's ProxySelector.
   *
   * @return ProxyOptions if a system proxy is configured, empty otherwise
   */
  static Optional<ProxyOptions> getProxyOptions() {
    try {
      ProxySelector proxySelector = ProxySelector.getDefault();
      if (proxySelector == null) {
        LOG.debug("No system ProxySelector configured");
        return Optional.empty();
      }
      String uriString =
          System.getProperty(PROXY_DETECTION_URI_PROPERTY, DEFAULT_PROXY_DETECTION_URI);
      URI uri = new URI(uriString);
      Optional<ProxyOptions> result =
          proxySelector.select(uri).stream()
              .filter(p -> p.address() != null)
              .findFirst()
              .map(
                  p -> {
                    InetSocketAddress addr = (InetSocketAddress) p.address();
                    return new ProxyOptions()
                        .setHost(addr.getHostString())
                        .setPort(addr.getPort())
                        .setType(ProxyType.HTTP);
                  });
      result.ifPresent(opts -> LOG.debug("Using HTTP proxy {}:{}", opts.getHost(), opts.getPort()));
      return result;
    } catch (URISyntaxException e) {
      // This should never happen with a hardcoded valid URI
      LOG.warn("Failed to parse proxy detection URI", e);
      return Optional.empty();
    }
  }

  /**
   * Returns a Java {@link Proxy} for the given URI using the system's default ProxySelector.
   *
   * <p>This method is useful for non-Vert.x HTTP clients (e.g., Apache CXF) that need standard Java
   * proxy configuration.
   *
   * @param uri the URI to get a proxy for
   * @return an Optional containing the first proxy with a non-null address, or empty if none found
   */
  public static Optional<Proxy> getProxy(URI uri) {
    ProxySelector proxySelector = ProxySelector.getDefault();
    if (proxySelector == null) {
      return Optional.empty();
    }
    return proxySelector.select(uri).stream().filter(p -> p.address() != null).findFirst();
  }
}
