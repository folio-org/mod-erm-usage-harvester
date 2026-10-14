package org.olf.erm.usage.harvester.endpoints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingConstants.MAX_ERROR_BODY_LENGTH;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.olf.erm.usage.harvester.endpoints.CounterApiException.CounterException;
import org.olf.erm.usage.harvester.endpoints.CounterApiException.CounterExceptionDelivery;
import org.olf.erm.usage.harvester.endpoints.CounterApiException.CounterExceptionParser;

/** Tests {@link CounterApiException} and its {@link CounterExceptionParser}. */
class CounterApiExceptionTest {

  private static final String SINGLE_EXCEPTION =
      """
      {"Code":1020,"Severity":"Warning","Message":"Too many requests",\
      "Data":"Retry later","Help_URL":"https://example.org/help"}\
      """;

  private static final String EXCEPTION_ARRAY =
      """
      [{"Code":3030,"Message":"No Usage Available for Requested Dates"},\
      {"Code":1020},{"Code":3031,"Message":"Usage Not Ready for Requested Dates"}]\
      """;

  @Test
  void testParseSingleObject() {
    assertThat(CounterExceptionParser.parse(SINGLE_EXCEPTION))
        .contains(
            List.of(
                new CounterException(
                    1020, "Too many requests", "Retry later", "https://example.org/help")));
  }

  @Test
  void testParseArrayKeepsOrderAndLeavesMissingFieldsNull() {
    assertThat(CounterExceptionParser.parse(EXCEPTION_ARRAY))
        .hasValueSatisfying(
            exceptions -> {
              assertThat(exceptions)
                  .extracting(CounterException::code)
                  .containsExactly(3030, 1020, 3031);
              assertThat(exceptions.get(1)).isEqualTo(new CounterException(1020, null, null, null));
            });
  }

  @Test
  void testParseCodeAsNumericString() {
    assertThat(CounterExceptionParser.parse("{\"Code\":\"2020\",\"Message\":\"api_key Invalid\"}"))
        .contains(List.of(new CounterException(2020, "api_key Invalid", null, null)));
  }

  @Test
  void testParseDataObjectAsJsonString() {
    assertThat(
            CounterExceptionParser.parse("{\"Code\":3040,\"Data\":{\"Begin_Date\":\"2025-01\"}}"))
        .hasValueSatisfying(
            exceptions ->
                assertThat(exceptions.getFirst().data()).isEqualTo("{\"Begin_Date\":\"2025-01\"}"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "[{\"Code\":1020},\"foo\"]",
        "[{\"Code\":null},{\"Code\":1020}]",
        "[{\"Code\":\"abc\"},{\"Code\":1020}]"
      })
  void testParseSkipsInvalidElements(String body) {
    assertThat(CounterExceptionParser.parse(body))
        .contains(List.of(new CounterException(1020, null, null, null)));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "   ",
        "[]",
        "{}",
        "{\"Code\":\"abc\"}",
        "{\"Code\":\"\"}",
        "{\"Code\":\"-1\"}",
        "{\"Code\":\"99999999999\"}",
        "{\"Code\":null}",
        "{\"Code\":1020.7}",
        "{\"Code\":4294968316}",
        "<html><body>Service Unavailable</body></html>",
        "{\"Code\":1020",
        "null",
        "42",
        "\"text\"",
        "{\"Report_Header\":{\"Report_ID\":\"TR\"},\"Report_Items\":[]}"
      })
  void testParseReturnsEmptyForNonCounterExceptions(String body) {
    assertThat(CounterExceptionParser.parse(body)).isEmpty();
  }

  @Test
  void testFromReportHeader() {
    assertThat(CounterApiException.fromReportHeader(EXCEPTION_ARRAY))
        .hasValueSatisfying(
            e -> {
              assertThat(e.delivery()).isEqualTo(CounterExceptionDelivery.REPORT_HEADER);
              assertThat(e.statusCode()).isNull();
              assertThat(e.getMessage()).isEqualTo("Report not valid: " + EXCEPTION_ARRAY);
              assertThat(e.codes()).containsExactly(3030, 1020, 3031);
            });
  }

