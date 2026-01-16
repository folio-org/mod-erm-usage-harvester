package org.olf.erm.usage.harvester.endpoints;

import static java.util.Objects.requireNonNull;
import static org.olf.erm.usage.counter51.Counter51Utils.getYearMonths;
import static org.olf.erm.usage.counter51.JsonProperties.EXCEPTIONS;
import static org.olf.erm.usage.counter51.JsonProperties.REPORT_HEADER;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClientOptions;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.HarvestingConfig;
import org.folio.rest.jaxrs.model.Report;
import org.folio.rest.jaxrs.model.SushiConfig;
import org.folio.rest.jaxrs.model.SushiCredentials;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.counter51.Counter51Utils;
import org.olf.erm.usage.counter51.client.Counter51Auth;

public class CS51Impl implements ServiceEndpoint {

  public static final String ATTRIBUTES_TO_SHOW_DR = "Access_Method";
  public static final String ATTRIBUTES_TO_SHOW_IR =
      "Authors|Publication_Date|Article_Version|YOP|Access_Type|Access_Method";
  public static final String ATTRIBUTES_TO_SHOW_PR = "Access_Method";
  public static final String ATTRIBUTES_TO_SHOW_TR = "YOP|Access_Type|Access_Method";

  private final String customerId;
  private final String reportRelease;
  private final String platform;
  private final String providerId;
  private final Vertx vertx;
  private final ExtendedCounter51Client client;
  private final ObjectMapper objectMapper = Counter51Utils.getDefaultObjectMapper();

  public CS51Impl(UsageDataProvider provider) {
    requireNonNull(provider);
    SushiCredentials sushiCredentials = requireNonNull(provider.getSushiCredentials());
    HarvestingConfig harvestingConfig = requireNonNull(provider.getHarvestingConfig());
    SushiConfig sushiConfig = requireNonNull(provider.getHarvestingConfig().getSushiConfig());

    String serviceUrlStr = requireNonNull(sushiConfig.getServiceUrl());
    try {
      new URI(serviceUrlStr).toURL();
    } catch (MalformedURLException | URISyntaxException | IllegalArgumentException e) {
      throw new InvalidServiceURLException(serviceUrlStr);
    }

    this.customerId = requireNonNull(sushiCredentials.getCustomerId());
    String requestorId = sushiCredentials.getRequestorId();
    String apiKey = sushiCredentials.getApiKey();
    this.platform = sushiCredentials.getPlatform();
    this.reportRelease = requireNonNull(harvestingConfig.getReportRelease());
    this.providerId = requireNonNull(provider.getId());

    Context context = Vertx.currentContext();
    this.vertx = context == null ? Vertx.vertx() : context.owner();

    Counter51Auth auth = new Counter51Auth(apiKey, requestorId);
    WebClientOptions webClientOptions = new WebClientOptions().setIdleTimeout(60);
    this.client = new ExtendedCounter51Client(vertx, webClientOptions, serviceUrlStr, auth);
  }

  private Future<ObjectNode> callClientMethod(String reportName, String beginDate, String endDate) {
    Future<?> reportFuture =
        switch (reportName) {
          case "TR" -> client.getReportsTR(customerId, beginDate, endDate, platform);
          case "PR" -> client.getReportsPR(customerId, beginDate, endDate, platform);
          case "DR" -> client.getReportsDR(customerId, beginDate, endDate, platform);
          case "IR" -> client.getReportsIR(customerId, beginDate, endDate, platform);
          default -> Future.failedFuture(new UnsupportedReportTypeException(reportName));
        };

    return reportFuture.map(report -> objectMapper.convertValue(report, ObjectNode.class));
  }

  private Future<List<CounterReport>> splitReport(ObjectNode objectNode, String reportName) {
    return vertx.executeBlocking(
        () -> {
          Date downloadTime = Date.from(Instant.now());
          return Counter51Utils.splitReport(objectNode).stream()
              .map(
                  r -> {
                    List<YearMonth> months = getYearMonths(r);
                    return createCounterReport(r, reportName, months.get(0), downloadTime);
                  })
              .toList();
        });
  }

  @Override
  public Future<List<CounterReport>> fetchReport(String report, String beginDate, String endDate) {
    String uppercaseReport = report.toUpperCase();
    return callClientMethod(uppercaseReport, beginDate, endDate)
        .map(this::throwIfReportContainsExceptions)
        .compose(on -> splitReport(on, uppercaseReport));
  }

  private ObjectNode throwIfReportContainsExceptions(ObjectNode objectNode) {
    JsonNode exceptionsNode = objectNode.path(REPORT_HEADER).path(EXCEPTIONS);
    if (exceptionsNode.isArray() && !exceptionsNode.isEmpty()) {
      throw new InvalidReportException(exceptionsNode.toString());
    }
    return objectNode;
  }

  private CounterReport createCounterReport(
      ObjectNode reportData, String reportName, YearMonth yearMonth, Date downloadTime) {
    CounterReport cr = new CounterReport();
    cr.setId(UUID.randomUUID().toString());
    cr.setYearMonth(yearMonth.toString());
    cr.setReportName(reportName);
    cr.setRelease(reportRelease);
    cr.setProviderId(providerId);
    cr.setDownloadTime(downloadTime);
    cr.setReport(objectMapper.convertValue(reportData, Report.class));
    return cr;
  }
}
