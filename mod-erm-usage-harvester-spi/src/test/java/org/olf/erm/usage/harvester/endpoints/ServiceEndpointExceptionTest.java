package org.olf.erm.usage.harvester.endpoints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingConstants.MAX_ERROR_BODY_LENGTH;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

class ServiceEndpointExceptionTest {

  @Test
  void testHttpErrorConstructor() {
    // Test with normal values
    String responseBody = "Error details from provider";
    ServiceEndpointException exception =
        new ServiceEndpointException(404, "Not Found", responseBody);

    assertThat(exception.getMessage())
        .isEqualTo("HTTP 404: Not Found - Error details from provider");
    assertThat(exception.getStatusCode()).isEqualTo(404);
    assertThat(exception.getResponseBody()).isEqualTo(responseBody);

    // Test with null response body
    exception = new ServiceEndpointException(500, "Internal Server Error", null);
    assertThat(exception.getMessage()).isEqualTo("HTTP 500: Internal Server Error - [no body]");
    assertThat(exception.getStatusCode()).isEqualTo(500);
    assertThat(exception.getResponseBody()).isEqualTo("[no body]");

    // Test with empty response body
    exception = new ServiceEndpointException(400, "Bad Request", "");
    assertThat(exception.getMessage()).isEqualTo("HTTP 400: Bad Request - [no body]");
    assertThat(exception.getStatusCode()).isEqualTo(400);
    assertThat(exception.getResponseBody()).isEqualTo("[no body]");
  }

  @Test
  void testResponseBodyConstructor() {
    String responseBody = "Error message from response body";
    ServiceEndpointException exception = new ServiceEndpointException(responseBody);

    assertThat(exception.getMessage()).isEqualTo(responseBody);
    assertThat(exception.getStatusCode()).isNull();
    assertThat(exception.getResponseBody()).isEqualTo(responseBody);
  }

  @Test
  void testLongResponseBodyAbbreviation() {
    StringBuilder longBody = new StringBuilder();
    for (int i = 0; i < MAX_ERROR_BODY_LENGTH + 100; i++) {
      longBody.append("X");
    }
    String expectedAbbreviatedBody =
        StringUtils.abbreviate(longBody.toString(), MAX_ERROR_BODY_LENGTH);

    // Test HTTP error constructor with long body
    ServiceEndpointException httpException =
        new ServiceEndpointException(403, "Forbidden", longBody.toString());

    assertThat(httpException.getMessage())
        .isEqualTo("HTTP 403: Forbidden - " + expectedAbbreviatedBody);
    assertThat(httpException.getStatusCode()).isEqualTo(403);
    assertThat(httpException.getResponseBody()).isEqualTo(longBody.toString());

    // Test response body constructor with long body
    ServiceEndpointException bodyException = new ServiceEndpointException(longBody.toString());

    assertThat(bodyException.getMessage()).isEqualTo(expectedAbbreviatedBody);
    assertThat(bodyException.getStatusCode()).isNull();
    assertThat(bodyException.getResponseBody()).isEqualTo(longBody.toString());
  }
}
