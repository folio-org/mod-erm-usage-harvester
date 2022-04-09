package org.olf.erm.usage.harvester;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.olf.erm.usage.harvester.DateUtil.getYearMonthFromString;
import static org.olf.erm.usage.harvester.HttpResponseUtil.getResponseBodyIfStatus200;
import static org.olf.erm.usage.harvester.Messages.createMsgStatus;

import io.vertx.core.CompositeFuture;
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

public class ExtCounterReportsClient extends CounterReportsClient {

  public static final String PATH = "/counter-reports";
  private final String okapiUrl;

  public ExtCounterReportsClient(String okapiUrl, String tenantId, String token) {
    super(okapiUrl, tenantId, token, WebClient.create(VertxUtils.getVertxFromContextOrNew()));
    this.okapiUrl = okapiUrl;
  }

  /** completes with the found report or null if none is found fails otherwise */
  public Future<CounterReport> getReport(
      String providerId, String reportName, String month, boolean tiny) {
    String queryStr =
        String.format(
            "(providerId=%s AND yearMonth=%s AND reportName==%s)", providerId, month, reportName);
    return super.getCounterReports(tiny, queryStr, null, null, 0, 1, null)
        .transform(ar -> getResponseBodyIfStatus200(ar, CounterReports.class))
        .flatMap(
            collection ->
                (collection.getCounterReports().isEmpty())
                    ? succeededFuture(null)
                    : succeededFuture(collection.getCounterReports().get(0)));
  }

  public Future<HttpResponse<Buffer>> upsertReport(CounterReport report) {
    AtomicReference<String> requestUrl = new AtomicReference<>(okapiUrl + PATH);
    return this.getReport(
            report.getProviderId(), report.getReportName(), report.getYearMonth(), true)
        .flatMap(
            existing -> {
              if (existing == null) { // no report found
                // POST the report
                return this.postCounterReports(null, report);
              } else {
                requestUrl.updateAndGet(s -> s += "/" + existing.getId());
                if (report.getFailedAttempts() != null) {
                  report.setFailedAttempts(existing.getFailedAttempts() + 1);
                }
                report.setId(existing.getId());
                return this.putCounterReportsById(report.getId(), null, report);
              }
            });
  }

  /**
   * Returns a List of FetchItems/Months that need fetching.
   *
   * @param provider UsageDataProvider
   * @param maxFailedAttempts number of max failed attempts
   */
  public Future<List<FetchItem>> getFetchList(UsageDataProvider provider, int maxFailedAttempts) {
    // check if harvesting status is 'active'
    if (!provider.getHarvestingConfig().getHarvestingStatus().equals(HarvestingStatus.ACTIVE)) {
      return Future.failedFuture("HarvestingStatus not active");
    }

    Promise<List<FetchItem>> promise = Promise.promise();

    // TODO: check for date Strings to not be empty
    // TODO: check for nulls
    YearMonth startMonth =
        getYearMonthFromString(provider.getHarvestingConfig().getHarvestingStart());
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
                    this.getValidMonths(
                            provider.getId(), reportName, startMonth, endMonth, maxFailedAttempts)
                        .map(
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

  /**
   * Returns List of months that that dont need fetching.
   *
   * @param providerId providerId
   * @param reportName reportType
   * @param start start month
   * @param end end month
   * @param maxFailedAttempts number of max failed attempts
   */
  public Future<List<YearMonth>> getValidMonths(
      String providerId, String reportName, YearMonth start, YearMonth end, int maxFailedAttempts) {
    String queryStr =
        String.format(
            "(providerId=%s AND "
                + "((cql.allRecords=1 NOT failedAttempts=\"\") OR (failedAttempts>=%s)) AND "
                + "reportName==%s AND yearMonth>=%s AND yearMonth<=%s)",
            providerId, maxFailedAttempts, reportName, start.toString(), end.toString());

    return super.getCounterReports(true, queryStr, null, null, 0, Integer.MAX_VALUE, null)
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
