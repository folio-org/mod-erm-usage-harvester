package org.olf.erm.usage.harvester.worker;

import static org.olf.erm.usage.harvester.ExceptionUtil.getMessageOrToString;
import static org.olf.erm.usage.harvester.worker.WorkerController.DefaultWorkerController;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.folio.rest.jaxrs.model.ErrorHandling;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClient;
import org.olf.erm.usage.harvester.client.ExtUsageDataProvidersClient;
import org.olf.erm.usage.harvester.client.ServiceEndpointFactory;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.olf.erm.usage.harvester.errorhandling.ErrorHandlingDefaults;
import org.olf.erm.usage.harvester.retry.ParkedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WorkerVerticle is a Vert.x Verticle responsible for fetching and uploading counter reports from a
 * specified usage data provider. It manages the lifecycle of the fetching and uploading process,
 * including handling failures, managing concurrency, and updating the usage data provider's state.
 */
public class WorkerVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LoggerFactory.getLogger(WorkerVerticle.class);

  private final ExtCounterReportsClient counterReportsClient;

  private final ExtUsageDataProvidersClient usageDataProvidersClient;

  private final UsageDataProvider usageDataProvider;

  private final int initialConcurrency;

  private final ErrorHandlingLoader errorHandlingLoader;

  private final LoggerContext logCtx;

  // Written in start() on the Vert.x context, read by getParked() and cancel() on the Quartz
  // thread.
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
   * @param errorHandlingLoader loads the tenant's error handling settings
   */
  public WorkerVerticle(
      final ExtCounterReportsClient counterReportsClient,
      final ExtUsageDataProvidersClient usageDataProvidersClient,
      final String tenantId,
      final UsageDataProvider usageDataProvider,
      final int initialConcurrency,
      final ErrorHandlingLoader errorHandlingLoader) {
    this.counterReportsClient = counterReportsClient;
    this.usageDataProvidersClient = usageDataProvidersClient;
    this.usageDataProvider = usageDataProvider;
    this.initialConcurrency = initialConcurrency;
    this.errorHandlingLoader = errorHandlingLoader;

    this.logCtx = new LoggerContext(tenantId, usageDataProvider.getLabel());
  }

  @Override
  public void start() {
    LOGGER.info(logCtx.createMsg("Deploying WorkerVerticle"));
    serviceEndpoint = ServiceEndpointFactory.createServiceEndpoint(vertx, usageDataProvider);

    try {
      updateUDPLastHarvestingDate();

      controller = new DefaultWorkerController(vertx, context, initialConcurrency, logCtx);

      final var fetcher = configureFetcher(controller);
      final var uploader = new Uploader(counterReportsClient, controller, logCtx);
      final var orchestrator = new Orchestrator(fetcher, uploader, controller, logCtx);

      final var initialItems = orchestrator.loadFetchList(getMaxFailedAttempts());
      orchestrator.startWith(initialItems);
    } catch (RuntimeException e) {
      // Vert.x does not call stop() when start() fails
      closeServiceEndpoint();
      throw e;
    }
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

  private Fetcher configureFetcher(final WorkerController controller) {
    final var strategy = serviceEndpoint.getErrorHandlingStrategy(usageDataProvider);
    final var errorHandler = FetcherErrorHandler.create(logCtx, controller, strategy);
    return new Fetcher(
        counterReportsClient, usageDataProvider, serviceEndpoint, logCtx, errorHandler);
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
   * Hands over the items parked during the run, to be retried in a separate retry run. This seals
   * the parked items: items parked later are dropped (and logged). Call it once, after {@link
   * #getFinished()} has completed or failed.
   *
   * @return an immutable snapshot of the parked items, or an empty list if the verticle was never
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

  private Future<Integer> getMaxFailedAttempts() {
    return errorHandlingLoader
        .load(context)
        .map(
            errorHandling ->
                Objects.requireNonNullElse(
                    errorHandling.getMaxFailedAttempts(),
                    ErrorHandlingDefaults.MAX_FAILED_ATTEMPTS))
        .onFailure(
            t ->
                LOGGER.error(
                    logCtx.createMsg(
                        "Failed getting maxFailedAttempts: {}", getMessageOrToString(t))))
        .onSuccess(s -> LOGGER.info(logCtx.createMsg("Using maxFailedAttempts={}", s)));
  }

  private void updateUDPLastHarvestingDate() {
    usageDataProvidersClient
        .updateUDPLastHarvestingDate(usageDataProvider, Date.from(Instant.now()))
        .onSuccess(v -> LOGGER.info(logCtx.createMsg("Updated harvestingDate")))
        .onFailure(t -> LOGGER.error(logCtx.createMsg(t.getMessage()), t));
  }

  /** Loads the tenant's error handling settings. */
  @FunctionalInterface
  public interface ErrorHandlingLoader {
    Future<ErrorHandling> load(Context context);
  }
}
