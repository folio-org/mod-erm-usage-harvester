package org.olf.erm.usage.harvester.errorhandling;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.ErrorHandling;
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
        .getById(TBL, UUID, ErrorHandling.class);
  }

  public static Future<ErrorHandling> get(Context vertxContext, Map<String, String> okapiHeaders) {
    return get(vertxContext, okapiHeaders.get(XOkapiHeaders.TENANT));
  }

  private ErrorHandlingPgUtil() {}
}
