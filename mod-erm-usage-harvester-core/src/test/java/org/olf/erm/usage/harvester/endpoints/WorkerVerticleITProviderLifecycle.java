package org.olf.erm.usage.harvester.endpoints;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.FetchItem;

/**
 * Records the lifecycle of the endpoints it creates: how many were created, in which deployment
 * (the deployment ID of the Vert.x context {@code create()} ran on), with which Vert.x instance,
 * how many were closed, and which items were fetched.
 */
public class WorkerVerticleITProviderLifecycle implements ServiceEndpointProvider {

  public static final String SERVICE_TYPE = "wvitplifecycle";
  public static final AtomicInteger CREATED = new AtomicInteger();
  public static final List<String> CREATED_IN_DEPLOYMENTS = new CopyOnWriteArrayList<>();
  public static final List<Vertx> CREATED_WITH_VERTX = new CopyOnWriteArrayList<>();
  public static final AtomicInteger CLOSED = new AtomicInteger();
  public static final List<FetchItem> FETCHED = new CopyOnWriteArrayList<>();

  public static void reset() {
    CREATED.set(0);
    CREATED_IN_DEPLOYMENTS.clear();
    CREATED_WITH_VERTX.clear();
    CLOSED.set(0);
    FETCHED.clear();
  }

  @Override
  public String getServiceType() {
    return SERVICE_TYPE;
  }

  @Override
  public String getServiceName() {
    return "WorkerVerticleITProviderLifecycle";
  }

  @Override
  public String getServiceDescription() {
    return "Test Provider recording the endpoint lifecycle";
  }

  @Override
  public ServiceEndpoint create(Vertx vertx, UsageDataProvider provider) {
    CREATED.incrementAndGet();
    final var context = Vertx.currentContext();
    if (context != null) {
      CREATED_IN_DEPLOYMENTS.add(context.deploymentID());
    }
    if (vertx != null) {
      CREATED_WITH_VERTX.add(vertx);
    }

    return new ServiceEndpoint() {
      @Override
      public Future<List<CounterReport>> fetchReport(
          String report, String beginDate, String endDate) {
        FETCHED.add(new FetchItem(report, beginDate, endDate));
        return Future.succeededFuture(List.of());
      }

      @Override
      public void close() {
        CLOSED.incrementAndGet();
      }
    };
  }
}
