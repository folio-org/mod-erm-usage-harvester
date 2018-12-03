package org.olf.erm.usage.harvester;

import static org.olf.erm.usage.harvester.Messages.ERR_MSG_DECODE;
import static org.olf.erm.usage.harvester.Messages.ERR_MSG_STATUS;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.log4j.Logger;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.Aggregator;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.CounterReports;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.folio.rest.jaxrs.model.UsageDataProvider.HarvestingStatus;
import org.folio.rest.jaxrs.model.UsageDataProviders;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

public class WorkerVerticle extends AbstractVerticle {

  private static final Logger LOG = Logger.getLogger(WorkerVerticle.class);


  private String okapiUrl;
  private String reportsPath;
  private String providerPath;
  private String aggregatorPath;
  private Token token;

  // TODO: handle limits > 30
  public Future<UsageDataProviders> getActiveProviders() {
    final String logprefix = "Tenant: " + token.getTenantId() + ", ";
    final String url = okapiUrl + providerPath;
    final String queryStr = String.format("(harvestingStatus=%s)", HarvestingStatus.ACTIVE);
    LOG.info(logprefix + "getting providers");

    Future<UsageDataProviders> future = Future.future();

    WebClient client = WebClient.create(vertx);
    client.requestAbs(HttpMethod.GET, url)
        .putHeader(XOkapiHeaders.TOKEN, token.getToken())
        .putHeader(XOkapiHeaders.TENANT, token.getTenantId())
        .putHeader(HttpHeaders.ACCEPT, MediaType.JSON_UTF_8.toString())
        .setQueryParam("limit", "30")
        .setQueryParam("offset", "0")
        .setQueryParam("query", queryStr)
        .send(ar -> {
          client.close();
          if (ar.succeeded()) {
            if (ar.result().statusCode() == 200) {
              UsageDataProviders entity;
              try {
                entity = ar.result().bodyAsJson(UsageDataProviders.class);
                LOG.info(logprefix + "total providers: " + entity.getTotalRecords());
                future.complete(entity);
              } catch (Exception e) {
                future.fail(logprefix + String.format(ERR_MSG_DECODE, url, e.getMessage()));
              }
            } else {
              future.fail(logprefix + String.format(ERR_MSG_STATUS, ar.result().statusCode(),
                  ar.result().statusMessage(), url));
            }
          } else {
            future.fail(logprefix + "error: " + ar.cause().getMessage());
          }
        });
    return future;
  }

  public Future<AggregatorSetting> getAggregatorSetting(UsageDataProvider provider) {
    final String logprefix = "Tenant: " + token.getTenantId() + ", ";
    Future<AggregatorSetting> future = Future.future();

    Aggregator aggregator = provider.getAggregator();
    if (aggregator == null || aggregator.getId() == null) {
      return Future
          .failedFuture(logprefix + "no aggregator found for provider " + provider.getLabel());
    }

    final String aggrUrl = okapiUrl + aggregatorPath + "/" + aggregator.getId();
    WebClient client = WebClient.create(vertx);
    client.requestAbs(HttpMethod.GET, aggrUrl)
        .putHeader(XOkapiHeaders.TOKEN, token.getToken())
        .putHeader(XOkapiHeaders.TENANT, token.getTenantId())
        .putHeader(HttpHeaders.ACCEPT, MediaType.JSON_UTF_8.toString())
        .send(ar -> {
          client.close();
          if (ar.succeeded()) {
            if (ar.result().statusCode() == 200) {
              try {
                AggregatorSetting setting = ar.result().bodyAsJson(AggregatorSetting.class);
                LOG.info(logprefix + "got AggregatorSetting for id: " + aggregator.getId());
                future.complete(setting);
              } catch (Exception e) {
                future.fail(logprefix + String.format(ERR_MSG_DECODE, aggrUrl, e.getMessage()));
              }
            } else {
              future.fail(logprefix + String.format(ERR_MSG_STATUS, ar.result().statusCode(),
                  ar.result().statusMessage(), aggrUrl));
            }
          } else {
            future.fail(logprefix + "failed getting AggregatorSetting for id: " + aggregator.getId()
                + ", " + ar.cause().getMessage());
          }
        });
    return future;
  }

