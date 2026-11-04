package org.olf.erm.usage.harvester.errorhandling;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.Resources;
import io.vertx.core.json.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.folio.rest.jaxrs.model.ErrorHandling;
import org.junit.Test;

public class ErrorHandlingDefaultsTest {

  @Test
  public void testEveryOptionHasADefault() {
    assertThat(ErrorHandlingDefaults.get()).hasNoNullFieldsOrPropertiesExcept("id");
  }

  @Test
  public void testGetReturnsNewInstance() {
    ErrorHandlingDefaults.get().getCode1000().setWaitMinutes(1);
    assertThat(ErrorHandlingDefaults.get().getCode1000().getWaitMinutes())
        .isEqualTo(ErrorHandlingDefaults.CODE_1000_WAIT_MINUTES);
  }

  @Test
  public void testRamlExampleMatchesDefaults() throws IOException {
    String example =
        Resources.toString(
            Resources.getResource("apidocs/raml/examples/errorHandlingDefaults.json"),
            StandardCharsets.UTF_8);

    assertThat(Json.decodeValue(example, ErrorHandling.class))
        .as("... examples/errorHandlingDefaults.json has to be updated with the defaults")
        .usingRecursiveComparison()
        .isEqualTo(ErrorHandlingDefaults.get());
  }
}
