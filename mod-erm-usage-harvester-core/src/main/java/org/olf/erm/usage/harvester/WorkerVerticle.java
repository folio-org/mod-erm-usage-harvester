package org.olf.erm.usage.harvester;

import static org.olf.erm.usage.harvester.Messages.ERR_MSG_DECODE;
import static org.olf.erm.usage.harvester.Messages.ERR_MSG_STATUS;

import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
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
import org.slf4j.helpers.MessageFormatter;

public class WorkerVerticle extends AbstractVerticle {

  private static final Logger LOG = LoggerFactory.getLogger(WorkerVerticle.class);
  private static final String TENANT = "Tenant: ";
  private static final String QUERY_PARAM = "query";
  private static final String CONFIG_MODULE = "ERM-USAGE-HARVESTER";
  private static final String CONFIG_CODE = "maxFailedAttempts";
  private static final String CONFIG_PATH = "/configurations/entries";

  private String okapiUrl;
  private String reportsPath;
  private String providerPath;
  private String aggregatorPath;
  private Token token;
  private String providerId = null;
  private int maxFailedAttempts = 5;

  private Handler<AsyncResult<CompositeFuture>> processingCompleteHandler =
      h -> {
        if (h.succeeded()) {
          LOG.info("Tenant: {}, Processing completed", token.getTenantId());
          vertx.undeploy(this.deploymentID());
        } else {
          LOG.error(
              "Tenant: {}, Error during processing, {}",
              token.getTenantId(),
              h.cause().getMessage(),
              h.cause());
        }
      };

  public WorkerVerticle(Token token) {
    this.token = token;
  }

  public WorkerVerticle(Token token, String providerId) {
    this.token = token;
    this.providerId = providerId;
  }

  private String format(String pattern, Object... args) {
    return MessageFormatter.arrayFormat(pattern, args).getMessage();
  }

  // TODO: handle limits > 30
  public Future<UsageDataProviders> getActiveProviders() {
    final String logprefix = TENANT + token.getTenantId() + ", {}";
    final String url = okapiUrl + providerPath;
    final String queryStr =
        String.format("(harvestingConfig.harvestingStatus=%s)", HarvestingStatus.ACTIVE);
    LOG.info(logprefix, "getting providers");

    Future<UsageDataProviders> future = Future.future();

    WebClient client = WebClient.create(vertx);
    client
        .requestAbs(HttpMethod.GET, url)
        .putHeader(XOkapiHeaders.TOKEN, token.getToken())
        .putHeader(XOkapiHeaders.TENANT, token.getTenantId())
        .putHeader(HttpHeaders.ACCEPT, MediaType.JSON_UTF_8.toString())
        .setQueryParam("limit", "30")
        .setQueryParam("offset", "0")
        .setQueryParam(QUERY_PARAM, queryStr)
        .send(
            ar -> {
              client.close();
              if (ar.succeeded()) {
                if (ar.result().statusCode() == 200) {
                  UsageDataProviders entity;
                  try {
                    entity = ar.result().bodyAsJson(UsageDataProviders.class);
                    LOG.info(logprefix, "total providers: " + entity.getTotalRecords());
                    future.complete(entity);
                  } catch (Exception e) {
                    future.fail(
                        format(logprefix, String.format(ERR_MSG_DECODE, url, e.getMessage())));
                  }
                } else {
                  future.fail(
                      format(
                          logprefix,
                          String.format(
                              ERR_MSG_STATUS,
                              ar.result().statusCode(),
                              ar.result().statusMessage(),
                              url)));
                }
              } else {
                future.fail(format(logprefix, "error: " + ar.cause().getMessage()));
              }
            });
    return future;
  }

