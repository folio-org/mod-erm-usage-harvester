package org.olf.erm.usage.harvester.client;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static org.olf.erm.usage.harvester.HttpResponseUtil.getResponseBodyIfStatus200;
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
        .transform(ar -> getResponseBodyIfStatus200(ar, AggregatorSetting.class))
        .transform(
            ar ->
                (ar.succeeded())
                    ? succeededFuture(ar.result())
                    : failedFuture(
                        format(
                            "Failed getting AggregatorSetting for id %s: %s",
                            aggregator.getId(), ar.cause().getMessage())));
  }
}
