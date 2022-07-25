package org.olf.erm.usage.harvester;

import static io.reactivex.schedulers.Schedulers.trampoline;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.olf.erm.usage.harvester.DateUtil.getYearMonthFromString;
import static org.olf.erm.usage.harvester.ExceptionUtil.getMessageOrToString;
import static org.olf.erm.usage.harvester.Messages.createMsgStatus;
import static org.olf.erm.usage.harvester.Messages.createProviderMsg;
import static org.olf.erm.usage.harvester.Messages.createTenantMsg;
import static org.olf.erm.usage.harvester.Messages.createTenantProviderMsg;
import static org.olf.erm.usage.harvester.endpoints.ServiceEndpoint.createCounterReport;

import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.observers.DisposableCompletableObserver;
import io.reactivex.schedulers.Schedulers;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.reactivex.SingleHelper;
import io.vertx.reactivex.core.AbstractVerticle;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.folio.rest.jaxrs.model.Aggregator;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.HarvestingConfig.HarvestVia;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.client.ExtAggregatorSettingsClient;
import org.olf.erm.usage.harvester.client.ExtAggregatorSettingsClientImpl;
import org.olf.erm.usage.harvester.client.ExtConfigurationsClient;
import org.olf.erm.usage.harvester.client.ExtConfigurationsClientImpl;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClient;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClientImpl;
import org.olf.erm.usage.harvester.client.ExtUsageDataProvidersClient;
import org.olf.erm.usage.harvester.client.ExtUsageDataProvidersClientImpl;
import org.olf.erm.usage.harvester.endpoints.InvalidReportException;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.olf.erm.usage.harvester.endpoints.TooManyRequestsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkerVerticle extends AbstractVerticle {

  public static final String MESSAGE_NO_TENANTID = "No token provided";
  public static final String MESSAGE_NO_TOKEN = "No token provided";
  public static final String MESSAGE_NO_PROVIDERID = "No token provided";
  private static final Logger LOG = LoggerFactory.getLogger(WorkerVerticle.class);
  private static final String CONFIG_MODULE = "ERM-USAGE-HARVESTER";
  private static final String CONFIG_NAME = "maxFailedAttempts";

  private final Promise<Void> finished = Promise.promise();
  private final String token;
  private final String tenantId;
  private final String providerId;
  private int maxFailedAttempts = 5;
  private ExtUsageDataProvidersClient udpClient;
  private ExtAggregatorSettingsClient aggregatorSettingsClient;
  private ExtCounterReportsClient counterReportsClient;

  public Future<Void> getFinished() {
    return finished.future();
  }

  private void logInfo(Supplier<String> logMessage) {
    if (LOG.isInfoEnabled()) {
      LOG.info(logMessage.get());
    }
  }

  private void logError(Supplier<String> logMessage, Throwable t) {
    if (LOG.isErrorEnabled()) {
      LOG.error(logMessage.get(), t);
    }
  }

  private void logError(Supplier<String> logMessage) {
    if (LOG.isErrorEnabled()) {
      LOG.error(logMessage.get());
    }
  }

  private CompletableObserver createCompletableObserver() {
    return new DisposableCompletableObserver() {
      @Override
      public void onComplete() {
        finished.complete();
        logInfo(() -> createTenantMsg(tenantId, "Processing completed"));
      }

      @Override
      public void onError(Throwable e) {
        finished.fail(e);
        logError(() -> createTenantMsg(tenantId, "Error during processing, {}", e.getMessage()), e);
      }
    };
  }

  public WorkerVerticle(String tenantId, String token, String providerId) {
    this.tenantId = requireNonNull(tenantId, MESSAGE_NO_TENANTID);
    this.token = requireNonNull(token, MESSAGE_NO_TOKEN);
    this.providerId = requireNonNull(providerId, MESSAGE_NO_PROVIDERID);
  }

  public Future<ServiceEndpoint> getServiceEndpoint(UsageDataProvider provider) {
    Promise<AggregatorSetting> aggrPromise = Promise.promise();
    Promise<ServiceEndpoint> sepPromise = Promise.promise();

    boolean useAggregator =
        provider.getHarvestingConfig().getHarvestVia().equals(HarvestVia.AGGREGATOR);
    Aggregator aggregator = provider.getHarvestingConfig().getAggregator();
    // Complete aggrPromise if aggregator is not set.. aka skip it
    if (useAggregator && aggregator != null && aggregator.getId() != null) {
      aggregatorSettingsClient
          .getAggregatorSetting(provider)
          .onSuccess(
              result ->
                  LOG.info(
                      createTenantMsg(
                          tenantId, "got AggregatorSetting for id: {}", result.getId())))
          .onComplete(aggrPromise);
    } else {
      aggrPromise.complete(null);
    }

    return aggrPromise
        .future()
        .compose(
            as -> {
              ServiceEndpoint sep = ServiceEndpoint.create(provider, as);
              if (sep != null) {
                sepPromise.complete(sep);
              } else {
                sepPromise.fail(
                    createTenantMsg(
                        tenantId,
                        createProviderMsg(
                            provider.getLabel(), "No service implementation available")));
              }
              return sepPromise.future();
            });
  }

  private <T> Single<T> toSingle(Future<T> future) {
    return SingleHelper.toSingle(future::onComplete);
  }

  private Observable<List<CounterReport>> processItems(
      UsageDataProvider provider,
      ServiceEndpoint sep,
      List<FetchItem> items,
      Scheduler scheduler,
      ThreadPoolExecutor executor) {

    return Observable.fromIterable(items)
        .flatMap(
            fetchItem ->
                Observable.zip(
                        Observable.timer(1, SECONDS, trampoline())
                            .doOnNext(
                                l ->
                                    logInfo(
                                        createTenantProviderMsg(
                                            tenantId,
                                            provider.getLabel(),
                                            "processing {}",
                                            fetchItem))),
                        Single.defer(
                                () ->
                                    toSingle(
                                        sep.fetchReport(
                                            fetchItem.getReportType(),
                                            fetchItem.getBegin(),
                                            fetchItem.getEnd())))
                            .toObservable(),
                        (f, s) -> s)
                    .subscribeOn(scheduler)
                    .retry(
                        3,
                        t -> {
                          if (t instanceof TooManyRequestsException) {
                            logInfo(
                                createTenantProviderMsg(
                                    tenantId,
                                    provider.getLabel(),
                                    "Too many requests.. retrying {}",
                                    fetchItem));
                            executor.setCorePoolSize(1);
                            executor.setMaximumPoolSize(1);
                            return true;
                          } else {
                            return false;
                          }
                        })
                    .onErrorResumeNext(
                        t -> {
                          logInfo(
                              createTenantProviderMsg(
                                  tenantId,
                                  provider.getLabel(),
                                  "received {}",
                                  getMessageOrToString(t)));
                          if (!(t instanceof InvalidReportException)) {
                            // handle generic failures
                            List<CounterReport> counterReportList =
                                DateUtil.getYearMonths(fetchItem.getBegin(), fetchItem.getEnd())
                                    .stream()
                                    .map(
                                        ym ->
                                            createCounterReport(
                                                    null, fetchItem.getReportType(), provider, ym)
                                                .withFailedReason(getMessageOrToString(t)))
                                    .collect(Collectors.toList());
                            return Observable.just(counterReportList);
                          }
                          List<FetchItem> expand = FetchListUtil.expand(fetchItem);
                          // handle failed single month
                          if (expand.size() <= 1) {
                            logInfo(
                                createTenantProviderMsg(
                                    tenantId,
                                    provider.getLabel(),
                                    "Returning null for {}",
                                    fetchItem));
                            return Observable.just(
                                List.of(
                                    createCounterReport(
                                            null,
                                            fetchItem.getReportType(),
                                            provider,
                                            getYearMonthFromString(fetchItem.getBegin()))
                                        .withFailedReason(getMessageOrToString(t))));
                          } else {
                            // handle failed multiple months
                            logInfo(
                                createTenantProviderMsg(
                                    tenantId,
                                    provider.getLabel(),
                                    "Expanded {} into {} FetchItems",
                                    fetchItem,
                                    expand.size()));
                            return processItems(provider, sep, expand, scheduler, executor);
                          }
                        }));
  }

  private String counterReportToString(CounterReport cr) {
    return cr.getReportName() + " " + cr.getYearMonth();
  }

  private List<CounterReport> createFailedCounterReports(
      UsageDataProvider provider, String failedReason, List<FetchItem> list) {
    List<FetchItem> expandedList =
        list.stream()
            .map(FetchListUtil::expand)
            .flatMap(Collection::stream)
            .distinct()
            .collect(Collectors.toList());
    return expandedList.stream()
        .map(
            fi ->
                createCounterReport(
                        null, fi.getReportType(), provider, getYearMonthFromString(fi.getBegin()))
                    .withFailedReason(failedReason))
        .collect(Collectors.toList());
  }

  public Completable fetchAndPostReportsRx(UsageDataProvider provider) {
    logInfo(() -> createTenantMsg(tenantId, "processing provider: {}", provider.getLabel()));

    toSingle(udpClient.updateUDPLastHarvestingDate(provider, Date.from(Instant.now())))
        .doOnSuccess(
            v ->
                logInfo(
                    createTenantProviderMsg(
                        tenantId, provider.getLabel(), "Updated harvestingDate")))
        .doOnError(
            t ->
                logError(
                    createTenantProviderMsg(tenantId, provider.getLabel(), "{}", t.getMessage())))
        .ignoreElement()
        .onErrorComplete()
        .subscribe();

    Single<ServiceEndpoint> sepSingle = toSingle(getServiceEndpoint(provider));
    Single<List<FetchItem>> fetchListSingle =
        toSingle(counterReportsClient.getFetchList(provider, maxFailedAttempts))
            .map(
                list -> {
                  if (list.isEmpty()) {
                    logInfo(
                        () ->
                            createTenantMsg(
                                tenantId,
                                createProviderMsg(
                                    provider.getLabel(), "No reports need to be fetched.")));
                  }
                  return list;
                })
            .map(FetchListUtil::collapse)
            .doOnError(
                t ->
                    logInfo(
                        createTenantProviderMsg(tenantId, provider.getLabel(), t.getMessage())));

    return sepSingle
        .flatMap(
            sep -> {
              ThreadPoolExecutor threadPoolExecutor =
                  new ThreadPoolExecutor(4, 4, 1, TimeUnit.MINUTES, new LinkedBlockingQueue<>());
              return fetchListSingle.map(
                  list ->
                      processItems(
                          provider,
                          sep,
                          list,
                          Schedulers.from(threadPoolExecutor),
                          threadPoolExecutor));
            })
        .onErrorReturn(
            t ->
                fetchListSingle
                    .map(
                        list ->
                            createFailedCounterReports(
                                provider, "Failed getting ServiceEndpoint: " + t.toString(), list))
                    .toObservable())
        .flatMapObservable(listObservable -> listObservable)
        .flatMap(Observable::fromIterable)
        .doOnNext(
            cr ->
                logInfo(
                    createTenantProviderMsg(
                        tenantId, provider.getLabel(), "Received: {}", counterReportToString(cr))))
        .flatMapCompletable(
            cr ->
                toSingle(counterReportsClient.upsertReport(cr))
                    .doOnSuccess(
                        resp ->
                            logInfo(
                                createTenantProviderMsg(
                                    tenantId,
                                    provider.getLabel(),
                                    "{} {}",
                                    counterReportToString(cr),
                                    createMsgStatus(resp.statusCode(), resp.statusMessage()))))
                    .doOnError(
                        t ->
                            logError(
                                createTenantProviderMsg(
                                    tenantId,
                                    provider.getLabel(),
                                    "{} {}",
                                    counterReportToString(cr),
                                    t.getMessage())))
                    .ignoreElement()
                    .onErrorComplete());
  }

  public void runSingleProviderRx() {
    toSingle(udpClient.getActiveProviderById(providerId))
        .flatMapCompletable(this::fetchAndPostReportsRx)
        .doFinally(() -> vertx.undeploy(this.deploymentID()))
        .subscribe(createCompletableObserver());
  }

  @Override
  public void stop() throws Exception {
    super.stop();
    logInfo(() -> createTenantMsg(tenantId, "undeployed WorkerVerticle"));
  }

  @Override
  public void start() throws Exception {
    super.start();

    String okapiUrl = requireNonNull(config().getString("okapiUrl"), "No okapiUrl configured");
    udpClient = new ExtUsageDataProvidersClientImpl(okapiUrl, tenantId, token);
    ExtConfigurationsClient configurationsClient =
        new ExtConfigurationsClientImpl(okapiUrl, tenantId, token);
    aggregatorSettingsClient = new ExtAggregatorSettingsClientImpl(okapiUrl, tenantId, token);
    counterReportsClient = new ExtCounterReportsClientImpl(okapiUrl, tenantId, token);

    configurationsClient
        .getModConfigurationValue(CONFIG_MODULE, CONFIG_NAME)
        .onSuccess(
            s -> LOG.info(createTenantMsg(tenantId, "Got config value {}={}", CONFIG_NAME, s)))
        .onFailure(
            t ->
                LOG.info(
                    createTenantMsg(
                        tenantId,
                        "Failed getting config value {}: {}",
                        CONFIG_NAME,
                        t.getMessage())))
        .onComplete(
            ar -> {
              if (ar.succeeded()) {
                maxFailedAttempts = Integer.parseInt(ar.result());
              }
              logInfo(
                  () -> createTenantMsg(tenantId, "using maxFailedAttempts={}", maxFailedAttempts));

              boolean isTesting = config().getBoolean("testing", false);
              if (!isTesting) {
                runSingleProviderRx();
              } else {
                LOG.info("Skipping harvesting (testing==true)");
              }
            });

    logInfo(() -> createTenantMsg(tenantId, "deployed WorkerVericle"));
  }
}
