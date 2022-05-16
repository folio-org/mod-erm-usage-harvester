package org.olf.erm.usage.harvester.client;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import java.time.YearMonth;
import java.util.List;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.FetchItem;

public interface ExtCounterReportsClient {

  /**
   * completes with the found report or null if none is found fails otherwise
   */
  Future<CounterReport> getReport(
      String providerId, String reportName, String month, boolean tiny);

  Future<HttpResponse<Buffer>> upsertReport(CounterReport report);

  /**
   * Returns a List of FetchItems/Months that need fetching.
   *
   * @param provider UsageDataProvider
   * @param maxFailedAttempts number of max failed attempts
   */
  Future<List<FetchItem>> getFetchList(UsageDataProvider provider, int maxFailedAttempts);

  /**
   * Returns List of months that that dont need fetching.
   *
   * @param providerId providerId
   * @param reportName reportType
   * @param start start month
   * @param end end month
   * @param maxFailedAttempts number of max failed attempts
   */
  Future<List<YearMonth>> getValidMonths(
      String providerId, String reportName, YearMonth start, YearMonth end, int maxFailedAttempts);
}
