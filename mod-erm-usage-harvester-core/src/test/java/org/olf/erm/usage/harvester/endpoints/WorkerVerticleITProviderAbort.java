package org.olf.erm.usage.harvester.endpoints;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.Report;
import org.folio.rest.jaxrs.model.UsageDataProvider;

/**
 * Simulates a fetch that is still running when the run is aborted: a fetch starting at {@link
 * #PENDING_BEGIN} only completes when the endpoint is closed, and then fails like a fetch whose
 * HTTP client was closed. Any other fetch immediately returns {@link #REPORTS_PER_FETCH} reports.
 */
public class WorkerVerticleITProviderAbort implements ServiceEndpointProvider {

  public static final String SERVICE_TYPE = "wvitpabort";
  public static final String PENDING_BEGIN = "2025-01-01";
  public static final int REPORTS_PER_FETCH = 5;
  public static final AtomicInteger CLOSED = new AtomicInteger();
  private static volatile Promise<Void> pendingFetchStarted = Promise.promise();

  public static void reset() {
    CLOSED.set(0);
    pendingFetchStarted = Promise.promise();
  }

  /**
   * @return a future that completes once a fetch starting at {@link #PENDING_BEGIN} was requested
   */
  public static Future<Void> pendingFetchStarted() {
    return pendingFetchStarted.future();
  }

  @Override
  public String getServiceType() {
    return SERVICE_TYPE;
  }

  @Override
  public String getServiceName() {
    return "WorkerVerticleITProviderAbort";
  }

  @Override
  public ServiceEndpoint create(Vertx vertx, UsageDataProvider provider) {
    final Promise<List<CounterReport>> pendingFetch = Promise.promise();

    return new ServiceEndpoint() {
      @Override
      public Future<List<CounterReport>> fetchReport(
          String report, String beginDate, String endDate) {
        if (PENDING_BEGIN.equals(beginDate)) {
          pendingFetchStarted.tryComplete();
          return pendingFetch.future();
        }
        return Future.succeededFuture(
            IntStream.range(0, REPORTS_PER_FETCH)
                .mapToObj(
                    i ->
                        new CounterReport()
                            .withReportName(report)
                            .withYearMonth(beginDate.substring(0, 7))
                            .withReport(new Report()))
                .toList());
      }

      @Override
      public void close() {
        CLOSED.incrementAndGet();
        pendingFetch.tryFail(new IllegalStateException("Client is closed"));
      }
    };
  }
}
