package org.olf.erm.usage.harvester.endpoints;

import static org.olf.erm.usage.harvester.endpoints.JsonUtil.isOfType;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import java.lang.reflect.Method;
import java.net.Proxy;
import java.net.URI;
import java.time.YearMonth;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Response.Builder;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.counter50.Counter5Utils;
import org.olf.erm.usage.counter50.Counter5Utils.Counter5UtilsException;
import org.openapitools.client.ApiClient;
import org.openapitools.client.model.COUNTERDatabaseReport;
import org.openapitools.client.model.COUNTERItemReport;
import org.openapitools.client.model.COUNTERPlatformReport;
import org.openapitools.client.model.COUNTERTitleReport;
import org.openapitools.client.model.SUSHIReportHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.HttpException;

public class CS50Impl implements ServiceEndpoint {

  public static final int MAX_ERROR_BODY_LENGTH = 2000;
  private static final Gson gson = new Gson();
  private static final Logger LOG = LoggerFactory.getLogger(CS50Impl.class);
  private final UsageDataProvider provider;
  private final DefaultApi client;

  CS50Impl(UsageDataProvider provider) {
    Objects.requireNonNull(provider.getSushiCredentials());
    Objects.requireNonNull(provider.getHarvestingConfig());
    Objects.requireNonNull(provider.getHarvestingConfig().getSushiConfig());
    Objects.requireNonNull(provider.getHarvestingConfig().getSushiConfig().getServiceUrl());
    this.provider = provider;

    String baseUrl = provider.getHarvestingConfig().getSushiConfig().getServiceUrl();
    if (!baseUrl.endsWith("/")) baseUrl += "/";

    ApiClient apiClient = new ApiClient();
    String apiKey = provider.getSushiCredentials().getApiKey();
    String reqId = provider.getSushiCredentials().getRequestorId();
    if (!Strings.isNullOrEmpty(apiKey)) {
      apiClient = new ApiClient("api_key", apiKey);
    } else if (!Strings.isNullOrEmpty(reqId)) {
      apiClient = new ApiClient("requestor_id", reqId);
    }

    apiClient.getAdapterBuilder().baseUrl(baseUrl);
    apiClient.getOkBuilder().readTimeout(60, TimeUnit.SECONDS);
    // apiClient.getOkBuilder().addInterceptor(new HttpLoggingInterceptor().setLevel(Level.BODY));

    apiClient
        .getOkBuilder()
        .addInterceptor(
            chain -> {
              Request request = chain.request();
              Response response = chain.proceed(request);

              // intercept response body if status code 2xx
              if (response.code() / 100 == 2) {
                ResponseBody responseBody =
                    Objects.requireNonNull(response.body(), "Response body is null");
                String body = responseBody.string();
                MediaType mediaType = responseBody.contentType();
                Builder respBuilder =
                    response.newBuilder().body(ResponseBody.create(body, mediaType));

                // pass through if its a report
                if (isOfType(body, COUNTERTitleReport.class)
                    || isOfType(body, COUNTERItemReport.class)
                    || isOfType(body, COUNTERPlatformReport.class)
                    || isOfType(body, COUNTERDatabaseReport.class)) {
                  return respBuilder.build();
                } else {
                  // otherwise route to 400
                  return respBuilder.code(400).message("Bad Request").build();
                }
              }
              return response;
            });

    try {
      Optional<Proxy> proxy = getProxy(new URI(baseUrl));
      if (proxy.isPresent()) apiClient.getOkBuilder().proxy(proxy.get());
    } catch (Exception e) {
      LOG.error("Error getting proxy: {}", e.getMessage());
    }

    client = apiClient.createService(DefaultApi.class);
  }

  private Throwable getSushiError(Throwable e) {
    if (e instanceof NoSuchElementException) {
      return new InvalidReportException(e.getMessage());
    }
    if (e instanceof HttpException) {
      HttpException ex = (HttpException) e;
      try {
        ResponseBody responseBody = ex.response().errorBody();
        String errorBody = Objects.requireNonNull(responseBody).string();
        if (!Strings.isNullOrEmpty(errorBody)) {
          return new Throwable(StringUtils.abbreviate(errorBody, MAX_ERROR_BODY_LENGTH), ex);
        }
      } catch (Exception exc) {
        return new Throwable("Error parsing error response: " + exc.getMessage(), ex);
      }
    }
    return e;
  }

  private List<CounterReport> createCounterReportList(
      Object report, String reportType, UsageDataProvider provider) throws Counter5UtilsException {

    List<Object> splitReports = Counter5Utils.split(report);
    return splitReports.stream()
        .map(
            r -> {
              List<YearMonth> yearMonthsFromReport = Counter5Utils.getYearMonthFromReport(r);
              if (yearMonthsFromReport.size() != 1) {
                throw new CS50Exception("Split report size not equal to 1");
              }

              return ServiceEndpoint.createCounterReport(
                  gson.toJson(r), reportType, provider, yearMonthsFromReport.get(0));
            })
        .collect(Collectors.toList());
  }

  private boolean hasReportItems(Object report) {
    return gson.toJsonTree(report).getAsJsonObject().getAsJsonArray("Report_Items").size() > 0;
  }

  private Object failIfInvalidReport(Object report)
      throws InvalidReportException, Counter5UtilsException {
    String content = gson.toJson(report);
    SUSHIReportHeader reportHeader = Counter5Utils.getSushiReportHeader(content);

    if (reportHeader == null) {
      throw new InvalidReportException("Report is missing Report_Header");
    }

    if (!reportHeader.getExceptions().isEmpty()) {
      throw new InvalidReportException(gson.toJson(reportHeader.getExceptions()));
    }

    if (!hasReportItems(report)) {
      throw new InvalidReportException("Report is missing Report_Items");
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
      return Future.failedFuture(e);
    }

    String customerId = provider.getSushiCredentials().getCustomerId();
    String platform = provider.getSushiCredentials().getPlatform();

    Promise<List<CounterReport>> promise = Promise.promise();
    try {
      ((Observable<?>) method.invoke(client, customerId, beginDate, endDate, platform))
          .singleOrError()
          .subscribeOn(Schedulers.io())
          .map(this::failIfInvalidReport)
          .map(r -> createCounterReportList(r, report, provider))
          .subscribe(promise::complete, e -> promise.fail(getSushiError(e)));
    } catch (Exception e) {
      promise.fail(e);
    }

    return promise.future();
  }

  static class CS50Exception extends RuntimeException {

    public CS50Exception(String message) {
      super(message);
    }
  }
}
