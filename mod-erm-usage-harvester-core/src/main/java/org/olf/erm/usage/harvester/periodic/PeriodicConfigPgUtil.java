package org.olf.erm.usage.harvester.periodic;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.util.Map;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.PeriodicConfig;
import org.folio.rest.persist.PostgresClient;

public class PeriodicConfigPgUtil {

  private static final String TBL = "periodic";
  private static final String UUID = "8bf5fe33-5ec8-420c-a86d-6320c55ba554";

  public static Future<RowSet<Row>> delete(Context vertxContext, String tenantId) {
    return PostgresClient.getInstance(vertxContext.owner(), tenantId).delete(TBL, UUID);
  }

  public static Future<RowSet<Row>> delete(Context vertxContext, Map<String, String> okapiHeaders) {
    return delete(vertxContext, okapiHeaders.get(XOkapiHeaders.TENANT));
  }

  public static Future<String> upsert(
      Context vertxContext, String tenantId, PeriodicConfig config) {
    return PostgresClient.getInstance(vertxContext.owner(), tenantId)
        .save(TBL, UUID, config, false, true);
  }

  public static Future<String> upsert(
      Context vertxContext, Map<String, String> okapiHeaders, PeriodicConfig config) {
    return upsert(vertxContext, okapiHeaders.get(XOkapiHeaders.TENANT), config);
  }

  public static Future<PeriodicConfig> get(Context vertxContext, String tenantId) {
    return PostgresClient.getInstance(vertxContext.owner(), tenantId)
        .getById(TBL, UUID, PeriodicConfig.class);
  }

  public static Future<PeriodicConfig> get(Context vertxContext, Map<String, String> okapiHeaders) {
    return get(vertxContext, okapiHeaders.get(XOkapiHeaders.TENANT));
  }

  private PeriodicConfigPgUtil() {}
}
