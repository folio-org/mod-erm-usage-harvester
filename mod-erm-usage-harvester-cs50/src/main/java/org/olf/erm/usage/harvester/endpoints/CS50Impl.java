package org.olf.erm.usage.harvester.endpoints;

import static io.swagger.annotations.ApiKeyAuthDefinition.ApiKeyLocation.QUERY;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.olf.erm.usage.harvester.endpoints.JsonUtil.isJsonArray;
import static org.olf.erm.usage.harvester.endpoints.JsonUtil.isOfType;
import static org.olf.erm.usage.harvester.endpoints.TooManyRequestsException.TOO_MANY_REQUEST_ERROR_CODE;
import static org.olf.erm.usage.harvester.endpoints.TooManyRequestsException.TOO_MANY_REQUEST_STR;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import io.reactivex.Observable;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import java.lang.reflect.Method;
import java.net.Proxy;
import java.net.URI;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Response.Builder;
import okhttp3.ResponseBody;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.counter50.Counter5Utils;
import org.olf.erm.usage.counter50.Counter5Utils.Counter5UtilsException;
import org.openapitools.client.ApiClient;
import org.openapitools.client.auth.ApiKeyAuth;
import org.openapitools.client.model.COUNTERDatabaseReport;
import org.openapitools.client.model.COUNTERItemReport;
import org.openapitools.client.model.COUNTERPlatformReport;
import org.openapitools.client.model.COUNTERTitleReport;
import org.openapitools.client.model.SUSHIErrorModel;
import org.openapitools.client.model.SUSHIReportHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.HttpException;

public class CS50Impl implements ServiceEndpoint {

  public static final int MAX_ERROR_BODY_LENGTH = 2000;
  private static final Gson gson = new Gson();
  private static final Logger LOG = LoggerFactory.getLogger(CS50Impl.class);
  private static final Map<String, Class<?>> reportClassMap =
      Map.of(
          "tr",
          COUNTERTitleReport.class,
          "pr",
          COUNTERPlatformReport.class,
          "dr",
          COUNTERDatabaseReport.class,
          "ir",
          COUNTERItemReport.class);
  private final UsageDataProvider provider;
  private final DefaultApi client;

  public CS50Impl(UsageDataProvider provider) {
    requireNonNull(provider.getSushiCredentials());
    requireNonNull(provider.getHarvestingConfig());
    requireNonNull(provider.getHarvestingConfig().getSushiConfig());
    requireNonNull(provider.getHarvestingConfig().getSushiConfig().getServiceUrl());
    this.provider = provider;

    String baseUrl = provider.getHarvestingConfig().getSushiConfig().getServiceUrl();
    if (!baseUrl.endsWith("/")) baseUrl += "/";

    ApiClient apiClient = new ApiClient();
    String apiKey = provider.getSushiCredentials().getApiKey();
    String reqId = provider.getSushiCredentials().getRequestorId();

    if (!Strings.isNullOrEmpty(apiKey)) {
      ApiKeyAuth keyAuth1 = new ApiKeyAuth(QUERY.toValue(), "api_key");
      keyAuth1.setApiKey(apiKey);
      apiClient.addAuthorization("api_key", keyAuth1);
    }
    if (!Strings.isNullOrEmpty(reqId)) {
      ApiKeyAuth keyAuth2 = new ApiKeyAuth(QUERY.toValue(), "requestor_id");
      keyAuth2.setApiKey(reqId);
      apiClient.addAuthorization("requestor_id", keyAuth2);
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
                    requireNonNull(response.body(), "Response body is null");
                String body = responseBody.string();
                MediaType mediaType = responseBody.contentType();
                Builder respBuilder =
                    response.newBuilder().body(ResponseBody.create(body, mediaType));

                Class<?> expectedReportClass =
                    reportClassMap.get(
                        request.url().pathSegments().get(request.url().pathSize() - 1));
                try {
                  // pass through if its a valid report
                  JsonUtil.validate(body, expectedReportClass);
                  return respBuilder.build();
                } catch (Exception e) {
                  // otherwise route to 400
                  respBuilder.code(400).message("Bad Request");
                  if (isOfType(body, SUSHIErrorModel.class) || isJsonArray(body)) {
                    return respBuilder.build();
                  }
                  return respBuilder
                      .body(ResponseBody.create(e.toString(), MediaType.parse("text/plain")))
                      .build();
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
        ResponseBody responseBody = requireNonNull(ex.response()).errorBody();
        String errorBody = requireNonNull(responseBody).string();
        String abbrStr = abbreviate(errorBody, MAX_ERROR_BODY_LENGTH);
        if (ex.code() == 429) {
          return new TooManyRequestsException(abbrStr, ex);
        } else {
          return new Throwable(abbrStr, ex);
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

  private boolean containsTooManyRequestsError(List<SUSHIErrorModel> errors) {
    return errors.stream()
        .anyMatch(
            em ->
                (nonNull(em.getMessage()) && em.getMessage().contains(TOO_MANY_REQUEST_STR))
                    || (nonNull(em.getCode()) && em.getCode().equals(TOO_MANY_REQUEST_ERROR_CODE)));
  }

  private Object failIfInvalidReport(Object report)
      throws InvalidReportException, Counter5UtilsException {
    String content = gson.toJson(report);
    SUSHIReportHeader reportHeader = Counter5Utils.getSushiReportHeader(content);

    if (reportHeader == null) {
      throw new InvalidReportException("Report is missing Report_Header");
    }

    if (reportHeader.getExceptions() != null && !reportHeader.getExceptions().isEmpty()) {
      String exceptionMsg = gson.toJson(reportHeader.getExceptions());
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
      return Future.failedFuture(e);
    }

    String customerId = provider.getSushiCredentials().getCustomerId();
    String platform = provider.getSushiCredentials().getPlatform();

    Promise<List<CounterReport>> promise = Promise.promise();
    try {
      ((Observable<?>) method.invoke(client, customerId, beginDate, endDate, platform))
          .singleOrError()
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