  // TODO: move

  public CounterReport createCounterReport(String reportData, String reportName,
      UsageDataProvider provider, YearMonth yearMonth) {
    CounterReport cr = new CounterReport();
    cr.setId(UUID.randomUUID().toString());
    cr.setYearMonth(yearMonth.toString());
    cr.setReportName(reportName);
    cr.setPlatformId(provider.getPlatformId());
    cr.setCustomerId(provider.getCustomerId());
    cr.setRelease(provider.getReportRelease().toString()); // TODO: update release to be a integer
    cr.setDownloadTime(Date.from(Instant.now())); // FIXME
    cr.setVendorId(provider.getVendorId());
    if (reportData != null) {
      cr.setFormat("???"); // FIXME
      cr.setReport(reportData);
    } else {
      cr.setFailedAttempts(1);
    }
    return cr;
  }

  public Future<ServiceEndpoint> getServiceEndpoint(UsageDataProvider provider) {
    Future<AggregatorSetting> aggrFuture = Future.future();
    Future<ServiceEndpoint> sepFuture = Future.future();

    Aggregator aggregator = provider.getAggregator();
    // Complete aggrFuture if aggregator is not set.. aka skip it
    if (aggregator != null) {
      aggrFuture = getAggregatorSetting(provider);
    } else {
      aggrFuture.complete(null);
    }

    aggrFuture.compose(as -> {
      ServiceEndpoint sep = ServiceEndpoint.create(provider, as);
      sepFuture.complete(sep);
    }, sepFuture);

    return sepFuture;
  }

  public Future<List<YearMonth>> getValidMonths(String vendorId, String reportName, YearMonth start,
      YearMonth end) {
    Future<List<YearMonth>> future = Future.future();
    WebClient client = WebClient.create(vertx);

    // TODO: report="" or NOT failedAttempts=""
    String queryStr = String.format(
        "(vendorId=%s AND report=\"\" AND reportName=%s AND yearMonth>=%s AND yearMonth<=%s)",
        vendorId, reportName, start.toString(), end.toString());
    client.getAbs(okapiUrl + reportsPath)
        .putHeader(XOkapiHeaders.TOKEN, token.getToken())
        .putHeader(XOkapiHeaders.TENANT, token.getTenantId())
        .putHeader(HttpHeaders.ACCEPT, MediaType.JSON_UTF_8.toString())
        .setQueryParam("query", queryStr)
        .setQueryParam("tiny", "true")
        .send(ar -> {
          if (ar.succeeded()) {
            // TODO: catch decode exception
            if (ar.result().statusCode() == 200) {
              CounterReports result = ar.result().bodyAsJson(CounterReports.class);
              List<YearMonth> availableMonths = new ArrayList<>();
              result.getCounterReports().forEach(r -> {
                // TODO: catch parse exception
                // TODO: check r.getDownloadTime() and value of r.getFailedAttempts()
                if (r.getFailedAttempts() == null) {
                  availableMonths.add(YearMonth.parse(r.getYearMonth()));
                }
              });
              future.complete(availableMonths);
            } else {
              future.fail(String.format(ERR_MSG_STATUS, ar.result().statusCode(),
                  ar.result().statusMessage(), okapiUrl + reportsPath));
            }
          } else {
            future.fail(ar.cause());
          }
        });

    return future;
  }

