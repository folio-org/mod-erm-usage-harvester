package org.olf.erm.usage.harvester;

import static io.vertx.core.Future.succeededFuture;
import static org.olf.erm.usage.harvester.DateUtil.getYearMonthFromString;
import static org.olf.erm.usage.harvester.ExceptionUtil.getMessageOrToString;
import static org.olf.erm.usage.harvester.Messages.createMsgStatus;
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
  private static final String CONFIG_MODULE = "ERM-USAGE-HARVESTER";
  private static final String CONFIG_NAME = "maxFailedAttempts";
  private final ExtConfigurationsClient configurationsClient;
  private final ExtCounterReportsClient counterReportsClient;
  private final ExtUsageDataProvidersClient usageDataProvidersClient;
  private final UsageDataProvider usageDataProvider;
  private final ServiceEndpoint serviceEndpoint;
  private final Promise<Void> finished = Promise.promise();
  private final AtomicInteger currentTasks = new AtomicInteger(0);
  private final String tenantId;
  private final LinkedBlockingQueue<FetchItem> queue = new LinkedBlockingQueue<>();
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
              queue.addAll(items);
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
        .onFailure(finished::fail);
  }

  @Override
  public void stop() {
    finished.tryComplete();
  }

  private void undeploy() {
    vertx
        .undeploy(context.deploymentID())
        .onSuccess(v -> logInfo("Undeployed WorkerVericle"))
        .onFailure(
            t -> log.error(createMsg("Error undeploying WorkerVerticle: {}", t.getMessage()), t));
  }

  private void startNext() {
    if (currentTasks.get() < maxConcurrency) {
      FetchItem item = queue.poll();
      if (item != null) {
        currentTasks.incrementAndGet();
        fetchReport(item)
            .compose(this::uploadReports)
            .onComplete(
                ar -> {
                  currentTasks.decrementAndGet();
                  startNext();
                });
      }
    }
  }

  private Future<List<CounterReport>> fetchReport(FetchItem item) {
    logInfo("processing {}", item);
    return serviceEndpoint
        .fetchReport(item.getReportType(), item.getBegin(), item.getEnd())
        .otherwise(t -> handleFailedReport(item, t))
        .otherwise(t -> createFailedCounterReportsFromFetchItem(item, t));
  }

  private List<CounterReport> handleFailedReport(FetchItem item, Throwable t) {
    logInfo("{} Received {}", item, getMessageOrToString(t));
    if (t instanceof TooManyRequestsException) {
      logInfo("Too many requests.. retrying {}", item);
      maxConcurrency = 1;
      queue.add(item);
      return Collections.emptyList();
    }
    if (t instanceof InvalidReportException) {
      List<FetchItem> expand = FetchListUtil.expand(item);
      // handle failed single month
      if (expand.size() <= 1) {
        logInfo("Returning null for {}", item);

        return List.of(
            createCounterReport(
                    null,
                    item.getReportType(),
                    usageDataProvider,
                    getYearMonthFromString(item.getBegin()))
                .withFailedReason(getMessageOrToString(t)));
      } else {
        // handle failed multiple months
        logInfo("Expanded {} into {} FetchItems", item, expand.size());
        queue.addAll(expand);
        return Collections.emptyList();
      }
    }
    // handle generic failures
    return createFailedCounterReportsFromFetchItem(item, t);
  }

  private List<CounterReport> createFailedCounterReportsFromFetchItem(FetchItem item, Throwable t) {
    return DateUtil.getYearMonths(item.getBegin(), item.getEnd()).stream()
        .map(
            ym ->
                createCounterReport(null, item.getReportType(), usageDataProvider, ym)
                    .withFailedReason(getMessageOrToString(t)))
        .toList();
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
                    resp ->
                        logInfo(
                            "Upload of {} {}",
                            counterReportToString(cr),
                            createMsgStatus(resp.statusCode(), resp.statusMessage())))
                .onFailure(
                    t -> log.error(createMsg("{} {}", counterReportToString(cr), t.getMessage())))
                .transform(ar -> succeededFuture()));
  }

  private Future<Integer> getMaxFailedAttempts() {
    return configurationsClient
        .getModConfigurationValue(CONFIG_MODULE, CONFIG_NAME)
        .map(Integer::parseInt)
        .onFailure(
            t ->
                logInfo("Failed getting config value {}: {}", CONFIG_NAME, getMessageOrToString(t)))
        .otherwise(5)
        .onSuccess(s -> logInfo("Using config value {}={}", CONFIG_NAME, s));
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
}
