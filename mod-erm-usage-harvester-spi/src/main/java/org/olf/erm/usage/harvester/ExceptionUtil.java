package org.olf.erm.usage.harvester;

import java.net.URI;
import org.folio.rest.jaxrs.model.ApiException;

public class ExceptionUtil {

  private ExceptionUtil() {}

  public static String getMessageOrToString(Throwable t) {
    return (t.getMessage() != null) ? t.getMessage() : t.toString();
  }

  /**
   * TODO: We need to implement this right
   *
   * @param t the thrown exception we need to convert
   * @return the properly structured API exception
   */
  public static ApiException getApiExceptionFrom(final Throwable t) {
    final var code = 123;
    final var message = t.getMessage();
    final var helpURL = URI.create("https://www.google.com");
    final var data = "Some additional data";

    return new ApiException()
        .withCode(code)
        .withMessage(message)
        .withHelpURL(helpURL)
        .withData(data);
  }
}
