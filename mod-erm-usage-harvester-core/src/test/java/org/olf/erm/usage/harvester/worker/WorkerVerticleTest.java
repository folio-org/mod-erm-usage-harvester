package org.olf.erm.usage.harvester.worker;

import static io.vertx.core.ThreadingModel.WORKER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.olf.erm.usage.harvester.worker.WorkerVerticle.ErrorHandlingLoader;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import java.time.YearMonth;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.ErrorHandling;
import org.folio.rest.jaxrs.model.HarvestingConfig;
import org.folio.rest.jaxrs.model.SushiConfig;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.folio.rest.jaxrs.model.UsageDataProviders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClient;
import org.olf.erm.usage.harvester.client.ExtUsageDataProvidersClient;
import org.olf.erm.usage.harvester.endpoints.WorkerVerticleITProviderAbort;
import org.olf.erm.usage.harvester.endpoints.WorkerVerticleITProviderLifecycle;
import org.olf.erm.usage.harvester.errorhandling.ErrorHandlingDefaults;

/** Test class for {@link WorkerVerticle} */
class WorkerVerticleTest {

  private static final String START_FAILURE = "start failed after endpoint creation";
  private static final String FETCH_LIST_FAILURE = "fetch list failed";
  private static final String ERROR_HANDLING_FAILURE = "error handling failed";
  private static final int MAX_FAILED_ATTEMPTS = 7;

