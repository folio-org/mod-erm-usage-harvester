package org.olf.erm.usage.harvester;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static org.olf.erm.usage.harvester.Messages.createMsgStatus;

import io.vertx.core.Future;
import io.vertx.ext.web.client.WebClient;
import org.folio.rest.client.AggregatorSettingsClient;
import org.folio.rest.jaxrs.model.Aggregator;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.folio.rest.tools.utils.VertxUtils;

public class ExtAggregatorSettingsClient extends AggregatorSettingsClient {

  public static final String PATH = "/aggregator-settings";

  public ExtAggregatorSettingsClient(String okapiUrl, String tenantId, String token) {
    super(okapiUrl, tenantId, token, WebClient.create(VertxUtils.getVertxFromContextOrNew()));
  }

  public Future<AggregatorSetting> getAggregatorSetting(UsageDataProvider provider) {
    Aggregator aggregator = provider.getHarvestingConfig().getAggregator();
    if (aggregator == null || aggregator.getId() == null) {
      return Future.failedFuture(
          format("No aggregator present for provider %s", provider.getLabel()));
    }

    return super.getAggregatorSettingsById(aggregator.getId(), null)
        .flatMap(
            resp -> {
              if (resp.statusCode() == 200) {
                AggregatorSetting setting = resp.bodyAsJson(AggregatorSetting.class);
                return succeededFuture(setting);
              } else {
                return failedFuture(createMsgStatus(resp.statusCode(), resp.statusMessage(), PATH));
              }
            })
        .transform(
            ar -> {
              if (ar.succeeded()) {
                return succeededFuture(ar.result());
              } else {
                return failedFuture(
                    format(
                        "Failed getting AggregatorSetting for id %s: %s",
                        aggregator.getId(), ar.cause().getMessage()));
              }
            });
  }
}