  public Future<List<FetchItem>> getFetchList(UsageDataProvider provider) {
    final String logprefix = "Tenant: " + token.getTenantId() + ", ";

    // check if harvesting status is 'active'
    if (!provider.getHarvestingStatus().equals(HarvestingStatus.ACTIVE)) {
      LOG.info(logprefix + "skipping " + provider.getLabel() + " as harvesting status is "
          + provider.getHarvestingStatus());
      return Future.failedFuture("Harvesting not active");
    }

    Future<List<FetchItem>> future = Future.future();

    YearMonth startMonth = DateUtil.getStartMonth(provider.getHarvestingStart());
    YearMonth endMonth = DateUtil.getEndMonth(provider.getHarvestingEnd());

    List<FetchItem> fetchList = new ArrayList<>();

    @SuppressWarnings("rawtypes")
    List<Future> futures = new ArrayList<>();
    provider.getRequestedReports().forEach(reportName -> {
      futures.add(
          getValidMonths(provider.getVendorId(), reportName, startMonth, endMonth).map(list -> {
            List<YearMonth> arrayList =
                DateUtil.getYearMonths(provider.getHarvestingStart(), provider.getHarvestingEnd());
            arrayList.removeAll(list);
            arrayList.forEach(li -> fetchList.add(
                new FetchItem(reportName, li.atDay(1).toString(), li.atEndOfMonth().toString())));
            return Future.succeededFuture();
          }));
    });

    CompositeFuture.all(futures).setHandler(ar -> {
      if (ar.succeeded()) {
        future.complete(fetchList);
      } else {
        future.fail(ar.cause());
      }
    });

    return future;
  }

  public void fetchAndPostReports(UsageDataProvider provider) {
    final String logprefix = "Tenant: " + token.getTenantId() + ", ";
    LOG.info(logprefix + "processing provider: " + provider.getLabel());

    getServiceEndpoint(provider).map(sep -> {
      if (sep != null) {
        getFetchList(provider).compose(list -> {
          if (list.isEmpty()) {
            LOG.info("Tenant: " + token.getTenantId() + ", Provider: " + provider.getLabel()
                + ", No reports need to be fetched.");
          }
          list.forEach(li -> {
            sep.fetchSingleReport(li.reportType, li.begin, li.end).setHandler(h -> {
              String reportData;
              if (h.succeeded()) {
                reportData = h.result();
              } else {
                reportData = null;
                LOG.error("Tenant: " + token.getTenantId() + ", Provider: " + provider.getLabel()
                    + ", " + li.toString() + ", " + h.cause().getMessage());
              }

              LocalDate parse = LocalDate.parse(li.begin);
              YearMonth month = YearMonth.of(parse.getYear(), parse.getMonth());
              CounterReport report =
                  createCounterReport(reportData, li.reportType, provider, month);
              postReport(report);
            });
          });
          return Future.succeededFuture();
        }).setHandler(h -> {
          if (h.failed()) {
            LOG.error(h.cause());
          }
        });
        return Future.succeededFuture();
      } else {
        return Future.failedFuture("No ServiceEndpoint");
      }
    }).setHandler(h -> {
      if (h.succeeded() && h.result().failed()) {
        LOG.error("Tenant: " + token.getTenantId() + ", Provider: " + provider.getLabel() + ", "
            + h.result().cause().getMessage());
      } else if (h.failed()) {
        LOG.error("Tenant: " + token.getTenantId() + ", Provider: " + provider.getLabel() + ", "
            + h.cause());
      }
    });
  }

  // TODO: handle failed POST/PUT
  public Future<HttpResponse<Buffer>> postReport(CounterReport report) {
    return getReport(report.getVendorId(), report.getReportName(), report.getYearMonth(), true)
        .compose(existing -> {
          if (existing == null) { // no report found
            // POST the report
            return sendReportRequest(HttpMethod.POST, report);
          } else {
            if (report.getFailedAttempts() != null) {
              // FIXME: check null
              report.setFailedAttempts(existing.getFailedAttempts() + 1);
            }
            report.setId(existing.getId());
            return sendReportRequest(HttpMethod.PUT, report);
          }
        });

  }

