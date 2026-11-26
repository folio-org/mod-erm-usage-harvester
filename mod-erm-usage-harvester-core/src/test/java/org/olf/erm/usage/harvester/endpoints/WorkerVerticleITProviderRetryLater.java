package org.olf.erm.usage.harvester.endpoints;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.Report;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.DateUtil;
import org.olf.erm.usage.harvester.endpoints.ErrorHandlingResult.RetryLater;

/**
 * An endpoint whose strategy answers {@link RetryLater}, for testing retry runs. The first {@link
 * #FAILURES} fetches fail, later ones succeed. The strategy asks to retry later until an item's
 * retry count reaches {@link #MAX_RETRIES}, then delegates to the default strategy, which gives up.
 * The fetch number {@link #HANGING_FETCH} takes {@link #HANG_TIME} and then fails.
 */
public class WorkerVerticleITProviderRetryLater implements ServiceEndpointProvider {

  public static final String SERVICE_TYPE = "wvitpretrylater";
  public static final Duration DELAY = Duration.ofSeconds(1);
  public static final Duration HANG_TIME = Duration.ofSeconds(15);
  public static final AtomicInteger FAILURES = new AtomicInteger();
  public static final AtomicInteger MAX_RETRIES = new AtomicInteger();
  public static final AtomicInteger HANGING_FETCH = new AtomicInteger();
  public static final AtomicInteger FETCHES = new AtomicInteger();

  /**
   * @param failures the number of fetches that fail before one succeeds
   * @param maxRetries how often an item is retried later before the default strategy takes over
   * @param hangingFetch the number of the fetch that hangs, or 0 for none
   */
  public static void reset(final int failures, final int maxRetries, final int hangingFetch) {
    FAILURES.set(failures);
    MAX_RETRIES.set(maxRetries);
    HANGING_FETCH.set(hangingFetch);
    FETCHES.set(0);
  }

  @Override
  public String getServiceType() {
    return SERVICE_TYPE;
  }

  @Override
  public String getServiceName() {
    return "WorkerVerticleITProviderRetryLater";
  }

  @Override
  public String getServiceDescription() {
    return "Test Provider answering RetryLater";
  }

  @Override
  public ServiceEndpoint create(final Vertx vertx, final UsageDataProvider provider) {
    return new ServiceEndpoint() {
      @Override
      public Future<List<CounterReport>> fetchReport(
          final String report, final String beginDate, final String endDate) {
        final var fetch = FETCHES.incrementAndGet();
        if (fetch == HANGING_FETCH.get()) {
          final var promise = Promise.<List<CounterReport>>promise();
          vertx.setTimer(HANG_TIME.toMillis(), id -> promise.tryFail("hung up"));
          return promise.future();
        }
        if (fetch <= FAILURES.get()) {
          return Future.failedFuture("please try later");
        }
        return Future.succeededFuture(
            DateUtil.getYearMonths(beginDate, endDate).stream()
                .map(YearMonth::toString)
                .map(
                    month ->
                        new CounterReport()
                            .withReportName(report)
                            .withReport(new Report().withAdditionalProperty("month", month))
                            .withRelease("4")
                            .withProviderId(provider.getId())
                            .withYearMonth(month))
                .toList());
      }

      @Override
      public ErrorHandlingStrategy getErrorHandlingStrategy(final UsageDataProvider udp) {
        final var defaultStrategy = ErrorHandlingStrategy.create(udp);
        return (fetchItem, retryCount, t) ->
            retryCount < MAX_RETRIES.get()
                ? new RetryLater(List.of(fetchItem), retryCount + 1, DELAY, "test")
                : defaultStrategy.handleFetchError(fetchItem, retryCount, t);
      }

      @Override
      public void close() {
        // nothing to close
      }
    };
  }
}
