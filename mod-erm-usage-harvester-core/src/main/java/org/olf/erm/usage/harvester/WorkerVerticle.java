package org.olf.erm.usage.harvester;

import static org.olf.erm.usage.harvester.Messages.createErrMsgDecode;
import static org.olf.erm.usage.harvester.Messages.createMsgStatus;
import static org.olf.erm.usage.harvester.Messages.createProviderMsg;
import static org.olf.erm.usage.harvester.Messages.createTenantMsg;

import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.Aggregator;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.CounterReports;
import org.folio.rest.jaxrs.model.HarvestingConfig.HarvestVia;
import org.folio.rest.jaxrs.model.HarvestingConfig.HarvestingStatus;
import org.folio.rest.jaxrs.model.Report;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.folio.rest.jaxrs.model.UsageDataProviders;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkerVerticle extends AbstractVerticle {

  private static final Logger LOG = LoggerFactory.getLogger(WorkerVerticle.class);
  private static final String QUERY_PARAM = "query";
  private static final String CONFIG_MODULE = "ERM-USAGE-HARVESTER";
  private static final String CONFIG_CODE = "maxFailedAttempts";

  private String okapiUrl;
  private String reportsPath;
  private String providerPath;
  private String aggregatorPath;
  private String modConfigPath;
  private Token token;
  private String providerId = null;
  private int maxFailedAttempts = 5;
  private WebClient client;

  private void logInfo(Supplier<String> logMessage) {
    if (LOG.isInfoEnabled()) {
      LOG.info(logMessage.get());
    }
  }

  private void logError(Supplier<String> logMessage, Throwable t) {
    if (LOG.isErrorEnabled()) {
      LOG.error(logMessage.get(), t);
    }
  }

  private void logError(Supplier<String> logMessage) {
    if (LOG.isErrorEnabled()) {
      LOG.error(logMessage.get());
    }
  }

  private Handler<AsyncResult<CompositeFuture>> processingCompleteHandler =
      h -> {
        if (h.succeeded()) {
          logInfo(() -> createTenantMsg(token.getTenantId(), "Processing completed"));
        } else {
          logError(
              () ->
                  createTenantMsg(
                      token.getTenantId(), "Error during processing, {}", h.cause().getMessage()));
        }
        vertx.undeploy(this.deploymentID());
      };

  public WorkerVerticle(Token token) {
    this.token = token;
  }

  public WorkerVerticle(Token token, String providerId) {
    this.token = token;
    this.providerId = providerId;
  }

  public Future<UsageDataProviders> getActiveProviders() {
    final String url = okapiUrl + providerPath;
    final String queryStr =
        String.format("(harvestingConfig.harvestingStatus=%s)", HarvestingStatus.ACTIVE);
    logInfo(() -> createTenantMsg(token.getTenantId(), "getting providers"));

    Promise<UsageDataProviders> promise = Promise.promise();

    client
        .requestAbs(HttpMethod.GET, url)
        .putHeader(XOkapiHeaders.TOKEN, token.getToken())
        .putHeader(XOkapiHeaders.TENANT, token.getTenantId())
        .putHeader(HttpHeaders.ACCEPT, MediaType.JSON_UTF_8.toString())
        .setQueryParam("limit", String.valueOf(Integer.MAX_VALUE))
        .setQueryParam("offset", "0")
        .setQueryParam(QUERY_PARAM, queryStr)
        .send(
            ar -> {
              if (ar.succeeded()) {
                if (ar.result().statusCode() == 200) {
                  UsageDataProviders entity;
                  try {
                    entity = ar.result().bodyAsJson(UsageDataProviders.class);
                    logInfo(
                        () ->
                            createTenantMsg(
                                token.getTenantId(),
                                "total providers: {}",
                                entity.getTotalRecords()));
                    promise.complete(entity);
                  } catch (Exception e) {
                    promise.fail(
                        createTenantMsg(
                            token.getTenantId(), createErrMsgDecode(url, e.getMessage())));
                  }
                } else {
                  promise.fail(
                      createTenantMsg(
                          token.getTenantId(),
                          createMsgStatus(
                              ar.result().statusCode(), ar.result().statusMessage(), url)));
                }
              } else {
                promise.fail(
                    createTenantMsg(token.getTenantId(), "error: {}", ar.cause().getMessage()));
              }
            });
    return promise.future();
  }

  public Future<AggregatorSetting> getAggregatorSetting(UsageDataProvider provider) {
    Promise<AggregatorSetting> promise = Promise.promise();

    Aggregator aggregator = provider.getHarvestingConfig().getAggregator();
    if (aggregator == null || aggregator.getId() == null) {
      return Future.failedFuture(
          createTenantMsg(
              token.getTenantId(), "no aggregator found for provider {}", provider.getLabel()));
    }

    final String aggrUrl = okapiUrl + aggregatorPath + "/" + aggregator.getId();
    client
        .requestAbs(HttpMethod.GET, aggrUrl)
        .putHeader(XOkapiHeaders.TOKEN, token.getToken())
        .putHeader(XOkapiHeaders.TENANT, token.getTenantId())
        .putHeader(HttpHeaders.ACCEPT, MediaType.JSON_UTF_8.toString())
        .send(
            ar -> {
              if (ar.succeeded()) {
                if (ar.result().statusCode() == 200) {
                  try {
                    AggregatorSetting setting = ar.result().bodyAsJson(AggregatorSetting.class);
                    logInfo(
                        () ->
                            createTenantMsg(
                                token.getTenantId(),
                                "got AggregatorSetting for id: {}",
                                aggregator.getId()));
                    promise.complete(setting);
                  } catch (Exception e) {
                    promise.fail(
                        createTenantMsg(
                            token.getTenantId(), createErrMsgDecode(aggrUrl, e.getMessage())));
                  }
                } else {
                  promise.fail(
                      createTenantMsg(
                          token.getTenantId(),
                          createMsgStatus(
                              ar.result().statusCode(), ar.result().statusMessage(), aggrUrl)));
                }
              } else {
                promise.fail(
                    createTenantMsg(
                        token.getTenantId(),
                        "failed getting AggregatorSetting for id: {}, {}",
                        aggregator.getId(),
                        ar.cause().getMessage()));
              }
            });
    return promise.future();
  }

  public CounterReport createCounterReport(
      String reportData, String reportName, UsageDataProvider provider, YearMonth yearMonth) {
    CounterReport cr = new CounterReport();
    cr.setId(UUID.randomUUID().toString());
    cr.setYearMonth(yearMonth.toString());
    cr.setReportName(reportName);
    cr.setRelease(
        provider
            .getHarvestingConfig()
            .getReportRelease()
            .toString()); // TODO: check release for null
    cr.setProviderId(provider.getId());
    cr.setDownloadTime(Date.from(Instant.now()));
    if (reportData != null) {
      cr.setReport(Json.decodeValue(reportData, Report.class));
    } else {
      cr.setFailedAttempts(1);
    }
    return cr;
  }

  public Future<ServiceEndpoint> getServiceEndpoint(UsageDataProvider provider) {
    Promise<AggregatorSetting> aggrPromise = Promise.promise();
    Promise<ServiceEndpoint> sepPromise = Promise.promise();

    boolean useAggregator =
        provider.getHarvestingConfig().getHarvestVia().equals(HarvestVia.AGGREGATOR);
    Aggregator aggregator = provider.getHarvestingConfig().getAggregator();
    // Complete aggrPromise if aggregator is not set.. aka skip it
    if (useAggregator && aggregator != null && aggregator.getId() != null) {
      getAggregatorSetting(provider).onComplete(aggrPromise);
    } else {
      aggrPromise.complete(null);
    }

    return aggrPromise
        .future()
        .compose(
            as -> {
              ServiceEndpoint sep = ServiceEndpoint.create(provider, as);
              if (sep != null) {
                sepPromise.complete(sep);
              } else {
                sepPromise.fail(
                    createTenantMsg(
                        token.getTenantId(),
                        createProviderMsg(
                            provider.getLabel(), "No service implementation available")));
              }
              return sepPromise.future();
            });
  }

  /**
   * Returns List of months that that dont need fetching.
   *
   * @param providerId providerId
   * @param reportName reportType
   * @param start start month
   * @param end end month
   */
  public Future<List<YearMonth>> getValidMonths(
      String providerId, String reportName, YearMonth start, YearMonth end) {
    Promise<List<YearMonth>> promise = Promise.promise();

    String queryStr =
        String.format(
            "(providerId=%s AND "
                + "((cql.allRecords=1 NOT failedAttempts=\"\") OR (failedAttempts>=%s)) AND "
                + "reportName==%s AND yearMonth>=%s AND yearMonth<=%s)",
            providerId, maxFailedAttempts, reportName, start.toString(), end.toString());
    client
        .getAbs(okapiUrl + reportsPath)
        .putHeader(XOkapiHeaders.TOKEN, token.getToken())
        .putHeader(XOkapiHeaders.TENANT, token.getTenantId())
        .putHeader(HttpHeaders.ACCEPT, MediaType.JSON_UTF_8.toString())
        .setQueryParam(QUERY_PARAM, queryStr)
        .setQueryParam("tiny", "true")
        .setQueryParam("offset", "0")
        .setQueryParam("limit", String.valueOf(Integer.MAX_VALUE))
        .send(
            ar -> {
              if (ar.succeeded()) {
                if (ar.result().statusCode() == 200) {
                  CounterReports result = ar.result().bodyAsJson(CounterReports.class);
                  List<YearMonth> availableMonths = new ArrayList<>();
                  result
                      .getCounterReports()
                      .forEach(r -> availableMonths.add(YearMonth.parse(r.getYearMonth())));
                  promise.complete(availableMonths);
                } else {
                  promise.fail(
                      createMsgStatus(
                          ar.result().statusCode(),
                          ar.result().statusMessage(),
                          okapiUrl + reportsPath));
                }
              } else {
                promise.fail(ar.cause());
              }
            });

    return promise.future();
  }

  /**
   * Returns a List of FetchItems/Months that need fetching.
   *
   * @param provider UsageDataProvider
   */
  public Future<List<FetchItem>> getFetchList(UsageDataProvider provider) {
    // check if harvesting status is 'active'
    if (!provider.getHarvestingConfig().getHarvestingStatus().equals(HarvestingStatus.ACTIVE)) {
      logInfo(
          () ->
              createTenantMsg(
                  token.getTenantId(),
                  "skipping {} as harvesting status is {}",
                  provider.getLabel(),
                  provider.getHarvestingConfig().getHarvestingStatus()));
      return Future.failedFuture("Harvesting not active");
    }

    Promise<List<FetchItem>> promise = Promise.promise();

    // TODO: check for date Strings to not be empty
    // TODO: check for nulls
    YearMonth startMonth =
        DateUtil.getYearMonthFromString(provider.getHarvestingConfig().getHarvestingStart());
    YearMonth endMonth =
        DateUtil.getYearMonthFromStringWithLimit(
            provider.getHarvestingConfig().getHarvestingEnd(), YearMonth.now().minusMonths(1));

    List<FetchItem> fetchList = new ArrayList<>();

    @SuppressWarnings({"rawtypes", "java:S3740"})
    List<Future> futures = new ArrayList<>();
    provider
        .getHarvestingConfig()
        .getRequestedReports()
        .forEach(
            reportName ->
                futures.add(
                    getValidMonths(provider.getId(), reportName, startMonth, endMonth)
                        .map(
                            list -> {
                              List<YearMonth> arrayList =
                                  DateUtil.getYearMonths(
                                      provider.getHarvestingConfig().getHarvestingStart(),
                                      provider.getHarvestingConfig().getHarvestingEnd());
                              arrayList.removeAll(list);
                              arrayList.forEach(
                                  li -> {
                                    FetchItem fetchItem =
                                        new FetchItem(
                                            reportName,
                                            li.atDay(1).toString(),
                                            li.atEndOfMonth().toString());
                                    LOG.info("Created FetchItem: {}", fetchItem);
                                    fetchList.add(fetchItem);
                                  });
                              return Future.succeededFuture();
                            })));

    CompositeFuture.all(futures)
        .onComplete(
            ar -> {
              if (ar.succeeded()) {
                promise.complete(fetchList);
              } else {
                promise.fail(ar.cause());
              }
            });

    return promise.future();
  }

  @SuppressWarnings({"rawtypes", "java:S3740"})
  public Future<List<Future>> fetchAndPostReports(UsageDataProvider provider) {
    logInfo(
        () -> createTenantMsg(token.getTenantId(), "processing provider: {}", provider.getLabel()));

    List<Future> futList = new ArrayList<>();
    futList.add(updateUDPLastHarvestingDate(provider));
    Promise<List<Future>> promise = Promise.promise();

    Future<ServiceEndpoint> sep = getServiceEndpoint(provider);
    sep.compose(s -> getFetchList(provider))
        .compose(
            list -> {
              if (list.isEmpty()) {
                logInfo(
                    () ->
                        createTenantMsg(
                            token.getTenantId(),
                            createProviderMsg(
                                provider.getLabel(), "No reports need to be fetched.")));
              }
              list.forEach(
                  li -> {
                    Promise complete = Promise.promise();
                    futList.add(complete.future());
                    sep.result()
                        .fetchSingleReport(li.getReportType(), li.getBegin(), li.getEnd())
                        .onComplete(
                            h -> {
                              CounterReport report;
                              LocalDate parse = LocalDate.parse(li.getBegin());
                              YearMonth month = YearMonth.of(parse.getYear(), parse.getMonth());
                              if (h.succeeded()) {
                                report =
                                    createCounterReport(
                                        h.result(), li.getReportType(), provider, month);
                              } else {
                                report =
                                    createCounterReport(null, li.getReportType(), provider, month);
                                report.setFailedReason(h.cause().getMessage());
                                logError(
                                    () ->
                                        createTenantMsg(
                                            token.getTenantId(),
                                            createProviderMsg(
                                                provider.getLabel(),
                                                "{}, {}",
                                                li,
                                                h.cause().getMessage())));
                              }
                              postReport(report)
                                  .onComplete(
                                      h2 -> {
                                        complete.complete();
                                        if (h2.failed()) {
                                          LOG.error(h2.cause().getMessage());
                                        }
                                      });
                            });
                  });
              promise.complete(futList);
              return Future.<Void>succeededFuture();
            })
        .onComplete(
            h -> {
              if (h.failed()) {
                logError(
                    () ->
                        createTenantMsg(
                            token.getTenantId(),
                            createProviderMsg(provider.getLabel(), h.cause().getMessage())),
                    h.cause());
                promise.complete(Collections.emptyList());
              }
            });
    return promise.future();
  }

  public Future<HttpResponse<Buffer>> postReport(CounterReport report) {
    return getReport(report.getProviderId(), report.getReportName(), report.getYearMonth(), true)
        .compose(
            existing -> {
              if (existing == null) { // no report found
                // POST the report
                return sendReportRequest(HttpMethod.POST, report);
              } else {
                if (report.getFailedAttempts() != null) {
                  report.setFailedAttempts(existing.getFailedAttempts() + 1);
                }
                report.setId(existing.getId());
                return sendReportRequest(HttpMethod.PUT, report);
              }
            });
  }

  public Future<HttpResponse<Buffer>> sendReportRequest(HttpMethod method, CounterReport report) {
    String urlTmp = okapiUrl + reportsPath;
    if (!method.equals(HttpMethod.POST) && !method.equals(HttpMethod.PUT)) {
      return Future.failedFuture("HttpMethod not supported");
    } else if (method.equals(HttpMethod.PUT)) {
      urlTmp += "/" + report.getId();
    }
    final String url = urlTmp;

    final Promise<HttpResponse<Buffer>> promise = Promise.promise();

    logInfo(
        () -> createTenantMsg(token.getTenantId(), "posting report with id {}", report.getId()));

    client
        .requestAbs(method, url)
        .putHeader(XOkapiHeaders.TOKEN, token.getToken())
        .putHeader(XOkapiHeaders.TENANT, token.getTenantId())
        .putHeader(HttpHeaders.ACCEPT, MediaType.PLAIN_TEXT_UTF_8.toString())
        .sendJsonObject(
            JsonObject.mapFrom(report),
            ar -> {
              if (ar.succeeded()) {
                logInfo(
                    () ->
                        createTenantMsg(
                            token.getTenantId(),
                            createMsgStatus(
                                ar.result().statusCode(), ar.result().statusMessage(), url)));
                promise.complete(ar.result());
              } else {
                logError(
                    () ->
                        createTenantMsg(
                            token.getTenantId(),
                            "error posting report: {}",
                            ar.cause().getMessage()),
                    ar.cause());
                promise.fail(ar.cause());
              }
            });

    return promise.future();
  }

  /** completes with the found report or null if none is found fails otherwise */
  public Future<CounterReport> getReport(
      String providerId, String reportName, String month, boolean tiny) {
    Promise<CounterReport> promise = Promise.promise();
    String queryStr =
        String.format(
            "(providerId=%s AND yearMonth=%s AND reportName==%s)", providerId, month, reportName);
    client
        .getAbs(okapiUrl + reportsPath)
        .putHeader(XOkapiHeaders.TOKEN, token.getToken())
        .putHeader(XOkapiHeaders.TENANT, token.getTenantId())
        .putHeader(HttpHeaders.ACCEPT, MediaType.JSON_UTF_8.toString())
        .setQueryParam(QUERY_PARAM, queryStr)
        .setQueryParam("tiny", String.valueOf(tiny))
        .send(
            handler -> {
              if (handler.succeeded()) {
                if (handler.result().statusCode() == 200) {
                  CounterReports collection = handler.result().bodyAsJson(CounterReports.class);
                  if (collection.getCounterReports().isEmpty()) {
                    promise.complete(null);
                  } else if (collection.getCounterReports().size() == 1) {
                    promise.complete(collection.getCounterReports().get(0));
                  } else {
                    promise.fail(
                        createTenantMsg(
                            token.getTenantId(),
                            createProviderMsg(
                                providerId,
                                "Too many results for {}, {} not processed",
                                reportName,
                                month)));
                  }
                } else {
                  promise.fail("received status code " + handler.result().statusCode());
                }
              } else {
                promise.fail(handler.cause());
              }
            });
    return promise.future();
  }

  public void run() {
    getActiveProviders()
        .compose(
            providers -> {
              @SuppressWarnings({"rawtypes", "java:S3740"})
              List<Future> complete = new ArrayList<>();
              providers
                  .getUsageDataProviders()
                  .forEach(
                      p ->
                          complete.add(this.fetchAndPostReports(p).compose(CompositeFuture::join)));
              CompositeFuture.join(complete).onComplete(processingCompleteHandler);
              return Future.succeededFuture();
            })
        .onComplete(
            h -> {
              if (h.failed()) {
                LOG.error(
                    "Verticle has failed, id: {}, {}", this.deploymentID(), h.cause().getMessage());
                vertx.undeploy(this.deploymentID());
              }
            });
  }

  public void runSingleProvider() {
    client
        .getAbs(okapiUrl + providerPath + "/" + providerId)
        .putHeader(XOkapiHeaders.TOKEN, token.getToken())
        .putHeader(XOkapiHeaders.TENANT, token.getTenantId())
        .putHeader(HttpHeaders.ACCEPT, MediaType.JSON_UTF_8.toString())
        .send(
            h -> {
              if (h.succeeded()) {
                if (h.result().statusCode() == 200) {
                  UsageDataProvider provider = h.result().bodyAsJson(UsageDataProvider.class);
                  if (provider
                      .getHarvestingConfig()
                      .getHarvestingStatus()
                      .equals(HarvestingStatus.ACTIVE)) {
                    fetchAndPostReports(provider)
                        .compose(CompositeFuture::join)
                        .onComplete(processingCompleteHandler);
                  } else {
                    logError(
                        () ->
                            createTenantMsg(
                                token.getTenantId(),
                                createProviderMsg(
                                    provider.getLabel(), "HarvestingStatus not ACTIVE")));
                    vertx.undeploy(this.deploymentID());
                  }
                } else {
                  logError(
                      () ->
                          createTenantMsg(
                              token.getTenantId(),
                              createProviderMsg(
                                  providerId,
                                  createMsgStatus(
                                      h.result().statusCode(),
                                      h.result().statusMessage(),
                                      providerPath))));
                  vertx.undeploy(this.deploymentID());
                }
              } else {
                LOG.error(h.cause().getMessage(), h.cause());
                vertx.undeploy(this.deploymentID());
              }
            });
  }

  @Override
  public void stop() throws Exception {
    super.stop();
    logInfo(() -> createTenantMsg(token.getTenantId(), "undeployed WorkerVerticle"));
  }

  @Override
  public void start() throws Exception {
    super.start();

    okapiUrl = config().getString("okapiUrl");
    reportsPath = config().getString("reportsPath");
    providerPath = config().getString("providerPath");
    aggregatorPath = config().getString("aggregatorPath");
    modConfigPath = config().getString("modConfigurationPath");
    client = WebClient.create(vertx);

    logInfo(() -> createTenantMsg(token.getTenantId(), "deployed WorkerVericle"));

    Future<String> limit = getModConfigurationValue(CONFIG_MODULE, CONFIG_CODE, "5");

    limit.onComplete(
        ar -> {
          if (ar.succeeded()) {
            maxFailedAttempts = Integer.parseInt(ar.result());
          }
          logInfo(
              () ->
                  createTenantMsg(
                      token.getTenantId(), "using maxFailedAttempts={}", maxFailedAttempts));

          boolean isTesting = config().getBoolean("testing", false);
          if (!isTesting) {
            if (providerId == null) run();
            else runSingleProvider();
          } else {
            LOG.info("TEST ENV");
          }
        });
  }

  public Future<String> getModConfigurationValue(String module, String code, String defaultValue) {
    Promise<String> promise = Promise.promise();
    final String queryStr = String.format("(module = %s and configName = %s)", module, code);
    client
        .getAbs(okapiUrl + modConfigPath)
        .setQueryParam(QUERY_PARAM, queryStr)
        .putHeader(XOkapiHeaders.TENANT, token.getTenantId())
        .putHeader(XOkapiHeaders.TOKEN, token.getToken())
        .putHeader(HttpHeaders.ACCEPT, MediaType.JSON_UTF_8.toString())
        .timeout(5000)
        .send(
            ar -> {
              if (ar.succeeded()) {
                if (ar.result().statusCode() == 200) {
                  JsonArray configs =
                      ar.result().bodyAsJsonObject().getJsonArray("configs", new JsonArray());
                  if (configs.size() == 1) {
                    promise.complete(configs.getJsonObject(0).getString("value"));
                  }
                } else {
                  logInfo(
                      () ->
                          createMsgStatus(
                              ar.result().statusCode(),
                              ar.result().statusMessage(),
                              "from configuration module"));
                }
              }
              promise.tryComplete(defaultValue);
            });
    return promise.future();
  }

  public Future<Void> updateUDPLastHarvestingDate(UsageDataProvider udp) {
    Promise<Void> promise = Promise.promise();
    udp.setHarvestingDate(Date.from(Instant.now()));
    String putUDPUrl = okapiUrl + providerPath + "/" + udp.getId();
    client
        .putAbs(putUDPUrl)
        .putHeader(XOkapiHeaders.TENANT, token.getTenantId())
        .putHeader(XOkapiHeaders.TOKEN, token.getToken())
        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString())
        .putHeader(HttpHeaders.ACCEPT, "text/plain")
        .timeout(5000)
        .sendJson(
            udp,
            ar -> {
              if (ar.succeeded()) {
                if (ar.result().statusCode() == 204) {
                  logInfo(
                      () ->
                          createTenantMsg(
                              token.getTenantId(),
                              "Updated harvestingDate for UsageDataProvider {}[{}]",
                              udp.getId(),
                              udp.getLabel()));
                  promise.complete();
                } else {
                  promise.fail(
                      createProviderMsg(
                          udp.getLabel(),
                          "Failed updating harvestingDate: {}",
                          createMsgStatus(
                              ar.result().statusCode(), ar.result().statusMessage(), putUDPUrl)));
                }
              } else {
                promise.fail(
                    createProviderMsg(
                        udp.getLabel(),
                        "Failed updating harvestingDate: {}",
                        ar.cause().getMessage()));
              }
            });
    return promise.future();
  }
}