  public Future<HttpResponse<Buffer>> sendReportRequest(HttpMethod method, CounterReport report) {
    final String logprefix = "Tenant: " + token.getTenantId() + ", ";
    String urlTmp = okapiUrl + reportsPath;
    if (!method.equals(HttpMethod.POST) && !method.equals(HttpMethod.PUT)) {
      return Future.failedFuture("HttpMethod not supported");
    } else if (method.equals(HttpMethod.PUT)) {
      urlTmp += "/" + report.getId();
    }
    final String url = urlTmp;

    final Future<HttpResponse<Buffer>> future = Future.future();

    LOG.info(logprefix + "posting report with id " + report.getId());

    WebClient client = WebClient.create(vertx);
    client.requestAbs(method, url)
        .putHeader(XOkapiHeaders.TOKEN, token.getToken())
        .putHeader(XOkapiHeaders.TENANT, token.getTenantId())
        .putHeader(HttpHeaders.ACCEPT, MediaType.PLAIN_TEXT_UTF_8.toString())
        .sendJsonObject(JsonObject.mapFrom(report), ar -> {
          if (ar.succeeded()) {
            LOG.info(logprefix + String.format(ERR_MSG_STATUS, ar.result().statusCode(),
                ar.result().statusMessage(), url));
            future.complete(ar.result());
          } else {
            LOG.error(ar.cause());
            future.fail(ar.cause());
          }
        });

    return future;
  }

  /**
   * completes with the found report or null if none is found fails otherwise
   */
  public Future<CounterReport> getReport(String vendorId, String reportName, String month,
      boolean tiny) {
    WebClient client = WebClient.create(vertx);
    Future<CounterReport> future = Future.future();
    String queryStr = String.format("(vendorId=%s AND yearMonth=%s AND reportName=%s)", vendorId,
        month, reportName);
    client.getAbs(okapiUrl + reportsPath)
        .putHeader(XOkapiHeaders.TOKEN, token.getToken())
        .putHeader(XOkapiHeaders.TENANT, token.getTenantId())
        .putHeader(HttpHeaders.ACCEPT, MediaType.JSON_UTF_8.toString())
        .setQueryParam("query", queryStr)
        .setQueryParam("tiny", String.valueOf(tiny))
        .send(handler -> {
          if (handler.succeeded()) {
            if (handler.result().statusCode() == 200) {
              CounterReports collection = handler.result().bodyAsJson(CounterReports.class);
              if (collection.getCounterReports().size() == 1) {
                future.complete(collection.getCounterReports().get(0));
              } else {
                future.complete(null);
              }
            } else {
              future.complete(null);
            }
          } else {
            future.fail(handler.cause());
          }
        });
    return future;
  }

  // TODO: undeploy Verticle when processing is done
  public void run() {
    getActiveProviders().map(providers -> {
      providers.getUsageDataProviders().forEach(p -> this.fetchAndPostReports(p));
      return Future.succeededFuture();
    }).setHandler(h -> {
      if (h.failed()) {
        LOG.error(h.cause().getMessage());
        LOG.error("Verticle has failed, id: " + this.deploymentID());
        vertx.undeploy(this.deploymentID());
      }
    });
  }

  private Future<Object> handleErrorFuture(String logPrefix) {
    return Future.future().setHandler(ar -> LOG.error(logPrefix + ar.cause().getMessage()));
  }

  public WorkerVerticle(Token token) {
    this.token = token;
  }

  @Override
  public void stop() throws Exception {
    super.stop();
    LOG.info("Tenant: " + token.getTenantId() + ", undeployed WorkerVerticle");
  }

  @Override
  public void start() throws Exception {
    super.start();

    okapiUrl = config().getString("okapiUrl");
    reportsPath = config().getString("reportsPath");
    providerPath = config().getString("providerPath");
    aggregatorPath = config().getString("aggregatorPath");

    LOG.info("Tenant: " + token.getTenantId() + ", deployed HarvesterVericle");
    if (!config().getBoolean("testing", false)) {
      run();
    }
  }
}
