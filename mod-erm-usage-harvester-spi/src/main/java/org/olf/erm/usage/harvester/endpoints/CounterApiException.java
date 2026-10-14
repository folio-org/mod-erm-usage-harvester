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

/**
 * COUNTER exceptions returned by a provider instead of (or within) a report, carrying the codes so
 * an {@link ErrorHandlingStrategy} can decide per code. Created by the {@code from…} factories; the
 * message equals the failed reason recorded for the affected reports.
 */
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
   * @return the HTTP status of the response, or {@code null} for {@link
   *     CounterExceptionDelivery#REPORT_HEADER}
   */
  public Integer statusCode() {
    return statusCode;
  }

  /**
   * The exception with the lowest code; the COUNTER CoP defines no precedence.
   *
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
   * @param code the COUNTER exception code
   * @return {@code true} if the error codes received contain the code
   */
  public boolean containsCode(final int code) {
    return codes().contains(code);
  }

  /**
   * One COUNTER exception object (CoP 5.1 Appendix D).
   *
   * @param code the exception code, e.g. 1020
   * @param message the {@code Message}, may be {@code null}
   * @param data the {@code Data}, as JSON text if it isn't a string, may be {@code null}
   * @param helpUrl the {@code Help_URL}, may be {@code null}
   */
  public record CounterException(int code, String message, String data, String helpUrl) {}

  /** How the provider delivered the COUNTER exceptions. */
  public enum CounterExceptionDelivery {
    /** inside Report_Header exceptions from an otherwise valid report */
    REPORT_HEADER,

    /** body is only exception(s), HTTP 2xx */
    BARE_SUCCESS_STATUS,

    /** body is only exception(s), HTTP 4xx/5xx */
    BARE_ERROR_STATUS
  }

  /**
   * Maps JSON to {@link CounterException}s: one object or an array of objects with a numeric (or
   * numeric string) {@code Code}; elements that aren't valid COUNTER exceptions are skipped.
   */
  static final class CounterExceptionParser {

    private CounterExceptionParser() {
      // Utility class
    }

    /**
     * @param responseBody the JSON to parse
     * @return the valid {@link CounterException}s, or empty if there is none
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

      // Skip invalid elements, so that one malformed entry doesn't hide e.g. a 1020
      return Optional.of(exceptions).filter(list -> !list.isEmpty());
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
