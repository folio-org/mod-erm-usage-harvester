package org.olf.erm.usage.harvester.client;

import static io.vertx.core.Future.succeededFuture;
import static org.olf.erm.usage.harvester.DateUtil.getYearMonthFromString;
import static org.olf.erm.usage.harvester.HttpResponseUtil.getResponseBodyIfStatus200;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.folio.rest.client.CounterReportsClient;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.CounterReports;
import org.folio.rest.jaxrs.model.HarvestingConfig.HarvestingStatus;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.folio.rest.tools.utils.VertxUtils;
import org.olf.erm.usage.harvester.DateUtil;
import org.olf.erm.usage.harvester.FetchItem;

public class ExtCounterReportsClientImpl extends CounterReportsClient
    implements ExtCounterReportsClient {

  public static final String PATH = "/counter-reports";
  private final String okapiUrl;

  public ExtCounterReportsClientImpl(String okapiUrl, String tenantId, String token) {
    this(okapiUrl, tenantId, token, WebClient.create(VertxUtils.getVertxFromContextOrNew()));
  }

  public ExtCounterReportsClientImpl(
      String okapiUrl, String tenantId, String token, WebClient webClient) {
    super(okapiUrl, tenantId, token, webClient);
    this.okapiUrl = okapiUrl;
  }

  @Override
  public Future<CounterReport> getReport(CounterReport report, boolean tiny) {
    String providerId = report.getProviderId();
    String release = report.getRelease();
    String reportName = report.getReportName();
    String month = report.getYearMonth();
    String queryStr =
        String.format(
            "(providerId=%s AND release=%s AND yearMonth=%s AND reportName==%s)",
            providerId, release, month, reportName);
    return super.getCounterReports(tiny, queryStr, null, null, null, 0, 1)
        .transform(ar -> getResponseBodyIfStatus200(ar, CounterReports.class))
        .flatMap(
            collection ->
                (collection.getCounterReports().isEmpty())
                    ? succeededFuture(null)
                    : succeededFuture(collection.getCounterReports().get(0)));
  }

  @Override
  public Future<HttpResponse<Buffer>> upsertReport(CounterReport report) {
    AtomicReference<String> requestUrl = new AtomicReference<>(okapiUrl + PATH);
    return this.getReport(report, true)
        .flatMap(
            existing -> {
              if (existing == null) { // no report found
                // POST the report
                return this.postCounterReports(report);
              } else {
                requestUrl.updateAndGet(s -> s += "/" + existing.getId());
                if (report.getFailedAttempts() != null) {
                  report.setFailedAttempts(existing.getFailedAttempts() + 1);
                }
                report.setId(existing.getId());
                return this.putCounterReportsById(report.getId(), report);
              }
            });
  }

  @Override
  public Future<List<FetchItem>> getFetchList(UsageDataProvider provider, int maxFailedAttempts) {
    // check if harvesting status is 'active'
    if (!provider.getHarvestingConfig().getHarvestingStatus().equals(HarvestingStatus.ACTIVE)) {
      return Future.failedFuture("HarvestingStatus not active");
    }

    Promise<List<FetchItem>> promise = Promise.promise();

    YearMonth startMonth =
        getYearMonthFromString(provider.getHarvestingConfig().getHarvestingStart());
    YearMonth endMonth =
        DateUtil.getYearMonthFromStringWithLimit(
            provider.getHarvestingConfig().getHarvestingEnd(), YearMonth.now().minusMonths(1));

    List<FetchItem> fetchList = new ArrayList<>();

    List<Future<Void>> futures = new ArrayList<>();
    String reportRelease = provider.getHarvestingConfig().getReportRelease();
    provider
        .getHarvestingConfig()
        .getRequestedReports()
        .forEach(
            reportName ->
                futures.add(
                    getValidMonths(
                            provider.getId(),
                            reportName,
                            reportRelease,
                            startMonth,
                            endMonth,
                            maxFailedAttempts)
                        .compose(
                            list -> {
                              List<YearMonth> arrayList =
                                  DateUtil.getYearMonths(startMonth, endMonth);
                              arrayList.removeAll(list);
                              arrayList.forEach(
                                  li ->
                                      fetchList.add(
                                          new FetchItem(
                                              reportName,
                                              li.atDay(1).toString(),
                                              li.atEndOfMonth().toString())));
                              return Future.succeededFuture();
                            })));

    Future.all(futures)
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

  @Override
  public Future<List<YearMonth>> getValidMonths(
      String providerId,
      String reportName,
      String release,
      YearMonth start,
      YearMonth end,
      int maxFailedAttempts) {
    String queryStr =
        String.format(
            """
            (providerId=%s AND release=%s AND \
            ((cql.allRecords=1 NOT failedAttempts="") OR (failedAttempts>=%s)) AND \
            reportName==%s AND yearMonth>=%s AND yearMonth<=%s)
            """,
            providerId, release, maxFailedAttempts, reportName, start.toString(), end.toString());

    return super.getCounterReports(true, queryStr, null, null, null, 0, Integer.MAX_VALUE)
        .transform(ar -> getResponseBodyIfStatus200(ar, CounterReports.class))
        .flatMap(
            result -> {
              List<YearMonth> availableMonths = new ArrayList<>();
              result
                  .getCounterReports()
                  .forEach(r -> availableMonths.add(YearMonth.parse(r.getYearMonth())));
              return succeededFuture(availableMonths);
            });
  }
}
