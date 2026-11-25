package org.olf.erm.usage.harvester.worker;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClient;

/**
 * An {@link ExtCounterReportsClient} for tests. It only serves the fetch list and counts how often
 * it was requested.
 *
 * @param fetchItems the fetch list to return, or {@code null} to make loading the fetch list fail
 * @param fetchListCalls how often the fetch list was requested
 */
record TestCounterReportsClient(List<FetchItem> fetchItems, AtomicInteger fetchListCalls)
    implements ExtCounterReportsClient {

  TestCounterReportsClient(final List<FetchItem> fetchItems) {
    this(fetchItems, new AtomicInteger());
  }

  @Override
  public Future<CounterReport> getReport(CounterReport report, boolean tiny) {
    // No-op, since we don't need it
    return null;
  }

  @Override
  public Future<HttpResponse<Buffer>> upsertReport(CounterReport report) {
    throw new AssertionError("No upload expected");
  }

  @Override
  public Future<List<FetchItem>> getFetchList(UsageDataProvider provider, int maxFailedAttempts) {
    fetchListCalls.incrementAndGet();
    if (fetchItems == null) {
      return Future.failedFuture(new NullPointerException());
    }
    return Future.succeededFuture(fetchItems);
  }

  @Override
  public Future<List<YearMonth>> getValidMonths(
      String providerId,
      String reportName,
      String reportRelease,
      YearMonth start,
      YearMonth end,
      int maxFailedAttempts) {
    // No-op, since we don't need it
    return null;
  }
}
