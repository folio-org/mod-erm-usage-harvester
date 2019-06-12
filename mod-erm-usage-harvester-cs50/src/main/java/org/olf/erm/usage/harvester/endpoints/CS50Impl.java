package org.olf.erm.usage.harvester.endpoints;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import io.vertx.core.Future;
import java.lang.reflect.Method;
import java.net.Proxy;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.counter50.Counter5Utils;
import org.openapitools.client.ApiClient;
import org.openapitools.client.model.SUSHIErrorModel;
import org.openapitools.client.model.SUSHIReportHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.HttpException;

public class CS50Impl implements ServiceEndpoint {

  private UsageDataProvider provider;
  private DefaultApi client;
  private static Gson gson = new Gson();
  private static final Logger LOG = LoggerFactory.getLogger(CS50Impl.class);

  @Override
  public boolean isValidReport(String report) {
    return false;
  }

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
    if (!Strings.isNullOrEmpty(apiKey)) apiClient = new ApiClient("api_key", apiKey);
    if (!Strings.isNullOrEmpty(reqId)) apiClient = new ApiClient("requestor_id", reqId);

    apiClient.getAdapterBuilder().baseUrl(baseUrl);
    apiClient.getOkBuilder().readTimeout(60, TimeUnit.SECONDS);
    // apiClient.getOkBuilder().addInterceptor(new HttpLoggingInterceptor().setLevel(Level.BODY));

    // workaround: route 201-299 codes to 400 (error)
    apiClient
        .getOkBuilder()
        .addInterceptor(
            chain -> {
              Request request = chain.request();
              Response response = chain.proceed(request);
              if (response.code() > 200 && response.code() < 300) {
                return response.newBuilder().code(400).build();
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
    if (e instanceof HttpException) {
      HttpException ex = (HttpException) e;
      try {
        ResponseBody responseBody = ex.response().errorBody();
        String errorBody = Objects.requireNonNull(responseBody).string();
        if (!Strings.isNullOrEmpty(errorBody)) {
          return new Throwable(gson.fromJson(errorBody, SUSHIErrorModel.class).toString(), ex);
        }
      } catch (Exception exc) {
        return new Throwable("Error parsing error response: " + exc.getMessage(), ex);
      }
    }
    return e;
  }

  @Override
  public Future<String> fetchSingleReport(String report, String beginDate, String endDate) {
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
    String platform = Objects.toString(provider.getSushiCredentials().getPlatform(), "");

    Future<String> future = Future.future();
    try {
      ((Observable<?>) method.invoke(client, customerId, beginDate, endDate, platform))
          .subscribeOn(Schedulers.io())
          .subscribe(
              r -> {
                String content = gson.toJson(r);
                SUSHIReportHeader reportHeader = Counter5Utils.getReportHeader(content);
                if (reportHeader == null) {
                  future.fail("Unkown Error - 200 Response is missing reportHeader");
                } else if (reportHeader.getExceptions() != null
                    && !reportHeader.getExceptions().isEmpty()) {
                  future.fail(
                      reportHeader.getExceptions().stream()
                          .map(SUSHIErrorModel::toString)
                          .collect(Collectors.joining(", ")));
                } else {
                  future.complete(content);
                }
              },
              e -> future.fail(getSushiError(e)));
    } catch (Exception e) {
      future.fail(e);
    }

    return future;
  }
}
