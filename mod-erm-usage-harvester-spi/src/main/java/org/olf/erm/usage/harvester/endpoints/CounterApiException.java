package org.olf.erm.usage.harvester.endpoints;

import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingConstants.MAX_ERROR_BODY_LENGTH;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** ... */
public final class CounterApiException extends RuntimeException {

  private final List<CounterException> exceptions;

  private final CounterExceptionDelivery delivery;

  private final Integer statusCode;

  private CounterApiException(
      final String message,
      final List<CounterException> exceptions,
      final CounterExceptionDelivery delivery,
      final Integer statusCode) {
    super(message);

    if (exceptions == null || exceptions.isEmpty()) {
      throw new IllegalArgumentException("exceptions must not be empty");
    }

    this.exceptions = List.copyOf(exceptions);
    this.delivery = Objects.requireNonNull(delivery);
    this.statusCode = statusCode;
  }

  /**
   * Creates the exception for exceptions found in the {@code Report_Header} of a report.
   *
   * @param exceptionsJson the {@code Exceptions} as a JSON array or object
   * @return the exception, or empty if {@code exceptionsJson} isn't a list of COUNTER exceptions
   */
  public static Optional<CounterApiException> fromReportHeader(final String exceptionsJson) {
    return CounterExceptionParser.parse(exceptionsJson)
        .map(
            list ->
                new CounterApiException(
                    "Report not valid: " + exceptionsJson,
                    list,
                    CounterExceptionDelivery.REPORT_HEADER,
                    null));
  }

  /**
   * Creates the exception for a response body that consists only of COUNTER exceptions.
   *
   * @param statusCode the HTTP status of the response
   * @param statusMessage the HTTP status message, ignored for 2xx
   * @param body the response body
   * @return the exception, or empty if {@code body} isn't a list of COUNTER exceptions
   */
  public static Optional<CounterApiException> fromBareBody(
      final int statusCode, final String statusMessage, final String body) {
    final var success = statusCode >= 200 && statusCode < 300;
    final var abbreviatedBody = abbreviate(body, MAX_ERROR_BODY_LENGTH);
    final var message =
        success
            ? abbreviatedBody
            : "HTTP " + statusCode + ": " + statusMessage + " - " + abbreviatedBody;
    final var delivery =
        success
            ? CounterExceptionDelivery.BARE_SUCCESS_STATUS
            : CounterExceptionDelivery.BARE_ERROR_STATUS;
    return CounterExceptionParser.parse(body)
        .map(list -> new CounterApiException(message, list, delivery, statusCode));
  }

  /**
   * @return the list of all the exceptions received
   */
  public List<CounterException> exceptions() {
    return exceptions;
  }

  /**
   * @return how the exception was delivered
   */
  public CounterExceptionDelivery delivery() {
    return delivery;
  }

  /**
   * @return the received HTTP status code
   */
  public Integer statusCode() {
    return statusCode;
  }

  /**
   * @return the {@link CounterException} deciding the error handling behavior.
   */
  public CounterException decidingException() {
    return exceptions.stream().min(Comparator.comparingInt(CounterException::code)).orElseThrow();
  }

  /**
   * @return the list of all the error codes received
   */
  public List<Integer> codes() {
    return exceptions.stream().map(CounterException::code).toList();
  }

  /**
   * @return {@code true} if the error codes received contain the code
   */
  public boolean containsCode(final int code) {
    return codes().contains(code);
  }

  /** ... */
  public record CounterException(int code, String message, String data, String helpUrl) {}

  /** ... */
  public enum CounterExceptionDelivery {
    /** inside Report_Header exceptions from an otherwise valid report */
    REPORT_HEADER,

    /** body is only exception(s), HTTP 2xx */
    BARE_SUCCESS_STATUS,

    /** body is only exception(s), HTTP 4xx/5xx */
    BARE_ERROR_STATUS
  }

  /** ... */
  static final class CounterExceptionParser {

    private CounterExceptionParser() {
      // Utility class
    }

    /**
     * @return the list of {@link CounterException}s extracted from the response string
     */
    static Optional<List<CounterException>> parse(final String responseBody) {
      if (responseBody == null || responseBody.isBlank()) {
        return Optional.empty();
      }

      final Object decoded;
      try {
        decoded = Json.decodeValue(responseBody);
      } catch (DecodeException e) {
        return Optional.empty();
      }

      final List<Object> candidates =
          switch (decoded) {
            case JsonObject o -> List.of(o);
            case JsonArray a -> a.stream().toList();
            case null, default -> List.of();
          };

      if (candidates.isEmpty()) {
        return Optional.empty();
      }

      final List<CounterException> exceptions =
          candidates.stream()
              .map(CounterExceptionParser::toCounterException)
              .flatMap(Optional::stream)
              .toList();

      // If we couldn't map each of the received error objects, we return an empty optional
      return Optional.of(exceptions)
          .filter(list -> !list.isEmpty() && list.size() == candidates.size());
    }

    private static Optional<CounterException> toCounterException(final Object candidate) {
      if (!(candidate instanceof JsonObject o)) {
        return Optional.empty();
      }

      return toCode(o.getValue("Code"))
          .map(
              code ->
                  new CounterException(
                      code,
                      asString(o.getValue("Message")),
                      asString(o.getValue("Data")),
                      asString(o.getValue("Help_URL"))));
    }

    private static Optional<Integer> toCode(final Object value) {
      return switch (value) {
        case Number n -> Optional.of(n.intValue());
        case String s when s.matches("\\d{1,9}") -> Optional.of(Integer.parseInt(s));
        case null, default -> Optional.empty();
      };
    }

    private static String asString(final Object value) {
      return value == null ? null : value.toString();
    }
  }
}
