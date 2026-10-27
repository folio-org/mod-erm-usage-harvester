package org.olf.erm.usage.harvester.errorhandling;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.ErrorHandling;
import org.folio.rest.jaxrs.model.ReportQueuedForProcessing;
import org.folio.rest.jaxrs.model.ServiceBusy;
import org.folio.rest.jaxrs.model.ServiceNotAvailable;
import org.folio.rest.jaxrs.model.TooManyRequests;
import org.folio.rest.persist.PostgresClient;

public class ErrorHandlingPgUtil {

  private static final String TBL = "error_handling";
  private static final String UUID = "40dfa132-7ce4-4f34-a3b0-4ac36f28d888";

  public static Future<String> upsert(Context vertxContext, String tenantId, ErrorHandling config) {
    return PostgresClient.getInstance(vertxContext.owner(), tenantId)
        .save(TBL, UUID, config, false, true);
  }

  public static Future<String> upsert(
      Context vertxContext, Map<String, String> okapiHeaders, ErrorHandling config) {
    return upsert(vertxContext, okapiHeaders.get(XOkapiHeaders.TENANT), config);
  }

  public static Future<ErrorHandling> get(Context vertxContext, String tenantId) {
    return PostgresClient.getInstance(vertxContext.owner(), tenantId)
        .getById(TBL, UUID, ErrorHandling.class)
        .map(ErrorHandlingPgUtil::withDefaults);
  }

  public static Future<ErrorHandling> get(Context vertxContext, Map<String, String> okapiHeaders) {
    return get(vertxContext, okapiHeaders.get(XOkapiHeaders.TENANT));
  }

  /** Fills in default values for everything that has not been saved yet. */
  private static ErrorHandling withDefaults(ErrorHandling errorHandling) {
    ErrorHandling eh = errorHandling != null ? errorHandling : new ErrorHandling();
    if (eh.getServiceNotAvailable() == null) eh.setServiceNotAvailable(new ServiceNotAvailable());
    if (eh.getServiceBusy() == null) eh.setServiceBusy(new ServiceBusy());
    if (eh.getReportQueuedForProcessing() == null)
      eh.setReportQueuedForProcessing(new ReportQueuedForProcessing());
    if (eh.getTooManyRequests() == null) eh.setTooManyRequests(new TooManyRequests());
    return eh;
  }

  private ErrorHandlingPgUtil() {}
}
