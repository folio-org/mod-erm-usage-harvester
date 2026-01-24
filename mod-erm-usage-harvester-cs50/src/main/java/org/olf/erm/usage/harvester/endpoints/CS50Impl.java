package org.olf.erm.usage.harvester.endpoints;

import static io.vertx.core.Future.failedFuture;
import static java.util.Objects.requireNonNull;
import static org.olf.erm.usage.harvester.endpoints.TooManyRequestsException.TOO_MANY_REQUEST_ERROR_CODE;
import static org.olf.erm.usage.harvester.endpoints.TooManyRequestsException.TOO_MANY_REQUEST_STR;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.ext.web.client.WebClientOptions;
import java.time.YearMonth;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.counter50.Counter5Utils;
import org.olf.erm.usage.counter50.Counter5Utils.Counter5UtilsException;
import org.olf.erm.usage.counter50.client.Counter50Auth;
import org.openapitools.counter50.model.COUNTERDatabaseReport;
import org.openapitools.counter50.model.COUNTERItemReport;
import org.openapitools.counter50.model.COUNTERPlatformReport;
import org.openapitools.counter50.model.COUNTERTitleReport;
import org.openapitools.counter50.model.SUSHIErrorModel;
import org.openapitools.counter50.model.SUSHIReportHeader;

public class CS50Impl implements ServiceEndpoint {

  private final UsageDataProvider provider;
  private final ExtendedCounter50Client client;

  private final Vertx vertx;

  public CS50Impl(UsageDataProvider provider) {
    requireNonNull(provider.getSushiCredentials());
    requireNonNull(provider.getHarvestingConfig());
    requireNonNull(provider.getHarvestingConfig().getSushiConfig());
    requireNonNull(provider.getHarvestingConfig().getSushiConfig().getServiceUrl());
    this.provider = provider;

    String baseUrl =
        StringUtils.removeEnd(provider.getHarvestingConfig().getSushiConfig().getServiceUrl(), "/");

    String apiKey = provider.getSushiCredentials().getApiKey();
    String reqId = provider.getSushiCredentials().getRequestorId();
    Counter50Auth auth = new Counter50Auth(apiKey, reqId);

    WebClientOptions webClientOptions = new WebClientOptions();
    getProxyOptions(baseUrl).ifPresent(webClientOptions::setProxyOptions);

    Context context = Vertx.currentContext();
    vertx = context == null ? Vertx.vertx() : context.owner();

    webClientOptions.setIdleTimeout(60);
    client = new ExtendedCounter50Client(vertx, webClientOptions, baseUrl, auth);
  }

  private Future<List<CounterReport>> createCounterReportList(
      Object report, String reportType, UsageDataProvider provider) {
    return vertx.executeBlocking(
        () -> {
          List<Object> splitReports;
          try {
            splitReports = Counter5Utils.split(report);
          } catch (Counter5UtilsException e) {
            throw new CS50Exception(e);
          }

          return splitReports.stream()
              .map(
                  r -> {
                    List<YearMonth> yearMonthsFromReport = Counter5Utils.getYearMonthFromReport(r);
                    if (yearMonthsFromReport.size() != 1) {
                      throw new CS50Exception("Split report size not equal to 1");
                    }

                    return ServiceEndpoint.createCounterReport(
                        Json.encode(r), reportType, provider, yearMonthsFromReport.get(0));
                  })
              .toList();
        });
  }

  private boolean containsTooManyRequestsError(List<SUSHIErrorModel> errors) {
    return errors.stream()
        .anyMatch(
            em ->
                (em.getMessage().contains(TOO_MANY_REQUEST_STR))
                    || (em.getCode().equals(TOO_MANY_REQUEST_ERROR_CODE)));
  }

  private SUSHIReportHeader getReportHeader(Object o) {
    if (o instanceof COUNTERTitleReport) {
      return ((COUNTERTitleReport) o).getReportHeader();
    }
    if (o instanceof COUNTERItemReport) {
      return ((COUNTERItemReport) o).getReportHeader();
    }
    if (o instanceof COUNTERDatabaseReport) {
      return ((COUNTERDatabaseReport) o).getReportHeader();
    }
    if (o instanceof COUNTERPlatformReport) {
      return ((COUNTERPlatformReport) o).getReportHeader();
    }
    return null;
  }

  private Object failIfInvalidReport(Object report)
      throws InvalidReportException, TooManyRequestsException {
    if (report == null) {
      throw new InvalidReportException("null");
    }

    SUSHIReportHeader reportHeader = getReportHeader(report);
    if (reportHeader == null) {
      throw new InvalidReportException("Report is missing Report_Header");
    }
    if (reportHeader.getExceptions() != null && !reportHeader.getExceptions().isEmpty()) {
      String exceptionMsg = Json.encode(reportHeader.getExceptions());
      if (containsTooManyRequestsError(reportHeader.getExceptions())) {
        throw new TooManyRequestsException(exceptionMsg);
      } else {
        throw new InvalidReportException(exceptionMsg);
      }
    }

    return report;
  }

  @Override
  public Future<List<CounterReport>> fetchReport(
      String reportName, String beginDate, String endDate) {
    String reportID = reportName.replace("_", "").toUpperCase();
    String customerId = provider.getSushiCredentials().getCustomerId();
    String platform = provider.getSushiCredentials().getPlatform();

    Future<?> reportFuture =
        switch (reportID) {
          case "TR" -> client.getReportsTR(customerId, beginDate, endDate, platform);
          case "IR" -> client.getReportsIR(customerId, beginDate, endDate, platform);
          case "DR" -> client.getReportsDR(customerId, beginDate, endDate, platform);
          case "PR" -> client.getReportsPR(customerId, beginDate, endDate, platform);
          default -> failedFuture(new UnsupportedReportTypeException(reportName));
        };

    return reportFuture
        .map(this::failIfInvalidReport)
        .flatMap(r -> createCounterReportList(r, reportName, provider));
  }

  static class CS50Exception extends RuntimeException {

    public CS50Exception(Throwable cause) {
      super(cause);
    }

    public CS50Exception(String message) {
      super(message);
    }
  }
}
