package org.olf.erm.usage.harvester.client;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.olf.erm.usage.harvester.HttpResponseUtil.getResponseBodyIfStatus200;
import static org.olf.erm.usage.harvester.Messages.createMsgStatus;
import static org.olf.erm.usage.harvester.Messages.createProviderMsg;

import io.vertx.core.Future;
import io.vertx.ext.web.client.WebClient;
import java.util.Date;
import org.folio.rest.client.UsageDataProvidersClient;
import org.folio.rest.jaxrs.model.HarvestingConfig.HarvestingStatus;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.folio.rest.jaxrs.model.UsageDataProviders;
import org.folio.rest.jaxrs.model.UsageDataProvidersGetOrder;
import org.folio.rest.tools.utils.VertxUtils;

public class ExtUsageDataProvidersClientImpl extends UsageDataProvidersClient
    implements ExtUsageDataProvidersClient {

  public static final String PATH = "/usage-data-providers";

  public ExtUsageDataProvidersClientImpl(String okapiUrl, String tenantId, String token) {
    super(okapiUrl, tenantId, token, WebClient.create(VertxUtils.getVertxFromContextOrNew()));
  }

  public ExtUsageDataProvidersClientImpl(
      String okapiUrl, String tenantId, String token, WebClient webClient) {
    super(okapiUrl, tenantId, token, webClient);
  }

  @Override
  public Future<Void> updateUDPLastHarvestingDate(UsageDataProvider udp, Date date) {
    return super.putUsageDataProvidersById(udp.getId(), udp.withHarvestingDate(date))
        .transform(
            ar -> {
              if (ar.succeeded()) {
                if (ar.result().statusCode() == 204) {
                  return succeededFuture();
                } else {
                  return failedFuture(
                      createProviderMsg(
                          udp.getLabel(),
                          "Failed updating harvestingDate: {}",
                          createMsgStatus(ar.result().statusCode(), ar.result().statusMessage())));
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

  @Override
  public Future<UsageDataProviders> getActiveProviders() {
    final String queryStr =
        String.format("(harvestingConfig.harvestingStatus=%s)", HarvestingStatus.ACTIVE);

    return super.getUsageDataProviders(
            queryStr, null, UsageDataProvidersGetOrder.ASC, null, 0, Integer.MAX_VALUE)
        .transform(ar -> getResponseBodyIfStatus200(ar, UsageDataProviders.class));
  }

  @Override
  public Future<UsageDataProvider> getActiveProviderById(String providerId) {
    return super.getUsageDataProvidersById(providerId)
        .transform(ar -> getResponseBodyIfStatus200(ar, UsageDataProvider.class))
        .transform(
            ar ->
                (ar.succeeded())
                    ? Future.succeededFuture(ar.result())
                    : Future.failedFuture(createProviderMsg(providerId, ar.cause().getMessage())))
        .flatMap(
            udp ->
                (udp.getHarvestingConfig().getHarvestingStatus().equals(HarvestingStatus.ACTIVE))
                    ? succeededFuture(udp)
                    : failedFuture(createProviderMsg(providerId, "HarvestingStatus not ACTIVE")));
  }
}