  public Future<AggregatorSetting> getAggregatorSetting(UsageDataProvider provider) {
    final String logprefix = TENANT + token.getTenantId() + ", {}";
    Future<AggregatorSetting> future = Future.future();

    Aggregator aggregator = provider.getHarvestingConfig().getAggregator();
    if (aggregator == null || aggregator.getId() == null) {
      return Future.failedFuture(
          format(logprefix, "no aggregator found for provider " + provider.getLabel()));
    }

    final String aggrUrl = okapiUrl + aggregatorPath + "/" + aggregator.getId();
    WebClient client = WebClient.create(vertx);
    client
        .requestAbs(HttpMethod.GET, aggrUrl)
        .putHeader(XOkapiHeaders.TOKEN, token.getToken())
        .putHeader(XOkapiHeaders.TENANT, token.getTenantId())
        .putHeader(HttpHeaders.ACCEPT, MediaType.JSON_UTF_8.toString())
        .send(
            ar -> {
              client.close();
              if (ar.succeeded()) {
                if (ar.result().statusCode() == 200) {
                  try {
                    AggregatorSetting setting = ar.result().bodyAsJson(AggregatorSetting.class);
                    LOG.info(logprefix, "got AggregatorSetting for id: " + aggregator.getId());
                    future.complete(setting);
                  } catch (Exception e) {
                    future.fail(
                        format(logprefix, String.format(ERR_MSG_DECODE, aggrUrl, e.getMessage())));
                  }
                } else {
                  future.fail(
                      format(
                          logprefix,
                          String.format(
                              ERR_MSG_STATUS,
                              ar.result().statusCode(),
                              ar.result().statusMessage(),
                              aggrUrl)));
                }
              } else {
                future.fail(
                    format(
                        logprefix,
                        "failed getting AggregatorSetting for id: "
                            + aggregator.getId()
                            + ", "
                            + ar.cause().getMessage()));
              }
            });
    return future;
  }

