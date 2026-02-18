package org.olf.erm.usage.harvester.client;

import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.TOKEN;

import io.vertx.core.Future;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import java.util.List;
import java.util.Optional;
import org.folio.settings.Entries;
import org.folio.settings.Entry;

public class SettingsClientImpl implements SettingsClient {

  @SuppressWarnings(
      "java:S1075") // suppress "URIs should not be hardcoded" because this is an internal API path
  public static final String ENTRIES_PATH = "/settings/entries";

  public static final String QUERY_PARAM = "query";
  public static final String QUERY_TEMPLATE = "(scope==\"%s\" AND key==\"%s\")";
  private final String okapiUrl;
  private final String tenantId;
  private final String token;
  private final WebClient webClient;

  public SettingsClientImpl(String okapiUrl, String tenantId, String token, WebClient webClient) {
    this.okapiUrl = okapiUrl;
    this.tenantId = tenantId;
    this.token = token;
    this.webClient = webClient;
  }

  @Override
  public Future<Optional<Object>> getValue(String scope, String key) {
    String uri = okapiUrl + ENTRIES_PATH;
    String query = QUERY_TEMPLATE.formatted(scope, key);
    return webClient
        .getAbs(uri)
        .putHeader(TENANT, tenantId)
        .putHeader(TOKEN, token)
        .addQueryParam(QUERY_PARAM, query)
        .as(BodyCodec.json(Entries.class))
        .send()
        .map(
            resp -> {
              List<Entry> items = resp.body().getItems();
              return items.isEmpty()
                  ? Optional.empty()
                  : Optional.ofNullable(resp.body().getItems().getFirst().getValue());
            });
  }

  /**
   * Parses a settings value as an Integer. Handles both String and Integer values.
   *
   * @param value the value to parse
   * @return the parsed Integer value
   * @throws IllegalArgumentException if the value cannot be parsed as an Integer
   */
  public static Integer parseIntegerValue(Object value) {
    if (value instanceof Integer i) {
      return i;
    }
    if (value instanceof String s) {
      return Integer.parseInt(s);
    }
    throw new IllegalArgumentException("Cannot parse value as Integer: " + value);
  }
}
