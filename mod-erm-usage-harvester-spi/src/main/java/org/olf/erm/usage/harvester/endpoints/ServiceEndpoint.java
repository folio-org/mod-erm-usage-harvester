package org.olf.erm.usage.harvester.endpoints;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Collections;
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

  boolean isValidReport(String report);

  Future<String> fetchSingleReport(String report, String beginDate, String endDate);

  default Future<List<CounterReport>> fetchReport(String report, String beginDate, String endDate) {
    return Future.succeededFuture(Collections.emptyList());
  }

  static CounterReport createCounterReport(
      String reportData, String reportName, UsageDataProvider provider, YearMonth yearMonth) {
    CounterReport cr = new CounterReport();
    cr.setId(UUID.randomUUID().toString());
    cr.setYearMonth(yearMonth.toString());
    cr.setReportName(reportName);
    cr.setRelease(provider.getHarvestingConfig().getReportRelease().toString());
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

    final Logger LOG = LoggerFactory.getLogger(ServiceEndpoint.class);

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

  default Optional<Proxy> getProxy(URI uri) {
    return ProxySelector.getDefault().select(uri).stream()
        .filter(p -> p.address() != null)
        .findFirst();
  }
}
