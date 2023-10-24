package org.olf.erm.usage.harvester.client;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static org.olf.erm.usage.harvester.HttpResponseUtil.getResponseBodyIfStatus200;

import io.vertx.core.Future;
import io.vertx.ext.web.client.WebClient;
import org.folio.rest.client.ConfigurationsClient;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configs;
import org.folio.rest.tools.utils.VertxUtils;

public class ExtConfigurationsClientImpl extends ConfigurationsClient
    implements ExtConfigurationsClient {

  public static final String PATH = "/configurations/entries"; // NOSONAR
  public static final String NO_ENTRY = "No configuration entry found";

  public ExtConfigurationsClientImpl(String okapiUrl, String tenantId, String token) {
    super(okapiUrl, tenantId, token, WebClient.create(VertxUtils.getVertxFromContextOrNew()));
  }

  public ExtConfigurationsClientImpl(
      String okapiUrl, String tenantId, String token, WebClient webClient) {
    super(okapiUrl, tenantId, token, webClient);
  }

  @Override
  public Future<String> getModConfigurationValue(String module, String configName) {
    final String queryStr = format("(module = %s and configName = %s)", module, configName);
    return super.getConfigurationsEntries(queryStr, 0, 1, null, null)
        .transform(ar -> getResponseBodyIfStatus200(ar, Configs.class))
        .flatMap(
            config ->
                (config.getConfigs().isEmpty())
                    ? failedFuture(NO_ENTRY)
                    : succeededFuture(config.getConfigs().get(0)))
        .map(Config::getValue);
  }
}
