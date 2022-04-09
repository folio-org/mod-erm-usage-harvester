package org.olf.erm.usage.harvester;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.olf.erm.usage.harvester.Messages.MSG_RESPONSE_BODY_IS_NULL;
import static org.olf.erm.usage.harvester.Messages.createMsgStatus;
import static org.olf.erm.usage.harvester.Messages.createProviderMsg;

import io.vertx.core.Future;
import io.vertx.ext.web.client.WebClient;
import java.util.Date;
import java.util.Objects;
import org.folio.rest.client.UsageDataProvidersClient;
import org.folio.rest.jaxrs.model.HarvestingConfig.HarvestingStatus;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.folio.rest.jaxrs.model.UsageDataProviders;
import org.folio.rest.jaxrs.model.UsageDataProvidersGetOrder;
import org.folio.rest.tools.utils.VertxUtils;

public class ExtUsageDataProvidersClient extends UsageDataProvidersClient {

  private static final String UDP_PATH = "/usage-data-providers";

  public ExtUsageDataProvidersClient(String okapiUrl, String tenantId, String token) {
    super(okapiUrl, tenantId, token, WebClient.create(VertxUtils.getVertxFromContextOrNew()));
  }

  public Future<Void> updateUDPLastHarvestingDate(UsageDataProvider udp, Date date) {
    return super.putUsageDataProvidersById(udp.getId(), null, udp.withHarvestingDate(date))
        .<Void>transform(
            ar -> {
              if (ar.succeeded()) {
                if (ar.result().statusCode() == 204) {
                  return succeededFuture();
                } else {
                  return failedFuture(
                      createProviderMsg(
                          udp.getLabel(),
                          "Failed updating harvestingDate: {}",
                          createMsgStatus(
                              ar.result().statusCode(), ar.result().statusMessage(), "putUDPUrl")));
                }
              } else {
                return failedFuture(
                    createProviderMsg(
                        udp.getLabel(),
                        "Failed updating harvestingDate: {}",
                        ar.cause().getMessage()));
              }
            });
  }

  public Future<UsageDataProviders> getActiveProviders() {
    final String queryStr =
        String.format("(harvestingConfig.harvestingStatus=%s)", HarvestingStatus.ACTIVE);

    return super.getUsageDataProviders(
            queryStr, null, UsageDataProvidersGetOrder.ASC, 0, Integer.MAX_VALUE, null)
        .transform(
            ar -> {
              if (ar.succeeded()) {
                if (ar.result().statusCode() == 200) {
                  return succeededFuture(
                      Objects.requireNonNull(
                          ar.result().bodyAsJson(UsageDataProviders.class),
                          MSG_RESPONSE_BODY_IS_NULL));
                }
                return failedFuture(
                    createMsgStatus(
                        ar.result().statusCode(), ar.result().statusMessage(), UDP_PATH));
              } else {
                return failedFuture(ar.cause());
              }
            });
  }

  public Future<UsageDataProvider> getActiveProviderById(String providerId) {
    return super.getUsageDataProvidersById(providerId, null)
        .flatMap(
            resp -> {
              if (resp.statusCode() == 200) {
                return succeededFuture(
                    Objects.requireNonNull(
                        resp.bodyAsJson(UsageDataProvider.class), MSG_RESPONSE_BODY_IS_NULL));
              } else {
                return failedFuture(
                    createProviderMsg(
                        providerId,
                        createMsgStatus(resp.statusCode(), resp.statusMessage(), UDP_PATH)));
              }
            })
        .flatMap(
            udp -> {
              if (udp.getHarvestingConfig().getHarvestingStatus().equals(HarvestingStatus.ACTIVE)) {
                return succeededFuture(udp);
              } else {
                return failedFuture(createProviderMsg(providerId, "HarvestingStatus not ACTIVE"));
              }
            });
  }
}