  private Vertx vertx;
  private UsageDataProvider usageDataProvider;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    WorkerVerticleITProviderLifecycle.reset();
    WorkerVerticleITProviderAbort.reset();
    usageDataProvider =
        new UsageDataProvider()
            .withId("a1b2c3d4-0000-4000-8000-000000000001")
            .withLabel("Provider")
            .withHarvestingConfig(
                new HarvestingConfig()
                    .withSushiConfig(
                        new SushiConfig()
                            .withServiceType(WorkerVerticleITProviderLifecycle.SERVICE_TYPE)));
  }

  @AfterEach
  void tearDown() {
    vertx.close().await();
  }

  @SuppressWarnings("squid:S3415") // Order of 'assertThat(...)' arguments
  @Test
  void testSuccessfulStartClosesEndpointWhenRunIsOver() {
    final var usageDataProvidersClient = new UsageDataProvidersClientStub(false);
    final var counterReportsClient =
        new CounterReportsClientStub(Future.succeededFuture(List.of()));
    final var workerVerticle =
        new WorkerVerticle(
            counterReportsClient,
            usageDataProvidersClient,
            "tenant",
            usageDataProvider,
            1,
            loadErrorHandlingAsync(
                Future.succeededFuture(
                    new ErrorHandling().withMaxFailedAttempts(MAX_FAILED_ATTEMPTS))));

    final String deploymentId = deploy(workerVerticle).await();
    workerVerticle.getFinished().await();
    assertThat(vertx.deploymentIDs())
        .as("... the run only finishes once the verticle is undeployed")
        .doesNotContain(deploymentId);

    assertThat(usageDataProvidersClient.harvestingDateUpdates.get()).isEqualTo(1);
    assertThat(counterReportsClient.requestedMaxFailedAttempts.get())
        .isEqualTo(MAX_FAILED_ATTEMPTS);
    assertThat(WorkerVerticleITProviderLifecycle.CREATED.get()).isEqualTo(1);
    assertThat(WorkerVerticleITProviderLifecycle.CREATED_IN_DEPLOYMENTS)
        .containsExactly(deploymentId);
    assertThat(WorkerVerticleITProviderLifecycle.CREATED_WITH_VERTX).containsExactly(vertx);
    assertThat(WorkerVerticleITProviderLifecycle.CLOSED.get()).isEqualTo(1);
  }

  @Test
  void testEndpointClosedWhenFetchListFails() {
    final var workerVerticle =
        new WorkerVerticle(
            new CounterReportsClientStub(
                Future.failedFuture(new IllegalStateException(FETCH_LIST_FAILURE))),
            new UsageDataProvidersClientStub(false),
            "tenant",
            usageDataProvider,
            1,
            loadErrorHandlingAsync(Future.succeededFuture(new ErrorHandling())));

    deploy(workerVerticle).await();
    assertThatThrownBy(workerVerticle.getFinished()::await).hasMessage(FETCH_LIST_FAILURE);

    assertThat(WorkerVerticleITProviderLifecycle.CREATED.get()).isEqualTo(1);
    assertThat(WorkerVerticleITProviderLifecycle.CLOSED.get()).isEqualTo(1);
  }

  @Test
  void testRunAbortedIfErrorHandlingCannotBeLoaded() {
    final var counterReportsClient =
        new CounterReportsClientStub(Future.succeededFuture(List.of()));
    final var workerVerticle =
        new WorkerVerticle(
            counterReportsClient,
            new UsageDataProvidersClientStub(false),
            "tenant",
            usageDataProvider,
            1,
            loadErrorHandlingAsync(
                Future.failedFuture(new IllegalStateException(ERROR_HANDLING_FAILURE))));

    deploy(workerVerticle).await();

    assertThatThrownBy(workerVerticle.getFinished()::await).hasMessage(ERROR_HANDLING_FAILURE);

    assertThat(counterReportsClient.fetchListRequests.get()).isZero();
    assertThat(WorkerVerticleITProviderLifecycle.CLOSED.get()).isEqualTo(1);
  }

  @Test
  void testMaxFailedAttemptsDefaultIfOptionIsNotSet() {
    final var counterReportsClient =
        new CounterReportsClientStub(Future.succeededFuture(List.of()));
    final var workerVerticle =
        new WorkerVerticle(
            counterReportsClient,
            new UsageDataProvidersClientStub(false),
            "tenant",
            usageDataProvider,
            1,
            loadErrorHandlingAsync(Future.succeededFuture(new ErrorHandling())));

    deploy(workerVerticle).await();
    workerVerticle.getFinished().await();

    assertThat(counterReportsClient.requestedMaxFailedAttempts.get())
        .isEqualTo(ErrorHandlingDefaults.MAX_FAILED_ATTEMPTS);
  }

  @Test
  void testFetchesCutOffByAbortAreNotUploaded() {
    usageDataProvider
        .getHarvestingConfig()
        .getSushiConfig()
        .setServiceType(WorkerVerticleITProviderAbort.SERVICE_TYPE);
    // the first fetch is still running when the second one's 5 failed uploads abort the run
    final var counterReportsClient =
        new CounterReportsClientStub(
            Future.succeededFuture(
                List.of(
                    new FetchItem("TR", WorkerVerticleITProviderAbort.PENDING_BEGIN, "2025-01-31"),
                    new FetchItem("TR", "2025-02-01", "2025-02-28"))));
    final var workerVerticle =
        new WorkerVerticle(
            counterReportsClient,
            new UsageDataProvidersClientStub(false),
            "tenant",
            usageDataProvider,
            2,
            loadErrorHandlingAsync(Future.succeededFuture(new ErrorHandling())));

    final String deploymentId = deploy(workerVerticle).await();
    assertThatThrownBy(workerVerticle.getFinished()::await)
        .hasMessage("Stopping after 5 failed uploads in a row");
    assertThat(vertx.deploymentIDs())
        .as("... an aborted run only finishes once the verticle is undeployed")
        .doesNotContain(deploymentId);

    assertThat(WorkerVerticleITProviderAbort.CLOSED.get()).isEqualTo(1);
    assertThat(counterReportsClient.uploads.get())
        .as(
            "... only the reports that caused the abort were uploaded, nothing of the cut-off"
                + " fetch")
        .isEqualTo(WorkerVerticleITProviderAbort.REPORTS_PER_FETCH);
  }

  @Test
  void testEndpointClosedWhenStartFailsAfterCreation() {
    final var workerVerticle =
        new WorkerVerticle(
            new CounterReportsClientStub(Future.succeededFuture(List.of())),
            new UsageDataProvidersClientStub(true),
            "tenant",
            usageDataProvider,
            1,
            loadErrorHandlingAsync(Future.succeededFuture(new ErrorHandling())));

    assertThatThrownBy(deploy(workerVerticle)::await).hasMessage(START_FAILURE);
    assertThat(WorkerVerticleITProviderLifecycle.CREATED.get()).isEqualTo(1);
    assertThat(WorkerVerticleITProviderLifecycle.CLOSED.get()).isEqualTo(1);
  }

  @Test
  void testDeploymentFailsIfEndpointProviderThrows() {
    // WorkerVerticleITProviderFailsInitialization throws in create()
    assertDeploymentFailsBeforeAnySideEffect("wvitpfailinit", "Initialization error");
  }

  @Test
  void testDeploymentFailsIfNoEndpointImplementationIsAvailable() {
    assertDeploymentFailsBeforeAnySideEffect(
        "unknownServiceType", "No service implementation available");
  }

  /**
   * Endpoint creation is the first step of {@link WorkerVerticle#start()}: if it fails, the
   * deployment fails and neither the harvesting date is updated nor the fetch list requested.
   */
  private void assertDeploymentFailsBeforeAnySideEffect(
      final String serviceType, final String expectedMessage) {
    usageDataProvider.getHarvestingConfig().getSushiConfig().setServiceType(serviceType);
    final var usageDataProvidersClient = new UsageDataProvidersClientStub(false);
    final var counterReportsClient =
        new CounterReportsClientStub(Future.succeededFuture(List.of()));
    final var workerVerticle =
        new WorkerVerticle(
            counterReportsClient,
            usageDataProvidersClient,
            "tenant",
            usageDataProvider,
            1,
            loadErrorHandlingAsync(Future.succeededFuture(new ErrorHandling())));

    assertThatThrownBy(deploy(workerVerticle)::await).hasMessageContaining(expectedMessage);
    assertThat(vertx.deploymentIDs()).isEmpty();
    assertThat(usageDataProvidersClient.harvestingDateUpdates.get()).isZero();
    assertThat(counterReportsClient.fetchListRequests.get()).isZero();
    assertThat(WorkerVerticleITProviderLifecycle.CREATED.get()).isZero();
  }

  /**
   * Returns a loader that completes with {@code result} on a later turn of the context, like the
   * database lookup in production. With an already completed future, the whole run would be over
   * inside {@link WorkerVerticle#start()}, before the deployment is registered, and the verticle
   * could not undeploy itself.
   */
  private static ErrorHandlingLoader loadErrorHandlingAsync(final Future<ErrorHandling> result) {
    return context -> {
      final Promise<ErrorHandling> promise = Promise.promise();
      context.runOnContext(
          v ->
              result.onComplete(
                  ar -> {
                    if (ar.succeeded()) {
                      promise.complete(ar.result());
                    } else {
                      promise.fail(ar.cause());
                    }
                  }));
      return promise.future();
    };
  }

  private Future<String> deploy(final WorkerVerticle workerVerticle) {
    return vertx.deployVerticle(workerVerticle, new DeploymentOptions().setThreadingModel(WORKER));
  }

  /**
   * Records harvesting date updates; if {@code failing}, throws synchronously instead, which is the
   * first call {@link WorkerVerticle#start()} makes after creating the endpoint.
   */
  private static class UsageDataProvidersClientStub implements ExtUsageDataProvidersClient {

    private final boolean failing;
    private final AtomicInteger harvestingDateUpdates = new AtomicInteger();

    UsageDataProvidersClientStub(final boolean failing) {
      this.failing = failing;
    }

    @Override
    public Future<Void> updateUDPLastHarvestingDate(UsageDataProvider udp, Date date) {
      if (failing) {
        throw new IllegalStateException(START_FAILURE);
      }
      harvestingDateUpdates.incrementAndGet();
      return Future.succeededFuture();
    }

    @Override
    public Future<UsageDataProviders> getActiveProviders() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Future<UsageDataProvider> getActiveProviderById(String providerId) {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Returns the given fetch list result; an empty list makes a run end right after it started.
   * Records how often the fetch list was requested and with which {@code maxFailedAttempts}.
   */
  private static class CounterReportsClientStub implements ExtCounterReportsClient {

    private final Future<List<FetchItem>> fetchListResult;
    private final AtomicInteger fetchListRequests = new AtomicInteger();
    private final AtomicInteger uploads = new AtomicInteger();
    private final AtomicInteger requestedMaxFailedAttempts = new AtomicInteger(-1);

    CounterReportsClientStub(final Future<List<FetchItem>> fetchListResult) {
      this.fetchListResult = fetchListResult;
    }

    @Override
    public Future<List<FetchItem>> getFetchList(UsageDataProvider provider, int maxFailedAttempts) {
      fetchListRequests.incrementAndGet();
      requestedMaxFailedAttempts.set(maxFailedAttempts);
      return fetchListResult;
    }

    @Override
    public Future<CounterReport> getReport(CounterReport report, boolean tiny) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Future<HttpResponse<Buffer>> upsertReport(CounterReport report) {
      // every upload fails, so the Uploader aborts the run after 5 of them
      uploads.incrementAndGet();
      return Future.failedFuture(new IllegalStateException("upload failed"));
    }

    @Override
    public Future<List<YearMonth>> getValidMonths(
        String providerId,
        String reportName,
        String reportRelease,
        YearMonth start,
        YearMonth end,
        int maxFailedAttempts) {
      throw new UnsupportedOperationException();
    }
  }
}
