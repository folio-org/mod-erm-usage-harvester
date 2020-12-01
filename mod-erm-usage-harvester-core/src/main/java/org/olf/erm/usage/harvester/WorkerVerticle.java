package org.olf.erm.usage.harvester;

import static org.olf.erm.usage.harvester.Messages.createErrMsgDecode;
import static org.olf.erm.usage.harvester.Messages.createMsgStatus;
import static org.olf.erm.usage.harvester.Messages.createProviderMsg;
import static org.olf.erm.usage.harvester.Messages.createTenantMsg;
import static org.olf.erm.usage.harvester.Messages.createTenantProviderMsg;

import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.observers.DisposableCompletableObserver;
import io.reactivex.schedulers.Schedulers;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
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
import org.olf.erm.usage.harvester.endpoints.InvalidReportException;
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

  private CompletableObserver createCompletableObserver() {
    return new DisposableCompletableObserver() {
      @Override
      public void onComplete() {
        logInfo(() -> createTenantMsg(token.getTenantId(), "Processing completed"));
      }

      @Override
      public void onError(Throwable e) {
        logError(
            () ->
                createTenantMsg(token.getTenantId(), "Error during processing, {}", e.getMessage()),
            e);
      }
    };
  }

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
                                  DateUtil.getYearMonths(startMonth, endMonth);
                              arrayList.removeAll(list);
                              arrayList.forEach(
                                  li -> {
                                    FetchItem fetchItem =
                                        new FetchItem(
                                            reportName,
                                            li.atDay(1).toString(),
                                            li.atEndOfMonth().toString());
                                    LOG.debug("Created FetchItem: {}", fetchItem);
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

  private <T> Single<T> wrapFuture(Future<T> future) {
    return io.vertx.reactivex.core.Future.<T>newInstance(future).rxOnComplete();
  }

  private Observable<List<CounterReport>> processItems(
      UsageDataProvider provider,
      ServiceEndpoint sep,
      List<FetchItem> items,
      Scheduler scheduler,
      ThreadPoolExecutor executor) {

    return Observable.fromIterable(items)
        .doOnNext(
            fetchItem ->
                logInfo(
                    createTenantProviderMsg(
                        token.getTenantId(), provider.getLabel(), "processing {}", fetchItem)))
        .flatMap(
            fetchItem ->
                Observable.defer(
                        () ->
                            io.vertx.reactivex.core.Future.<List<CounterReport>>newInstance(
                                    sep.fetchReport(
                                        fetchItem.getReportType(),
                                        fetchItem.getBegin(),
                                        fetchItem.getEnd()))
                                .rxOnComplete()
                                .toObservable())
                    .subscribeOn(scheduler)
                    .retry(
                        3,
                        t -> {
                          if ((t instanceof InvalidReportException)
                              && t.getMessage().contains("requests")) {
                            logInfo(
                                createTenantProviderMsg(
                                    token.getTenantId(),
                                    provider.getLabel(),
                                    "Too many requests.. retrying {}",
                                    fetchItem));
                            executor.setCorePoolSize(1);
                            executor.setMaximumPoolSize(1);
                            return true;
                          } else {
                            return false;
                          }
                        })
                    .onErrorResumeNext(
                        t -> {
                          logInfo(
                              createTenantProviderMsg(
                                  token.getTenantId(),
                                  provider.getLabel(),
                                  "received {}",
                                  t.getMessage()));
                          if (!(t instanceof InvalidReportException)) {
                            // handle generic failues
                            List<CounterReport> counterReportList =
                                DateUtil.getYearMonths(fetchItem.getBegin(), fetchItem.getEnd())
                                    .stream()
                                    .map(
                                        ym ->
                                            createCounterReport(
                                                    null, fetchItem.getReportType(), provider, ym)
                                                .withFailedReason(t.getMessage()))
                                    .collect(Collectors.toList());
                            return Observable.just(counterReportList);
                          }
                          List<FetchItem> expand = FetchListUtil.expand(fetchItem);
                          // handle failed single month
                          if (expand.size() <= 1) {
                            logInfo(
                                createTenantProviderMsg(
                                    token.getTenantId(),
                                    provider.getLabel(),
                                    "Returning null for {}",
                                    fetchItem));
                            return Observable.just(
                                List.of(
                                    createCounterReport(
                                            null,
                                            fetchItem.getReportType(),
                                            provider,
                                            DateUtil.getYearMonthFromString(fetchItem.getBegin()))
                                        .withFailedReason(t.getMessage())));
                          } else {
                            // handle failes multiple months
                            logInfo(
                                createTenantProviderMsg(
                                    token.getTenantId(),
                                    provider.getLabel(),
                                    "Expanded {} into {} FetchItems",
                                    fetchItem,
                                    expand.size()));
                            return processItems(provider, sep, expand, scheduler, executor);
                          }
                        }));
  }

  private String counterReportToString(CounterReport cr) {
    return cr.getReportName() + " " + cr.getYearMonth();
  }

  public Completable fetchAndPostReportsRx(UsageDataProvider provider) {
    logInfo(
        () -> createTenantMsg(token.getTenantId(), "processing provider: {}", provider.getLabel()));

    wrapFuture(updateUDPLastHarvestingDate(provider))
        .doOnError(t -> LOG.error(t.getMessage()))
        .ignoreElement()
        .onErrorComplete()
        .subscribe();

    Single<ServiceEndpoint> sepSingle = wrapFuture(getServiceEndpoint(provider));
    Single<List<FetchItem>> fetchListSingle =
        wrapFuture(getFetchList(provider))
            .map(
                list -> {
                  if (list.isEmpty()) {
                    logInfo(
                        () ->
                            createTenantMsg(
                                token.getTenantId(),
                                createProviderMsg(
                                    provider.getLabel(), "No reports need to be fetched.")));
                  }
                  return list;
                })
            .map(FetchListUtil::collapse);

    return sepSingle
        .zipWith(
            fetchListSingle,
            (s, l) -> {
              ThreadPoolExecutor threadPoolExecutor =
                  new ThreadPoolExecutor(4, 4, 1, TimeUnit.MINUTES, new LinkedBlockingQueue<>());
              return processItems(
                  provider, s, l, Schedulers.from(threadPoolExecutor), threadPoolExecutor);
            })
        .flatMapObservable(listObservable -> listObservable)
        .flatMap(Observable::fromIterable)
        .doOnNext(cr -> LOG.info("Received: {}", counterReportToString(cr)))
        .flatMapCompletable(
            cr ->
                wrapFuture(postReport(cr))
                    .ignoreElement()
                    .doOnError(t -> LOG.error(t.getMessage()))
                    .onErrorComplete());
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

  public void runRx() {
    wrapFuture(getActiveProviders())
        .map(UsageDataProviders::getUsageDataProviders)
        .flatMapObservable(Observable::fromIterable)
        .flatMapCompletable(this::fetchAndPostReportsRx)
        .doAfterTerminate(() -> vertx.undeploy(this.deploymentID()))
        .subscribe(createCompletableObserver());
  }

  public void runSingleProviderRx() {
    client
        .getAbs(okapiUrl + providerPath + "/" + providerId)
        .putHeader(XOkapiHeaders.TOKEN, token.getToken())
        .putHeader(XOkapiHeaders.TENANT, token.getTenantId())
        .putHeader(HttpHeaders.ACCEPT, MediaType.JSON_UTF_8.toString())
        .rxSend()
        .map(
            resp -> {
              if (resp.statusCode() == 200) {
                return resp.bodyAsJson(UsageDataProvider.class);
              } else {
                throw new Exception(
                    createProviderMsg(
                        providerId,
                        createMsgStatus(resp.statusCode(), resp.statusMessage(), providerPath)));
              }
            })
        .map(
            udp -> {
              if (udp.getHarvestingConfig().getHarvestingStatus().equals(HarvestingStatus.ACTIVE)) {
                return udp;
              } else {
                throw new Exception(
                    createProviderMsg(udp.getLabel(), "HarvestingStatus not ACTIVE"));
              }
            })
        .flatMapCompletable(this::fetchAndPostReportsRx)
        .doFinally(() -> vertx.undeploy(this.deploymentID()))
        .subscribe(createCompletableObserver());
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
            if (providerId == null) runRx();
            else runSingleProviderRx();
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
