package org.olf.erm.usage.harvester.worker;

import static org.olf.erm.usage.harvester.ExceptionUtil.getMessageOrToString;
import static org.olf.erm.usage.harvester.worker.WorkerController.DefaultWorkerController;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import java.time.Instant;
import java.util.Date;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClient;
import org.olf.erm.usage.harvester.client.ExtUsageDataProvidersClient;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.olf.erm.usage.harvester.errorhandling.ErrorHandlingDefaults;
import org.olf.erm.usage.harvester.errorhandling.ErrorHandlingPgUtil;
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

  private final String tenantId;

  private final UsageDataProvider usageDataProvider;

  private final ServiceEndpoint serviceEndpoint;

  private final int initialConcurrency;

  private final LoggerContext logCtx;

  private DefaultWorkerController controller;

  /**
   * Creates a new instance of WorkerVerticle with the specified dependencies.
   *
   * @param counterReportsClient the client to retrieve counter reports
   * @param usageDataProvidersClient the client to update the usage data providers state
   * @param tenantId the tenants ID
   * @param usageDataProvider the usage data provider itself, needed by the counter reports client
   *     to create a fetch list
   * @param serviceEndpoint the service endpoint needed to fetch a reports from
   * @param initialConcurrency the initial maximum number of tasks that are allowed to run in
   *     parallel
   */
  public WorkerVerticle(
      final ExtCounterReportsClient counterReportsClient,
      final ExtUsageDataProvidersClient usageDataProvidersClient,
      final String tenantId,
      final UsageDataProvider usageDataProvider,
      final ServiceEndpoint serviceEndpoint,
      final int initialConcurrency) {
    this.counterReportsClient = counterReportsClient;
    this.usageDataProvidersClient = usageDataProvidersClient;
    this.tenantId = tenantId;
    this.usageDataProvider = usageDataProvider;
    this.serviceEndpoint = serviceEndpoint;
    this.initialConcurrency = initialConcurrency;

    this.logCtx = new LoggerContext(tenantId, usageDataProvider.getLabel());
  }

  @Override
  public void start() {
    LOGGER.info(logCtx.createMsg("Deploying WorkerVerticle"));
    updateUDPLastHarvestingDate();

    controller = new DefaultWorkerController(vertx, context, initialConcurrency, logCtx);

    final var fetcher = configureFetcher(controller);
    final var uploader = new Uploader(counterReportsClient, controller, logCtx);
    final var orchestrator = new Orchestrator(fetcher, uploader, controller, logCtx);

    final var maxFailedAttempts = getMaxFailedAttempts();
    orchestrator.startWith(maxFailedAttempts);
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
    return controller
        .getFinished()
        .future()
        .onSuccess(v -> LOGGER.info(logCtx.createMsg("Processing completed")))
        .onFailure(
            t -> LOGGER.error(logCtx.createMsg("Error during processing, {}", t.getMessage()), t));
  }

  private Future<Integer> getMaxFailedAttempts() {
    return ErrorHandlingPgUtil.get(context, tenantId)
        .map(
            errorHandling ->
                errorHandling.getMaxFailedAttempts() != null
                    ? errorHandling.getMaxFailedAttempts()
                    : ErrorHandlingDefaults.MAX_FAILED_ATTEMPTS)
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
}
