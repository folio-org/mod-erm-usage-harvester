package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.tools.utils.TenantTool;
import org.olf.erm.usage.harvester.periodic.PeriodicConfigPgUtil;
import org.olf.erm.usage.harvester.periodic.SchedulingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TenantAPIImpl extends TenantAPI {

  private static final Logger log = LoggerFactory.getLogger(TenantAPIImpl.class);

  @Override
  public void postTenant(
      TenantAttributes tenantAttributes,
      Map<String, String> headers,
      Handler<AsyncResult<Response>> handler,
      Context context) {
    String tenantId = TenantTool.tenantId(headers);
    if (isDisable(tenantAttributes)) {
      log.info("Tenant: {}, removing scheduled job on disable", tenantId);
      SchedulingUtil.deleteJob(tenantId);
    }
    super.postTenant(tenantAttributes, headers, handler, context);
  }

  @Override
  Future<Integer> loadData(
      TenantAttributes attributes,
      String tenantId,
      Map<String, String> headers,
      Context vertxContext) {
    return PeriodicConfigPgUtil.get(vertxContext, tenantId)
        .onFailure(
            cause ->
                log.error(
                    "Tenant: {}, failed getting PeriodicConfig: {}", tenantId, cause.getMessage()))
        .map(
            config -> {
              SchedulingUtil.createOrUpdateJob(config, tenantId);
              return 0;
            })
        .transform(ar -> Future.succeededFuture(0));
  }

  private static boolean isDisable(TenantAttributes tenantAttributes) {
    return tenantAttributes != null
        && tenantAttributes.getModuleFrom() != null
        && tenantAttributes.getModuleTo() == null;
  }
}