  @Test
  void testFromReportHeaderReturnsEmptyForInvalidJson() {
    assertThat(CounterApiException.fromReportHeader("[{\"Severity\":\"Error\"}]")).isEmpty();
    assertThat(CounterApiException.fromReportHeader(null)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {SINGLE_EXCEPTION, EXCEPTION_ARRAY, "<html/>", "{}"})
  void testFromBareBodyWithDecodedJsonMatchesStringVariant(String body) {
    var fromString = CounterApiException.fromBareBody(400, "Bad Request", body);
    var fromDecoded =
        CounterApiException.fromBareBody(
            400, "Bad Request", body, CounterExceptionParser.decode(body));

    assertThat(fromDecoded.map(CounterApiException::getMessage))
        .isEqualTo(fromString.map(CounterApiException::getMessage));
    assertThat(fromDecoded.map(CounterApiException::exceptions))
        .isEqualTo(fromString.map(CounterApiException::exceptions));
  }

  @Test
  void testFromBareBodyWithNullJsonReturnsEmpty() {
    assertThat(CounterApiException.fromBareBody(200, null, SINGLE_EXCEPTION, null)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(ints = {200, 202, 299})
  void testFromBareBodyWithSuccessStatus(int statusCode) {
    assertThat(CounterApiException.fromBareBody(statusCode, "OK", SINGLE_EXCEPTION))
        .hasValueSatisfying(
            e -> {
              assertThat(e.delivery()).isEqualTo(CounterExceptionDelivery.BARE_SUCCESS_STATUS);
              assertThat(e.statusCode()).isEqualTo(statusCode);
              assertThat(e.getMessage()).isEqualTo(SINGLE_EXCEPTION);
              assertThat(e.codes()).containsExactly(1020);
            });
  }

  @ParameterizedTest
  @ValueSource(ints = {300, 400, 429, 500})
  void testFromBareBodyWithErrorStatus(int statusCode) {
    assertThat(CounterApiException.fromBareBody(statusCode, "Some Status", SINGLE_EXCEPTION))
        .hasValueSatisfying(
            e -> {
              assertThat(e.delivery()).isEqualTo(CounterExceptionDelivery.BARE_ERROR_STATUS);
              assertThat(e.statusCode()).isEqualTo(statusCode);
              assertThat(e.getMessage())
                  .isEqualTo("HTTP " + statusCode + ": Some Status - " + SINGLE_EXCEPTION);
            });
  }

  @Test
  void testFromBareBodyAbbreviatesLongBodyInMessageOnly() {
    String data = "x".repeat(MAX_ERROR_BODY_LENGTH + 1000);
    String body = "{\"Code\":3030,\"Data\":\"" + data + "\"}";

    assertThat(CounterApiException.fromBareBody(200, "OK", body))
        .hasValueSatisfying(
            e -> {
              assertThat(e.getMessage()).hasSize(MAX_ERROR_BODY_LENGTH).endsWith("...");
              assertThat(e.exceptions().getFirst().data()).isEqualTo(data);
            });
  }

  @Test
  void testFromReportHeaderAbbreviatesLongExceptionsInMessageOnly() {
    String data = "x".repeat(MAX_ERROR_BODY_LENGTH + 1000);
    String json = "[{\"Code\":3030,\"Data\":\"" + data + "\"}]";

    assertThat(CounterApiException.fromReportHeader(json))
        .hasValueSatisfying(
            e -> {
              assertThat(e.getMessage())
                  .startsWith("Report not valid: [{")
                  .hasSize("Report not valid: ".length() + MAX_ERROR_BODY_LENGTH)
                  .endsWith("...");
              assertThat(e.exceptions().getFirst().data()).isEqualTo(data);
            });
  }

  @Test
  void testFromBareBodyReturnsEmptyForNonCounterBody() {
    assertThat(
            CounterApiException.fromBareBody(
                500, "Internal Server Error", "<html><body>Error</body></html>"))
        .isEmpty();
  }

  @Test
  void testParseKeepsEntriesWithMessageButWithoutCode() {
    assertThat(CounterExceptionParser.parse("[{\"Code\":1020},{\"Message\":\"no code\"}]"))
        .contains(
            List.of(
                new CounterException(1020, null, null, null),
                new CounterException(null, "no code", null, null)));
  }

  @Test
  void testParseAcceptsWholeNumberAsDecimal() {
    assertThat(CounterExceptionParser.parse("{\"Code\":3030.0}"))
        .contains(List.of(new CounterException(3030, null, null, null)));
  }

  @Test
  void testParseKeepsMessageOfEntryWithFractionalCode() {
    assertThat(CounterExceptionParser.parse("{\"Code\":1020.7,\"Message\":\"x\"}"))
        .contains(List.of(new CounterException(null, "x", null, null)));
  }

  @Test
  void testCodesSkipsEntriesWithoutCode() {
    CounterApiException e =
        CounterApiException.fromReportHeader("[{\"Message\":\"no code\"},{\"Code\":3030}]")
            .orElseThrow();

    assertThat(e.codes()).containsExactly(3030);
    assertThat(e.containsCode(3030)).isTrue();
  }

  @Test
  void testContainsCode() {
    CounterApiException e = CounterApiException.fromReportHeader(EXCEPTION_ARRAY).orElseThrow();

    assertThat(e.containsCode(1020)).isTrue();
    assertThat(e.containsCode(3031)).isTrue();
    assertThat(e.containsCode(1011)).isFalse();
  }

  @Test
  @SuppressWarnings("DataFlowIssue") // modifying the immutable list is what we test
  void testExceptionsAreUnmodifiable() {
    CounterApiException e = CounterApiException.fromReportHeader(SINGLE_EXCEPTION).orElseThrow();

    assertThatThrownBy(() -> e.exceptions().add(new CounterException(1, null, null, null)))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
