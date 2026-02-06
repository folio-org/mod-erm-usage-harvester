package org.olf.erm.usage.harvester.endpoints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebClientsTest {

  private Vertx vertx;
  private ProxySelector originalProxySelector;

  @BeforeEach
  void setUp() {
    WebClients.reset();
    originalProxySelector = ProxySelector.getDefault();
    vertx = Vertx.vertx();
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    ProxySelector.setDefault(originalProxySelector);
    if (vertx != null) {
      CountDownLatch latch = new CountDownLatch(1);
      vertx.close().onComplete(ar -> latch.countDown());
      latch.await(5, TimeUnit.SECONDS);
    }
    WebClients.reset();
  }

  @Test
  void internalReturnsSameInstanceForSameVertx() {
    WebClient client1 = WebClients.internal(vertx);
    WebClient client2 = WebClients.internal(vertx);

    assertThat(client1).isSameAs(client2);
  }

  @Test
  void externalReturnsSameInstanceForSameVertx() {
    WebClient client1 = WebClients.external(vertx);
    WebClient client2 = WebClients.external(vertx);

    assertThat(client1).isSameAs(client2);
  }

  @Test
  void internalAndExternalReturnDifferentInstances() {
    WebClient internal = WebClients.internal(vertx);
    WebClient external = WebClients.external(vertx);

    assertThat(internal).isNotSameAs(external);
  }

  @Test
  void differentVertxInstancesGetDifferentClients() throws InterruptedException {
    Vertx vertx2 = Vertx.vertx();
    try {
      WebClient client1 = WebClients.internal(vertx);
      WebClient client2 = WebClients.internal(vertx2);

      assertThat(client1).isNotSameAs(client2);
    } finally {
      CountDownLatch latch = new CountDownLatch(1);
      vertx2.close().onComplete(ar -> latch.countDown());
      latch.await(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void internalThrowsOnNullVertx() {
    assertThatThrownBy(() -> WebClients.internal(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("vertx must not be null");
  }

  @Test
  void externalThrowsOnNullVertx() {
    assertThatThrownBy(() -> WebClients.external(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("vertx must not be null");
  }

  @Test
  void getProxyOptionsReturnsEmptyWhenNoProxyConfigured() {
    ProxySelector.setDefault(
        new ProxySelector() {
          @Override
          public List<Proxy> select(URI uri) {
            return Collections.singletonList(Proxy.NO_PROXY);
          }

          @Override
          public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            // Not needed for this test - proxy connection failures are not tested here
          }
        });

    assertThat(WebClients.getProxyOptions()).isEmpty();
  }

  @Test
  void getProxyOptionsReturnsEmptyWhenProxySelectorIsNull() {
    ProxySelector.setDefault(null);

    assertThat(WebClients.getProxyOptions()).isEmpty();
  }

  @Test
  void getProxyOptionsReturnsProxyWhenConfigured() {
    String proxyHost = "proxy.example.com";
    int proxyPort = 8080;

    ProxySelector.setDefault(
        new ProxySelector() {
          @Override
          public List<Proxy> select(URI uri) {
            return Collections.singletonList(
                new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
          }

          @Override
          public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            // Not needed for this test - proxy connection failures are not tested here
          }
        });

    assertThat(WebClients.getProxyOptions())
        .isPresent()
        .hasValueSatisfying(
            proxyOptions -> {
              assertThat(proxyOptions.getHost()).isEqualTo(proxyHost);
              assertThat(proxyOptions.getPort()).isEqualTo(proxyPort);
              assertThat(proxyOptions.getType()).isEqualTo(io.vertx.core.net.ProxyType.HTTP);
            });
  }

  @Test
  void getProxyOptionsFiltersOutProxiesWithNullAddress() {
    ProxySelector.setDefault(
        new ProxySelector() {
          @Override
          public List<Proxy> select(URI uri) {
            // Proxy.NO_PROXY has null address
            return List.of(
                Proxy.NO_PROXY,
                new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy.example.com", 8080)));
          }

          @Override
          public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            // Not needed for this test - proxy connection failures are not tested here
          }
        });

    assertThat(WebClients.getProxyOptions())
        .isPresent()
        .hasValueSatisfying(
            proxyOptions -> {
              assertThat(proxyOptions.getHost()).isEqualTo("proxy.example.com");
              assertThat(proxyOptions.getPort()).isEqualTo(8080);
            });
  }

  @Test
  void externalIdleTimeoutIsConfigured() {
    assertThat(WebClients.EXTERNAL_IDLE_TIMEOUT_SECONDS).isEqualTo(60);
  }

  @Test
  void resetClearsAllCachedClients() {
    WebClient internal1 = WebClients.internal(vertx);
    WebClient external1 = WebClients.external(vertx);

    WebClients.reset();

    // After reset, we should get new instances
    WebClient internal2 = WebClients.internal(vertx);
    WebClient external2 = WebClients.external(vertx);

    assertThat(internal2).isNotSameAs(internal1);
    assertThat(external2).isNotSameAs(external1);
  }

  @Test
  void getProxyReturnsEmptyWhenProxySelectorIsNull() throws Exception {
    ProxySelector.setDefault(null);

    assertThat(WebClients.getProxy(new URI("https://example.com"))).isEmpty();
  }

  @Test
  void getProxyReturnsEmptyWhenNoProxyConfigured() throws Exception {
    ProxySelector.setDefault(
        new ProxySelector() {
          @Override
          public List<Proxy> select(URI uri) {
            return Collections.singletonList(Proxy.NO_PROXY);
          }

          @Override
          public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            // Not needed for this test - proxy connection failures are not tested here
          }
        });

    assertThat(WebClients.getProxy(new URI("https://example.com"))).isEmpty();
  }

  @Test
  void getProxyReturnsProxyWhenConfigured() throws Exception {
    String proxyHost = "proxy.example.com";
    int proxyPort = 8080;

    ProxySelector.setDefault(
        new ProxySelector() {
          @Override
          public List<Proxy> select(URI uri) {
            return Collections.singletonList(
                new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
          }

          @Override
          public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            // Not needed for this test - proxy connection failures are not tested here
          }
        });

    assertThat(WebClients.getProxy(new URI("https://example.com")))
        .isPresent()
        .hasValueSatisfying(
            proxy -> {
              assertThat(proxy.type()).isEqualTo(Proxy.Type.HTTP);
              InetSocketAddress addr = (InetSocketAddress) proxy.address();
              assertThat(addr.getHostString()).isEqualTo(proxyHost);
              assertThat(addr.getPort()).isEqualTo(proxyPort);
            });
  }

  @Test
  void getProxyFiltersOutProxiesWithNullAddress() throws Exception {
    ProxySelector.setDefault(
        new ProxySelector() {
          @Override
          public List<Proxy> select(URI uri) {
            return List.of(
                Proxy.NO_PROXY,
                new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy.example.com", 8080)));
          }

          @Override
          public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            // Not needed for this test - proxy connection failures are not tested here
          }
        });

    assertThat(WebClients.getProxy(new URI("https://example.com")))
        .isPresent()
        .hasValueSatisfying(
            proxy -> {
              InetSocketAddress addr = (InetSocketAddress) proxy.address();
              assertThat(addr.getHostString()).isEqualTo("proxy.example.com");
              assertThat(addr.getPort()).isEqualTo(8080);
            });
  }
}
