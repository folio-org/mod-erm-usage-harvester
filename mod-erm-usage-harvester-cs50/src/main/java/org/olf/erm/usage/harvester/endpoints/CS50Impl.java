package org.olf.erm.usage.harvester.endpoints;

import static io.vertx.core.Future.failedFuture;
import static java.util.Objects.requireNonNull;
import static org.olf.erm.usage.harvester.endpoints.TooManyRequestsException.TOO_MANY_REQUEST_ERROR_CODE;
import static org.olf.erm.usage.harvester.endpoints.TooManyRequestsException.TOO_MANY_REQUEST_STR;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.base.Strings;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.ext.web.client.WebClientOptions;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.counter50.Counter5Utils;
import org.olf.erm.usage.counter50.Counter5Utils.Counter5UtilsException;
import org.openapitools.client.ApiClient.AuthInfo;
import org.openapitools.client.CounterApiClient;
import org.openapitools.client.api.CounterDefaultApiImpl;
import org.openapitools.client.model.COUNTERDatabaseReport;
import org.openapitools.client.model.COUNTERItemReport;
import org.openapitools.client.model.COUNTERPlatformReport;
import org.openapitools.client.model.COUNTERTitleReport;
import org.openapitools.client.model.SUSHIErrorModel;
import org.openapitools.client.model.SUSHIReportHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CS50Impl implements ServiceEndpoint {

  static {
    /* Vert.x 4.5.22 introduced java.time.Duration in ProxyOptions, which requires Jackson's
    JSR310 module for serialization. */
    DatabindCodec.mapper().registerModule(new JavaTimeModule());
    DatabindCodec.prettyMapper().registerModule(new JavaTimeModule());
  }

  public static final int MAX_ERROR_BODY_LENGTH = 2000;
  private static final Logger LOG = LoggerFactory.getLogger(CS50Impl.class);
  private final UsageDataProvider provider;
  private final CounterDefaultApiImpl client;

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
    AuthInfo authInfo = createAuthInfo(apiKey, reqId);

    WebClientOptions webClientOptions = new WebClientOptions();
    try {
      Optional<Proxy> proxy = getProxy(new URI(baseUrl));
      proxy.ifPresent(
          p -> {
            ProxyOptions proxyOptions = new ProxyOptions();
            InetSocketAddress addr = (InetSocketAddress) p.address();
            proxyOptions.setHost(addr.getHostString());
            proxyOptions.setPort(addr.getPort());
            proxyOptions.setType(ProxyType.HTTP);
            webClientOptions.setProxyOptions(proxyOptions);
          });
    } catch (Exception e) {
      LOG.error("Error getting proxy: {}", e.getMessage());
    }

    Context context = Vertx.currentContext();
    vertx = context == null ? Vertx.vertx() : context.owner();

    JsonObject config = JsonObject.mapFrom(webClientOptions).put("timeout", 60000);
    CounterApiClient counterApiClient = new CounterApiClient(vertx, config);
    counterApiClient.setBasePath(baseUrl);

    client = new CounterDefaultApiImpl(counterApiClient, authInfo);
  }

  private AuthInfo createAuthInfo(String apiKey, String reqId) {
    AuthInfo authInfo = new AuthInfo();
    if (!Strings.isNullOrEmpty(apiKey)) {
      authInfo.addApi_keyAuthentication(apiKey, null);
    }
    if (!Strings.isNullOrEmpty(reqId)) {
      authInfo.addRequestor_idAuthentication(reqId, null);
    }
    return authInfo;
  }

  private Future<List<CounterReport>> createCounterReportList(
      Object report, String reportType, UsageDataProvider provider) {
    return vertx.executeBlocking(
        bch -> {
          List<Object> splitReports;
          try {
            splitReports = Counter5Utils.split(report);
          } catch (Counter5UtilsException e) {
            throw new CS50Exception(e);
          }

          bch.complete(
              splitReports.stream()
                  .map(
                      r -> {
                        List<YearMonth> yearMonthsFromReport =
                            Counter5Utils.getYearMonthFromReport(r);
                        if (yearMonthsFromReport.size() != 1) {
                          throw new CS50Exception("Split report size not equal to 1");
                        }

                        return ServiceEndpoint.createCounterReport(
                            Json.encode(r), reportType, provider, yearMonthsFromReport.get(0));
                      })
                  .collect(Collectors.toList()));
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
  public Future<List<CounterReport>> fetchReport(String report, String beginDate, String endDate) {
    String reportID = report.replace("_", "").toUpperCase();

    Method method;
    try {
      method =
          client
              .getClass()
              .getMethod(
                  "getReports" + reportID, String.class, String.class, String.class, String.class);
    } catch (NoSuchMethodException e) {
      LOG.error(e.getMessage(), e);
      return failedFuture(e);
    }

    String customerId = provider.getSushiCredentials().getCustomerId();
    String platform = provider.getSushiCredentials().getPlatform();

    Promise<List<CounterReport>> promise = Promise.promise();
    try {
      ((Future<Object>) method.invoke(client, customerId, beginDate, endDate, platform))
          .map(this::failIfInvalidReport)
          .flatMap(r -> createCounterReportList(r, report, provider))
          .onSuccess(promise::complete)
          .onFailure(promise::fail);
    } catch (Exception e) {
      promise.fail(e);
    }
    return promise.future();
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
