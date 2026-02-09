package org.olf.erm.usage.harvester.endpoints;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.UUID;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.Report;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ServiceEndpoint {

  Logger LOG = LoggerFactory.getLogger(ServiceEndpoint.class);

  /**
   * Fetches a report from a provider and returns a list containing a {@link CounterReport} for each
   * month in the requested range.
   *
   * <p>Use {@link ServiceEndpoint#createCounterReport(String, String, UsageDataProvider,
   * YearMonth)} for creating a {@link CounterReport}
   *
   * @param report requested report type
   * @param beginDate start date (e.g. "2018-01-01")
   * @param endDate end date (e.g. "2018-12-31")
   * @return Future with a List of {@link CounterReport}
   * @throws InvalidReportException if the fetched report contains any COUNTER exceptions
   * @throws InvalidServiceURLException if the service URL is invalid
   * @throws TooManyRequestsException to signal that too many requests are made
   * @throws UnsupportedReportTypeException if the requested report type is not supported
   */
  Future<List<CounterReport>> fetchReport(String report, String beginDate, String endDate);

  static CounterReport createCounterReport(
      String reportData, String reportName, UsageDataProvider provider, YearMonth yearMonth) {
    CounterReport cr = new CounterReport();
    cr.setId(UUID.randomUUID().toString());
    cr.setYearMonth(yearMonth.toString());
    cr.setReportName(reportName);
    cr.setRelease(provider.getHarvestingConfig().getReportRelease());
    cr.setProviderId(provider.getId());
    cr.setDownloadTime(Date.from(Instant.now()));
    if (reportData != null) {
      cr.setReport(Json.decodeValue(reportData, Report.class));
    } else {
      cr.setFailedAttempts(1);
    }
    return cr;
  }

  static List<ServiceEndpointProvider> getAvailableProviders() {
    ServiceLoader<ServiceEndpointProvider> loader =
        ServiceLoader.load(ServiceEndpointProvider.class);
    return Lists.newArrayList(loader.iterator());
  }

  static ServiceEndpoint create(UsageDataProvider provider, AggregatorSetting aggregator) {
    Objects.requireNonNull(provider);

    String serviceType;
    if (Objects.isNull(aggregator)) {
      if (Objects.nonNull(provider.getHarvestingConfig())
          && Objects.nonNull(provider.getHarvestingConfig().getSushiConfig())) {
        serviceType = provider.getHarvestingConfig().getSushiConfig().getServiceType();
      } else {
        serviceType = null;
      }
    } else {
      serviceType = aggregator.getServiceType();
    }

    if (Strings.isNullOrEmpty(serviceType)) {
      LOG.error("ServiceType is null or empty for providerId {}", provider.getId());
      return null;
    }

    ServiceLoader<ServiceEndpointProvider> loader =
        ServiceLoader.load(ServiceEndpointProvider.class);
    for (ServiceEndpointProvider p : loader) {
      if (p.getServiceType().equals(serviceType)) {
        return p.create(provider, aggregator);
      }
    }

    LOG.error("No implementation found for serviceType '{}'", serviceType);
    return null;
  }

  /**
   * Returns a proxy for the given URI using the system's default {@link ProxySelector}.
   *
   * @param uri the URI to get a proxy for
   * @return an Optional containing the first proxy with a non-null address, or empty if none found
   */
  default Optional<Proxy> getProxy(URI uri) {
    return ProxySelector.getDefault().select(uri).stream()
        .filter(p -> p.address() != null)
        .findFirst();
  }

  /**
   * Returns Vert.x {@link ProxyOptions} for the given URL using the system's default proxy
   * settings.
   *
   * @param url the URL to get proxy options for
   * @return an Optional containing ProxyOptions if a proxy is configured, or empty if no proxy is
   *     needed or the URL is null/invalid
   */
  default Optional<ProxyOptions> getProxyOptions(String url) {
    if (url == null) {
      return Optional.empty();
    }
    try {
      return getProxy(new URI(url))
          .map(
              p -> {
                InetSocketAddress addr = (InetSocketAddress) p.address();
                return new ProxyOptions()
                    .setHost(addr.getHostString())
                    .setPort(addr.getPort())
                    .setType(ProxyType.HTTP);
              });
    } catch (URISyntaxException e) {
      LOG.error("Error getting proxy for URL '{}': {}", url, e.getMessage());
      return Optional.empty();
    }
  }
}
