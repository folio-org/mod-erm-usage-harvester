package org.olf.erm.usage.harvester.endpoints;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.HarvestingConfig;
import org.folio.rest.jaxrs.model.SushiConfig;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ServiceEndpointTest {

  private final ProxySelector originalProxySelector = ProxySelector.getDefault();
  private final ServiceEndpoint serviceEndpoint = new TestProviderImpl();

  private static UsageDataProvider provider =
      new UsageDataProvider()
          .withHarvestingConfig(
              new HarvestingConfig()
                  .withSushiConfig(new SushiConfig().withServiceType("TestProviderType")));

  @AfterEach
  void tearDown() {
    ProxySelector.setDefault(originalProxySelector);
  }

  @Test
  void testGetAvailableProviders() {
    List<ServiceEndpointProvider> list = ServiceEndpoint.getAvailableProviders();
    assertThat(list).hasSize(1);
    assertThat(list.getFirst().getServiceName()).isEqualTo("TestProviderName");
    assertThat(list.getFirst().getServiceType()).isEqualTo("TestProviderType");
  }

  @Test
  void testCreateNoImplGiven() {
    ServiceEndpoint sep =
        ServiceEndpoint.create(provider, new AggregatorSetting().withServiceType(""));
    assertThat(sep).isNull();
  }

  @Test
  void testCreateNoImplFound() {
    ServiceEndpoint sep =
        ServiceEndpoint.create(
            provider, new AggregatorSetting().withServiceType("TestProviderType2"));
    assertThat(sep).isNull();
  }

  @Test
  void testCreateOk() {
    ServiceEndpoint sep =
        ServiceEndpoint.create(
            provider, new AggregatorSetting().withServiceType("TestProviderType"));
    assertThat(sep).isInstanceOf(TestProviderImpl.class);
  }

  @Test
  void testCreateOkNoAggregator() {
    ServiceEndpoint sep = ServiceEndpoint.create(provider, null);
    assertThat(sep).isInstanceOf(TestProviderImpl.class);
  }

  @Test
  void testCreateNoHarvesterConfig() {
    ServiceEndpoint sep = ServiceEndpoint.create(new UsageDataProvider(), null);
    assertThat(sep).isNull();
  }

  @Test
  void testCreateNoSushiConfig() {
    ServiceEndpoint sep =
        ServiceEndpoint.create(
            new UsageDataProvider().withHarvestingConfig(new HarvestingConfig()), null);
    assertThat(sep).isNull();
  }

  @Test
  void testGetProxyOptionsReturnsEmptyWhenUrlIsNull() {
    Optional<ProxyOptions> result = serviceEndpoint.getProxyOptions(null);
    assertThat(result).isEmpty();
  }

  @Test
  void testGetProxyOptionsReturnsEmptyWhenUrlIsInvalid() {
    Optional<ProxyOptions> result = serviceEndpoint.getProxyOptions("not a valid url ://");
    assertThat(result).isEmpty();
  }

  @Test
  void testGetProxyOptionsReturnsEmptyWhenNoProxyConfigured() {
    ProxySelector.setDefault(new NoProxySelector());
    Optional<ProxyOptions> result = serviceEndpoint.getProxyOptions("http://example.com");
    assertThat(result).isEmpty();
  }

  @Test
  void testGetProxyOptionsReturnsProxyOptionsWhenConfigured() {
    ProxySelector.setDefault(new TestProxySelector("proxy.example.com", 8080));
    Optional<ProxyOptions> result = serviceEndpoint.getProxyOptions("http://example.com");
    assertThat(result).isPresent();
    ProxyOptions proxyOptions = result.get();
    assertThat(proxyOptions.getHost()).isEqualTo("proxy.example.com");
    assertThat(proxyOptions.getPort()).isEqualTo(8080);
    assertThat(proxyOptions.getType()).isEqualTo(ProxyType.HTTP);
  }

  /** ProxySelector that returns no proxy. */
  private static class NoProxySelector extends ProxySelector {
    @Override
    public List<Proxy> select(URI uri) {
      return Collections.singletonList(Proxy.NO_PROXY);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
      // no-op
    }
  }

  /** ProxySelector that returns a configured HTTP proxy. */
  private static class TestProxySelector extends ProxySelector {
    private final String host;
    private final int port;

    TestProxySelector(String host, int port) {
      this.host = host;
      this.port = port;
    }

    @Override
    public List<Proxy> select(URI uri) {
      return Collections.singletonList(
          new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port)));
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
      // no-op
    }
  }
}
