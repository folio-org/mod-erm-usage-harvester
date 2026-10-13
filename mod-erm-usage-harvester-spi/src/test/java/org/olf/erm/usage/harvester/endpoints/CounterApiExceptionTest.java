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
  void testParseDataObjectAsJsonString() {
    assertThat(
            CounterExceptionParser.parse("{\"Code\":3040,\"Data\":{\"Begin_Date\":\"2025-01\"}}"))
        .hasValueSatisfying(
            exceptions ->
                assertThat(exceptions.getFirst().data()).isEqualTo("{\"Begin_Date\":\"2025-01\"}"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "   ",
        "[]",
        "{}",
        "{\"Message\":\"no code\"}",
        "{\"Code\":\"1020\"}",
        "{\"Code\":null}",
        "[{\"Code\":1020},\"foo\"]",
        "[{\"Code\":1020},{\"Message\":\"no code\"}]",
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
    assertThat(CounterApiException.fromReportHeader("[{\"Message\":\"no code\"}]")).isEmpty();
    assertThat(CounterApiException.fromReportHeader(null)).isEmpty();
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
  void testFromBareBodyReturnsEmptyForNonCounterBody() {
    assertThat(
            CounterApiException.fromBareBody(
                500, "Internal Server Error", "<html><body>Error</body></html>"))
        .isEmpty();
  }

  @Test
  void testDecidingExceptionIsLowestCode() {
    CounterApiException e = CounterApiException.fromReportHeader(EXCEPTION_ARRAY).orElseThrow();

    assertThat(e.decidingException().code()).isEqualTo(1020);
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
