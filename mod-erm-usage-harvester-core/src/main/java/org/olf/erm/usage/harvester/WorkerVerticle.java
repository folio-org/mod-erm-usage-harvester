package org.olf.erm.usage.harvester;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.olf.erm.usage.harvester.Constants.SETTINGS_KEY_MAX_FAILED_ATTEMPTS;
import static org.olf.erm.usage.harvester.Constants.SETTINGS_SCOPE_HARVESTER;
import static org.olf.erm.usage.harvester.DateUtil.getYearMonthFromString;
import static org.olf.erm.usage.harvester.ExceptionUtil.getMessageOrToString;
import static org.olf.erm.usage.harvester.FetchListUtil.expand;
import static org.olf.erm.usage.harvester.Messages.createMsgStatus;
import static org.olf.erm.usage.harvester.WorkerVerticle.QueueItem.createQueueItemList;
import static org.olf.erm.usage.harvester.endpoints.ServiceEndpoint.createCounterReport;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.client.ExtConfigurationsClient;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClient;
import org.olf.erm.usage.harvester.client.ExtUsageDataProvidersClient;
import org.olf.erm.usage.harvester.endpoints.InvalidReportException;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.olf.erm.usage.harvester.endpoints.TooManyRequestsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkerVerticle extends AbstractVerticle {

  private static final Logger log = LoggerFactory.getLogger(WorkerVerticle.class);
  private static final int RETRY_COUNT_TOO_MANY_REQUESTS = 2;
  private static final int MAX_FAILED_UPLOAD_COUNT = 5;
  private final ExtConfigurationsClient configurationsClient;
  private final ExtCounterReportsClient counterReportsClient;
  private final ExtUsageDataProvidersClient usageDataProvidersClient;
  private final UsageDataProvider usageDataProvider;
  private final ServiceEndpoint serviceEndpoint;
  private final Promise<Void> finished = Promise.promise();
  private final AtomicInteger currentTasks = new AtomicInteger(0);
  private final AtomicInteger failedUploadCount = new AtomicInteger(0);
  private final String tenantId;
  private final LinkedBlockingQueue<QueueItem> queue = new LinkedBlockingQueue<>();
  private int maxConcurrency;

  public WorkerVerticle(
      ExtConfigurationsClient configurationsClient,
      ExtCounterReportsClient counterReportsClient,
      ExtUsageDataProvidersClient usageDataProvidersClient,
      String tenantId,
      UsageDataProvider usageDataProvider,
      ServiceEndpoint serviceEndpoint,
      int initialConcurrency) {
    this.configurationsClient = configurationsClient;
    this.counterReportsClient = counterReportsClient;
    this.usageDataProvidersClient = usageDataProvidersClient;
    this.tenantId = tenantId;
    this.usageDataProvider = usageDataProvider;
    this.serviceEndpoint = serviceEndpoint;
    this.maxConcurrency = initialConcurrency;
  }

  public Future<Void> getFinished() {
    return finished
        .future()
        .onSuccess(v -> logInfo("Processing completed"))
        .onFailure(t -> log.error(createMsg("Error during processing, {}", t.getMessage()), t));
  }

  @Override
  public void start() {
    logInfo("Deploying WorkerVerticle");
    updateUDPLastHarvestingDate();

    getMaxFailedAttempts()
        .compose(this::getFetchList)
        .onSuccess(
            items -> {
              if (items.isEmpty()) {
                undeploy();
                return;
              }
              queue.addAll(createQueueItemList(items, 0));
              for (int i = 1; i <= maxConcurrency; i++) {
                startNext();
              }
              vertx.setPeriodic(
                  5000,
                  id -> {
                    if (queue.isEmpty() && currentTasks.get() == 0) {
                      vertx.cancelTimer(id);
                      undeploy();
                    }
                  });
            })
        .onFailure(
            t -> {
              finished.fail(t);
              undeploy();
            });
  }

  private void undeploy() {
    finished.tryComplete();
    queue.clear();
    if (vertx.deploymentIDs().contains(context.deploymentID())) {
      vertx
          .undeploy(context.deploymentID())
          .onSuccess(v -> logInfo("Undeployed WorkerVericle"))
          .onFailure(
              t -> log.error(createMsg("Error undeploying WorkerVerticle: {}", t.getMessage()), t));
    }
  }

  private void startNext() {
    if (currentTasks.get() < maxConcurrency) {
      QueueItem queueItem = queue.poll();
      if (queueItem != null) {
        currentTasks.incrementAndGet();
        fetchReport(queueItem)
            .compose(this::uploadReports)
            .onComplete(
                ar -> {
                  currentTasks.decrementAndGet();
                  startNext();
                });
      }
    }
  }

  private Future<List<CounterReport>> fetchReport(QueueItem queueItem) {
    FetchItem item = queueItem.item;
    logInfo("processing {}", item);
    return serviceEndpoint
        .fetchReport(item.getReportType(), item.getBegin(), item.getEnd())
        .otherwise(t -> handleFailedReport(queueItem, t));
  }

  private List<CounterReport> handleFailedReport(QueueItem queueItem, Throwable t) {
    FetchItem item = queueItem.item;
    logInfo("{} Received {}", item, getMessageOrToString(t));
    if (t instanceof TooManyRequestsException) {
      maxConcurrency = 1;
      if (queueItem.retryCount < RETRY_COUNT_TOO_MANY_REQUESTS) {
        logInfo("Too many requests.. adding {} back to queue", item);
        queue.add(new QueueItem(item, queueItem.retryCount + 1));
        return Collections.emptyList();
      } else {
        logInfo(
            "Too many requests.. returning null for {} after {} retries",
            item,
            RETRY_COUNT_TOO_MANY_REQUESTS);
        return createFailedReports(item, t);
      }
    }
    if (t instanceof InvalidReportException) {
      List<FetchItem> expand = expand(item);
      // handle failed single month
      if (expand.size() <= 1) {
        logInfo("Returning null for {}", item);
        return createFailedReports(expand, t);
      } else {
        // handle failed multiple months
        logInfo("Expanded {} into {} FetchItems", item, expand.size());
        queue.addAll(createQueueItemList(expand, 0));
        return Collections.emptyList();
      }
    }
    // handle generic failures
    return createFailedReports(item, t);
  }

  /**
   * Chain and execute asynchronous methods on a list of CounterReport objects sequentially.
   *
   * <p>This method takes a list of CounterReport objects and a method that processes each
   * CounterReport asynchronously. It then sequentially executes the provided method on each item in
   * the list, waiting for the completion of one before starting the next.
   *
   * @param list The list of CounterReport objects to process sequentially.
   * @param method A function that takes a CounterReport object and returns a Future<Void>
   *     representing an asynchronous operation on that object.
   * @return A Future<Void> representing the completion of all asynchronous operations in the list,
   *     executed sequentially.
   */
  private Future<Void> chainCall(
      List<CounterReport> list, Function<CounterReport, Future<Void>> method) {
    return list.stream()
        .reduce(
            Future.succeededFuture(),
            (acc, item) -> acc.compose(v -> method.apply(item)),
            (a, b) -> a);
  }

  private Future<Void> uploadReports(List<CounterReport> crs) {
    return chainCall(
        crs,
        cr ->
            counterReportsClient
                .upsertReport(cr)
                .onSuccess(
                    resp -> {
                      if (resp.statusCode() / 100 != 2) {
                        failedUploadCount.incrementAndGet();
                      } else {
                        failedUploadCount.set(0);
                      }
                      logInfo(
                          "Upload of {} {}",
                          counterReportToString(cr),
                          createMsgStatus(resp.statusCode(), resp.statusMessage()));
                    })
                .onFailure(
                    t -> {
                      failedUploadCount.incrementAndGet();
                      log.error(createMsg("{} {}", counterReportToString(cr), t.getMessage()));
                    })
                .transform(
                    ar -> {
                      if (failedUploadCount.get() >= MAX_FAILED_UPLOAD_COUNT) {
                        String msg =
                            "Stopping after "
                                + MAX_FAILED_UPLOAD_COUNT
                                + " failed uploads in a row";
                        finished.tryFail(msg);
                        undeploy();
                        return failedFuture(msg);
                      } else {
                        return succeededFuture();
                      }
                    }));
  }

  private Future<Integer> getMaxFailedAttempts() {
    return configurationsClient
        .getModConfigurationValue(
          SETTINGS_SCOPE_HARVESTER, SETTINGS_KEY_MAX_FAILED_ATTEMPTS)
        .map(Integer::parseInt)
        .onFailure(
            t ->
                logInfo("Failed getting config value {}: {}", SETTINGS_KEY_MAX_FAILED_ATTEMPTS, getMessageOrToString(t)))
        .otherwise(5)
        .onSuccess(s -> logInfo("Using config value {}={}", SETTINGS_KEY_MAX_FAILED_ATTEMPTS, s));
  }

  private Future<List<FetchItem>> getFetchList(int maxFailedAttempts) {
    return counterReportsClient
        .getFetchList(usageDataProvider, maxFailedAttempts)
        .map(
            list -> {
              if (list.isEmpty()) {
                logInfo("No reports need to be fetched.");
              }
              return list;
            })
        .map(FetchListUtil::collapse)
        .onFailure(t -> logInfo(t.getMessage()));
  }

  private void updateUDPLastHarvestingDate() {
    usageDataProvidersClient
        .updateUDPLastHarvestingDate(usageDataProvider, Date.from(Instant.now()))
        .onSuccess(v -> logInfo("Updated harvestingDate"))
        .onFailure(t -> log.error(createMsg("{}", t.getMessage())));
  }

  private List<CounterReport> createFailedReports(FetchItem item, Throwable t) {
    return createFailedReports(expand(item), t);
  }

  private List<CounterReport> createFailedReports(List<FetchItem> items, Throwable t) {
    return items.stream()
        .map(
            i ->
                createCounterReport(
                        null,
                        i.getReportType(),
                        usageDataProvider,
                        getYearMonthFromString(i.getBegin()))
                    .withFailedReason(getMessageOrToString(t)))
        .toList();
  }

  private String createMsg(String pattern, Object... args) {
    return Messages.createTenantProviderMsg(tenantId, usageDataProvider.getLabel(), pattern, args);
  }

  private String counterReportToString(CounterReport cr) {
    return cr.getReportName() + " " + cr.getYearMonth();
  }

  private void logInfo(String pattern, Object... args) {
    if (log.isInfoEnabled()) {
      log.info(createMsg(pattern, args));
    }
  }

  static class QueueItem {
    private final FetchItem item;
    private final int retryCount;

    public QueueItem(FetchItem item, int retryCount) {
      this.item = item;
      this.retryCount = retryCount;
    }

    public static List<QueueItem> createQueueItemList(List<FetchItem> itemList, int retryCount) {
      return itemList.stream().map(item -> new QueueItem(item, retryCount)).toList();
    }
  }
}
