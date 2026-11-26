package org.olf.erm.usage.harvester.worker;

import static org.olf.erm.usage.harvester.ExceptionUtil.getMessageOrToString;
import static org.olf.erm.usage.harvester.worker.WorkerController.DefaultWorkerController;
import static org.olf.erm.usage.harvester.worker.WorkerController.QueueItem;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClient;
import org.olf.erm.usage.harvester.client.ExtUsageDataProvidersClient;
import org.olf.erm.usage.harvester.client.ServiceEndpointFactory;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.olf.erm.usage.harvester.errorhandling.ErrorHandlingLoader;
import org.olf.erm.usage.harvester.errorhandling.ErrorHandlingSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WorkerVerticle is a Vert.x Verticle responsible for fetching and uploading counter reports from a
 * specified usage data provider. It manages the lifecycle of the fetching and uploading process,
 * including handling failures, managing concurrency, and updating the usage data provider's state.
 *
 * <p>A <em>regular run</em> starts with the provider's fetch list. A <em>retry run</em> starts with
 * the given retry items instead, i.e. items an earlier run has parked (see {@link #getParked()}).
 */
public class WorkerVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LoggerFactory.getLogger(WorkerVerticle.class);

  private final ExtCounterReportsClient counterReportsClient;

  private final ExtUsageDataProvidersClient usageDataProvidersClient;

  private final UsageDataProvider usageDataProvider;

  private final int initialConcurrency;

  private final ErrorHandlingLoader errorHandlingLoader;

  private final LoggerContext logCtx;

  private final List<ParkedItem> retryItems;

  /**
   * Written in start() on the Vert.x context, read by getParked() and cancel() on the Quartz
   * thread.
   */
  private volatile DefaultWorkerController controller;

  private ServiceEndpoint serviceEndpoint;

  /**
   * Creates a new instance of WorkerVerticle with the specified dependencies.
   *
   * @param counterReportsClient the client to retrieve counter reports
   * @param usageDataProvidersClient the client to update the usage data providers state
   * @param tenantId the tenants ID
   * @param usageDataProvider the usage data provider itself, needed by the counter reports client
   *     to create a fetch list
   * @param initialConcurrency the initial maximum number of tasks that are allowed to run in
   *     parallel
   * @param errorHandlingLoader loads the error handling settings of the run
   */
  public WorkerVerticle(
      final ExtCounterReportsClient counterReportsClient,
      final ExtUsageDataProvidersClient usageDataProvidersClient,
      final String tenantId,
      final UsageDataProvider usageDataProvider,
      final int initialConcurrency,
      final ErrorHandlingLoader errorHandlingLoader) {
    this(
        counterReportsClient,
        usageDataProvidersClient,
        tenantId,
        usageDataProvider,
        initialConcurrency,
        errorHandlingLoader,
        Collections.emptyList());
  }

  /**
   * Creates a new instance of WorkerVerticle for a retry run: it starts with the given retry items,
   * each with its retry count, instead of the provider's fetch list. The items are used as given,
   * the caller has to make sure they are due.
   *
   * @param counterReportsClient the client to retrieve counter reports
   * @param usageDataProvidersClient the client to update the usage data providers state
   * @param tenantId the tenants ID
   * @param usageDataProvider the usage data provider itself
   * @param initialConcurrency the initial maximum number of tasks that are allowed to run in
   *     parallel
   * @param errorHandlingLoader loads the error handling settings of the run
   * @param retryItems the items to retry; an empty list starts a regular run
   */
  public WorkerVerticle(
      final ExtCounterReportsClient counterReportsClient,
      final ExtUsageDataProvidersClient usageDataProvidersClient,
      final String tenantId,
      final UsageDataProvider usageDataProvider,
      final int initialConcurrency,
      final ErrorHandlingLoader errorHandlingLoader,
      final List<ParkedItem> retryItems) {
    this.counterReportsClient = counterReportsClient;
    this.usageDataProvidersClient = usageDataProvidersClient;
    this.usageDataProvider = usageDataProvider;
    this.initialConcurrency = initialConcurrency;
    this.errorHandlingLoader = errorHandlingLoader;
    this.retryItems = List.copyOf(retryItems);

    this.logCtx = new LoggerContext(tenantId, usageDataProvider.getLabel());
  }

  /**
   * Loads the error handling settings first and only then sets up the run, so that all components
   * get resolved settings. If loading them fails, the deployment fails.
   */
  @Override
  public void start(final Promise<Void> startPromise) {
    LOGGER.info(logCtx.createMsg("Deploying WorkerVerticle"));
    serviceEndpoint = ServiceEndpointFactory.createServiceEndpoint(vertx, usageDataProvider);

    try {
      updateUDPLastHarvestingDate();
      errorHandlingLoader
          .load(context)
          .map(this::initializeControllerAndCreateOrchestrator)
          .onSuccess(
              orchestrator -> {
                startPromise.complete();
                context.runOnContext(v -> orchestrator.startHarvesting());
              })
          .onFailure(
              t -> {
                LOGGER.error(
                    logCtx.createMsg("Failed to start the run: {}", getMessageOrToString(t)));
                closeServiceEndpoint();
                startPromise.fail(t);
              });
    } catch (RuntimeException e) {
      closeServiceEndpoint();
      throw e;
    }
  }

  private Orchestrator initializeControllerAndCreateOrchestrator(
      final ErrorHandlingSettings settings) {
    LOGGER.info(logCtx.createMsg("Using {}", settings));
    controller = new DefaultWorkerController(vertx, context, initialConcurrency, logCtx);

    final var strategy = serviceEndpoint.getErrorHandlingStrategy(usageDataProvider);
    final var fetcher =
        new Fetcher(
            counterReportsClient,
            usageDataProvider,
            serviceEndpoint,
            logCtx,
            FetcherErrorHandler.create(logCtx, controller, strategy),
            settings.maxFailedAttempts(),
            QueueItem.fromParked(retryItems));
    final var uploader = new Uploader(counterReportsClient, controller, logCtx);

    return new Orchestrator(fetcher, uploader, controller, logCtx);
  }

  @Override
  public void stop() {
    closeServiceEndpoint();
  }

  private void closeServiceEndpoint() {
    if (serviceEndpoint != null) {
      serviceEndpoint.close();
    }
  }

  /**
   * @return a future that completes when the processing of all queue items is finished
   */
  public Future<Void> getFinished() {
    if (controller == null) {
      return Future.failedFuture("WorkerVerticle never started");
    }

    return controller
        .getFinished()
        .future()
        .onSuccess(v -> LOGGER.info(logCtx.createMsg("Processing completed")))
        .onFailure(
            t -> LOGGER.error(logCtx.createMsg("Error during processing, {}", t.getMessage()), t));
  }

  /**
   * @return an immutable snapshot of the parked items, or an empty list if the Verticle was never
   *     started (e.g. the deployment failed)
   */
  public List<ParkedItem> getParked() {
    return controller == null ? List.of() : controller.sealParked();
  }

  /**
   * Cancels the run: the controller aborts with the given reason, so {@link #getFinished()} fails
   * with it. Can be called from any thread. Cancelling a run that has already finished has no
   * effect.
   *
   * @param reason why the run is canceled
   */
  public void cancel(final String reason) {
    if (controller == null) {
      LOGGER.warn(logCtx.createMsg("Cannot cancel, the WorkerVerticle was never started"));
      return;
    }

    LOGGER.info(logCtx.createMsg("Cancelling: {}", reason));
    context.runOnContext(v -> controller.abort(reason));
  }

  private void updateUDPLastHarvestingDate() {
    usageDataProvidersClient
        .updateUDPLastHarvestingDate(usageDataProvider, Date.from(Instant.now()))
        .onSuccess(v -> LOGGER.info(logCtx.createMsg("Updated harvestingDate")))
        .onFailure(t -> LOGGER.error(logCtx.createMsg(t.getMessage()), t));
  }

  /** An item the strategy asked to retry later, handed over to be retried in a separate run. */
  public record ParkedItem(FetchItem item, int retryCount, Instant dueAt, String reason) {}
}