  public CounterReport createCounterReport(
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

  public Future<ServiceEndpoint> getServiceEndpoint(UsageDataProvider provider) {
    Future<AggregatorSetting> aggrFuture = Future.future();
    Future<ServiceEndpoint> sepFuture = Future.future();

    boolean useAggregator =
        provider.getHarvestingConfig().getHarvestVia().equals(HarvestVia.AGGREGATOR);
    Aggregator aggregator = provider.getHarvestingConfig().getAggregator();
    // Complete aggrFuture if aggregator is not set.. aka skip it
    if (useAggregator && aggregator != null && aggregator.getId() != null) {
      aggrFuture = getAggregatorSetting(provider);
    } else {
      aggrFuture.complete(null);
    }

    aggrFuture.compose(
        as -> {
          ServiceEndpoint sep = ServiceEndpoint.create(provider, as);
          if (sep != null) {
            sepFuture.complete(sep);
          } else {
            sepFuture.fail(
                String.format(
                    "Tenant: %s, Provider: %s, No service implementation available",
                    token.getTenantId(), provider.getLabel()));
          }
        },
        sepFuture);

    return sepFuture;
  }

  /**
   * Returns List of months that that dont need fetching.
   *
   * @param providerId providerId
   * @param reportName reportType
   * @param start start month
   * @param end end month
   * @return
   */
  public Future<List<YearMonth>> getValidMonths(
      String providerId, String reportName, YearMonth start, YearMonth end) {
    Future<List<YearMonth>> future = Future.future();
    WebClient client = WebClient.create(vertx);

    String queryStr =
        String.format(
            "(providerId=%s AND "
                + "((cql.allRecords=1 NOT failedAttempts=\"\") OR (failedAttempts>=%s)) AND "
                + "reportName=%s AND yearMonth>=%s AND yearMonth<=%s)",
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
                  future.complete(availableMonths);
                } else {
                  future.fail(
                      String.format(
                          ERR_MSG_STATUS,
                          ar.result().statusCode(),
                          ar.result().statusMessage(),
                          okapiUrl + reportsPath));
                }
              } else {
                future.fail(ar.cause());
              }
            });

    return future;
  }

  /**
   * Returns a List of FetchItems/Months that need fetching.
   *
   * @param provider UsageDataProvider
   * @return
   */
  public Future<List<FetchItem>> getFetchList(UsageDataProvider provider) {
    final String logprefix = TENANT + token.getTenantId() + ", {}";

    // check if harvesting status is 'active'
    if (!provider.getHarvestingConfig().getHarvestingStatus().equals(HarvestingStatus.ACTIVE)) {
      LOG.info(
          logprefix,
          "skipping "
              + provider.getLabel()
              + " as harvesting status is "
              + provider.getHarvestingConfig().getHarvestingStatus());
      return Future.failedFuture("Harvesting not active");
    }

    Future<List<FetchItem>> future = Future.future();

    YearMonth startMonth =
        DateUtil.getStartMonth(provider.getHarvestingConfig().getHarvestingStart());
    YearMonth endMonth = DateUtil.getEndMonth(provider.getHarvestingConfig().getHarvestingEnd());

    List<FetchItem> fetchList = new ArrayList<>();

    @SuppressWarnings("rawtypes")
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
                                    LOG.info(
                                        "Created FetchItem: "
                                            + fetchItem.reportType
                                            + " "
                                            + fetchItem.begin
                                            + " "
                                            + fetchItem.end);
                                    fetchList.add(fetchItem);
                                  });
                              return Future.succeededFuture();
                            })));

    CompositeFuture.all(futures)
        .setHandler(
            ar -> {
              if (ar.succeeded()) {
                future.complete(fetchList);
              } else {
                future.fail(ar.cause());
              }
            });

    return future;
  }

  @SuppressWarnings("rawtypes")
  public Future<List<Future>> fetchAndPostReports(UsageDataProvider provider) {
    final String logprefix = TENANT + token.getTenantId() + ", {}";
    LOG.info(logprefix, "processing provider: " + provider.getLabel());

    List<Future> futList = new ArrayList<>();
    Future<List<Future>> future = Future.future();

    Future<ServiceEndpoint> sep = getServiceEndpoint(provider);
    sep.compose(s -> getFetchList(provider))
        .compose(
            list -> {
              if (list.isEmpty()) {
                LOG.info(
                    logprefix,
                    "Provider: " + provider.getLabel() + ", No reports need to be fetched.");
              }
              list.forEach(
                  li -> {
                    Future complete = Future.future();
                    futList.add(complete);
                    sep.result()
                        .fetchSingleReport(li.reportType, li.begin, li.end)
                        .setHandler(
                            h -> {
                              CounterReport report;
                              LocalDate parse = LocalDate.parse(li.begin);
                              YearMonth month = YearMonth.of(parse.getYear(), parse.getMonth());
                              if (h.succeeded()) {
                                report =
                                    createCounterReport(h.result(), li.reportType, provider, month);
                              } else {
                                report = createCounterReport(null, li.reportType, provider, month);
                                report.setFailedReason(h.cause().getMessage());
                                LOG.error(
                                    logprefix,
                                    "Provider: "
                                        + provider.getLabel()
                                        + ", "
                                        + li.toString()
                                        + ", "
                                        + h.cause().getMessage());
                              }
                              postReport(report)
                                  .setHandler(
                                      h2 -> {
                                        complete.complete();
                                        if (h2.failed()) {
                                          LOG.error(h2.cause().getMessage());
                                        }
                                      });
                            });
                  });
              future.complete(futList);
              return Future.<Void>succeededFuture();
            })
        .setHandler(
            h -> {
              if (h.failed()) {
                LOG.error(logprefix, "Provider: " + provider.getLabel() + ", " + h.cause());
                future.complete(Collections.emptyList());
              }
            });
    return future;
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
    final String logprefix = TENANT + token.getTenantId() + ", {}";
    String urlTmp = okapiUrl + reportsPath;
    if (!method.equals(HttpMethod.POST) && !method.equals(HttpMethod.PUT)) {
      return Future.failedFuture("HttpMethod not supported");
    } else if (method.equals(HttpMethod.PUT)) {
      urlTmp += "/" + report.getId();
    }
    final String url = urlTmp;

    final Future<HttpResponse<Buffer>> future = Future.future();

    LOG.info(logprefix, "posting report with id " + report.getId());

    WebClient client = WebClient.create(vertx);
    client
        .requestAbs(method, url)
        .putHeader(XOkapiHeaders.TOKEN, token.getToken())
        .putHeader(XOkapiHeaders.TENANT, token.getTenantId())
        .putHeader(HttpHeaders.ACCEPT, MediaType.PLAIN_TEXT_UTF_8.toString())
        .sendJsonObject(
            JsonObject.mapFrom(report),
            ar -> {
              if (ar.succeeded()) {
                LOG.info(
                    logprefix,
                    String.format(
                        ERR_MSG_STATUS,
                        ar.result().statusCode(),
                        ar.result().statusMessage(),
                        url));
                future.complete(ar.result());
              } else {
                LOG.error(ar.cause().getMessage(), ar.cause());
                future.fail(ar.cause());
              }
            });

    return future;
  }

  /** completes with the found report or null if none is found fails otherwise */
  public Future<CounterReport> getReport(
      String providerId, String reportName, String month, boolean tiny) {
    WebClient client = WebClient.create(vertx);
    Future<CounterReport> future = Future.future();
    String queryStr =
        String.format(
            "(providerId=%s AND yearMonth=%s AND reportName=%s)", providerId, month, reportName);
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
                    future.complete(null);
                  } else if (collection.getCounterReports().size() == 1) {
                    future.complete(collection.getCounterReports().get(0));
                  } else {
                    String msg =
                        String.format(
                            "Tenant: %s, Provider: %s, %s",
                            token.getTenantId(),
                            providerId,
                            "Too many results for "
                                + reportName
                                + ", "
                                + month
                                + ", not processed");
                    future.fail(msg);
                  }
                } else {
                  future.fail("received status code " + handler.result().statusCode());
                }
              } else {
                future.fail(handler.cause());
              }
            });
    return future;
  }

  public void run() {
    getActiveProviders()
        .compose(
            providers -> {
              @SuppressWarnings("rawtypes")
              List<Future> complete = new ArrayList<>();
              providers
                  .getUsageDataProviders()
                  .forEach(
                      p ->
                          complete.add(this.fetchAndPostReports(p).compose(CompositeFuture::join)));
              CompositeFuture.join(complete).setHandler(processingCompleteHandler);
              return Future.succeededFuture();
            })
        .setHandler(
            h -> {
              if (h.failed()) {
                LOG.error(
                    "Verticle has failed, id: {}, {}", this.deploymentID(), h.cause().getMessage());
                vertx.undeploy(this.deploymentID());
              }
            });
  }

  public void runSingleProvider() {
    WebClient client = WebClient.create(vertx);
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
                        .setHandler(processingCompleteHandler);
                  } else {
                    LOG.error(
                        TENANT
                            + token.getTenantId()
                            + ", Provider: "
                            + provider.getLabel()
                            + ", HarvestingStatus not ACTIVE");
                    vertx.undeploy(this.deploymentID());
                  }
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
    LOG.info("Tenant: {}, undeployed WorkerVerticle", token.getTenantId());
  }

  @Override
  public void start() throws Exception {
    super.start();

    okapiUrl = config().getString("okapiUrl");
    reportsPath = config().getString("reportsPath");
    providerPath = config().getString("providerPath");
    aggregatorPath = config().getString("aggregatorPath");

    LOG.info("Tenant: {}, deployed WorkerVericle", token.getTenantId());

    Future<String> limit = getModConfigurationValue(CONFIG_MODULE, CONFIG_CODE, "5");

    limit.setHandler(
        ar -> {
          if (ar.succeeded()) {
            maxFailedAttempts = Integer.valueOf(ar.result());
          }
          LOG.info(
              "Tenant: {}, using maxFailedAttempts={}", token.getTenantId(), maxFailedAttempts);

          if (!config().getBoolean("testing", false)) {
            if (providerId == null) run();
            else runSingleProvider();
          } else {
            LOG.info("TEST ENV");
          }
        });
  }

  public Future<String> getModConfigurationValue(String module, String code, String defaultValue) {
    Future<String> future = Future.future();
    final String path = CONFIG_PATH;
    final String cql = String.format("?query=(module = %s and code = %s)", module, code);
    WebClient client = WebClient.create(vertx);
    client
        .getAbs(okapiUrl + path + cql)
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
                    future.complete(configs.getJsonObject(0).getString("value"));
                  }
                } else {
                  LOG.info(
                      "Received status code {} {} from configuration module",
                      ar.result().statusCode(),
                      ar.result().statusMessage());
                }
              }
              if (!future.isComplete()) {
                future.complete(defaultValue);
              }
            });
    return future;
  }
}
