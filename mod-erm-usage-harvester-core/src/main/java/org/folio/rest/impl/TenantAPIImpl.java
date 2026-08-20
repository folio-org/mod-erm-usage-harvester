package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.tools.utils.TenantTool;
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
    Handler<AsyncResult<Response>> wrappedHandler = handler;
    if (isDisableOrPurge(tenantAttributes)) {
      wrappedHandler =
          ar -> {
            if (ar.succeeded() && ar.result().getStatus() / 100 == 2) {
              log.info("Tenant: {}, removing scheduled job on disable or purge", tenantId);
              SchedulingUtil.deleteJob(tenantId);
            }
            handler.handle(ar);
          };
    }
    super.postTenant(tenantAttributes, headers, wrappedHandler, context);
  }

  @Override
  Future<Integer> loadData(
      TenantAttributes attributes,
      String tenantId,
      Map<String, String> headers,
      Context vertxContext) {
    return SchedulingUtil.createOrUpdateJobFromDbConfig(vertxContext, tenantId)
        .transform(ar -> Future.succeededFuture(0));
  }

  private static boolean isDisableOrPurge(TenantAttributes tenantAttributes) {
    if (tenantAttributes == null) {
      return false;
    }
    boolean isPurge = Boolean.TRUE.equals(tenantAttributes.getPurge());
    boolean isDisable =
        tenantAttributes.getModuleFrom() != null && tenantAttributes.getModuleTo() == null;
    return isPurge || isDisable;
  }
}
