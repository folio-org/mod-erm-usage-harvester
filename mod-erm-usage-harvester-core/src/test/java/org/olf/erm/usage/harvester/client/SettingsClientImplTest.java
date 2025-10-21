package org.olf.erm.usage.harvester.client;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.olf.erm.usage.harvester.client.SettingsClientImpl.ENTRIES_PATH;
import static org.olf.erm.usage.harvester.client.SettingsClientImpl.QUERY_PARAM;
import static org.olf.erm.usage.harvester.client.SettingsClientImpl.QUERY_TEMPLATE;
import static org.olf.erm.usage.harvester.client.SettingsClientImpl.parseIntegerValue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.settings.Entries;
import org.folio.settings.Entry;
import org.folio.settings.ResultInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(VertxExtension.class)
@WireMockTest
class SettingsClientImplTest {

  private static final String TENANT = "someTenant";
  private static final String TOKEN = "someToken";
  private static final String SCOPE = "testscope";
  private static final String KEY = "testkey";
  private static final String QUERY = QUERY_TEMPLATE.formatted(SCOPE, KEY);
  private static final String LOCALHOST_URL_TEMPLATE = "http://localhost:%d";
  private static final Vertx vertx = Vertx.vertx();
  private static final WebClient webClient = WebClient.create(vertx);
  private static SettingsClient settingsClient;

  @BeforeAll
  static void beforeAll(WireMockRuntimeInfo wmRuntimeInfo) {
    settingsClient =
        new SettingsClientImpl(wmRuntimeInfo.getHttpBaseUrl(), TENANT, TOKEN, webClient);
  }

  private static Stream<Object> provideObjectValues() throws JsonProcessingException {
    return Stream.of(
        "string",
        true,
        12,
        null,
        new ObjectMapper()
            .readValue("{ \"foo\": 123}", new TypeReference<Map<String, Object>>() {}),
        new ObjectMapper().readValue("[ 1, 2, 3 ]", new TypeReference<List<Object>>() {}));
  }

  private Entries createEntries(List<Entry> entries) {
    return new Entries()
        .withItems(entries)
        .withResultInfo(new ResultInfo().withTotalRecords(entries.size()));
  }

  private Entry createEntry(Object value) {
    return new Entry().withId(UUID.randomUUID()).withScope(SCOPE).withKey(KEY).withValue(value);
  }

  private void stubEntriesWithResponse(ResponseDefinitionBuilder response) {
    stubFor(
        get(urlPathEqualTo(ENTRIES_PATH))
            .withHeader(XOkapiHeaders.TENANT, equalTo(TENANT))
            .withHeader(XOkapiHeaders.TOKEN, equalTo(TOKEN))
            .withQueryParam(QUERY_PARAM, equalTo(QUERY))
            .willReturn(response));
  }

  @ParameterizedTest
  @MethodSource("provideObjectValues")
  void testGetValueWithDifferentObjects(Object object, VertxTestContext context) {
    Entries entries = createEntries(List.of(createEntry(object)));
    stubEntriesWithResponse(jsonResponse(entries, 200));
    settingsClient
        .getValue(SCOPE, KEY)
        .onComplete(
            context.succeeding(
                opt -> {
                  if (object == null) {
                    assertThat(opt).isEmpty();
                  } else {
                    assertThat(opt).get().isEqualTo(object);
                  }
                  context.completeNow();
                }));
  }

  @Test
  void testGetValueWithEmptyResults(VertxTestContext context) {
    stubEntriesWithResponse(okJson(Json.encode(createEntries(Collections.emptyList()))));
    settingsClient
        .getValue(SCOPE, KEY)
        .onComplete(
            context.succeeding(
                opt -> {
                  assertThat(opt).isEmpty();
                  context.completeNow();
                }));
  }

  @Test
  void testGetValueWithEmptyResponseBody(VertxTestContext context) {
    stubEntriesWithResponse(ok(""));
    settingsClient.getValue(SCOPE, KEY).onComplete(context.failingThenComplete());
  }

  @Test
  void testGetValueWith404Response(VertxTestContext context) {
    stubEntriesWithResponse(notFound());
    settingsClient.getValue(SCOPE, KEY).onComplete(context.failingThenComplete());
  }

  @Test
  void testGetValueWithUnavailableService(VertxTestContext context) {
    int nextFreePort = NetworkUtils.nextFreePort();
    SettingsClient unavailableClient =
        new SettingsClientImpl(
            LOCALHOST_URL_TEMPLATE.formatted(nextFreePort), TENANT, TOKEN, webClient);
    unavailableClient.getValue(SCOPE, KEY).onComplete(context.failingThenComplete());
  }

  private static Stream<Object> provideValidIntegerValues() {
    return Stream.of("5", "0", "-10", "2147483647", "-2147483648", 5, 0, -10, 2147483647);
  }

  private static Stream<Object> provideInvalidIntegerValues() {
    return Stream.of(
        "abc",
        "12.5",
        "",
        "  ",
        true,
        false,
        Json.decodeValue("{ \"foo\": 123}"),
        Json.decodeValue("[ 1, 2, 3 ]"));
  }

  @ParameterizedTest
  @MethodSource("provideValidIntegerValues")
  void testParseIntegerValueWithValidInputs(Object input) {
    Integer result = parseIntegerValue(input);
    if (input instanceof String s) {
      assertThat(result).isEqualTo(Integer.parseInt(s));
    } else {
      assertThat(result).isEqualTo(input);
    }
  }

  @ParameterizedTest
  @MethodSource("provideInvalidIntegerValues")
  void testParseIntegerValueWithInvalidInputs(Object input) {
    assertThatThrownBy(() -> parseIntegerValue(input)).isInstanceOf(Exception.class);
  }

  @Test
  void testParseIntegerValueWithNull() {
    assertThatThrownBy(() -> parseIntegerValue(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot parse value as Integer");
  }
}
