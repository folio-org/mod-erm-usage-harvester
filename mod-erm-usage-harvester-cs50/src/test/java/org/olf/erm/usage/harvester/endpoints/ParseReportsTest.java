package org.olf.erm.usage.harvester.endpoints;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.olf.erm.usage.counter50.Counter5Utils;
import org.olf.erm.usage.counter50.Counter5Utils.Counter5UtilsException;

@RunWith(Parameterized.class)
public class ParseReportsTest {

  private final String resourceName;
  private final Class<?> clazz;

  @Parameters(name = "{0}")
  public static Iterable<String[]> reports() {
    return List.of(
        new String[] {"DRWithItemId.json", "COUNTERDatabaseReport"},
        new String[] {"DRWithoutItemId.json", "COUNTERDatabaseReport"},
        new String[] {"IRWithoutParentDetails.json", "COUNTERItemReport"},
        new String[] {"IRWithParentDetails.json", "COUNTERItemReport"},
        new String[] {"PR.json", "COUNTERPlatformReport"},
        new String[] {"TR.json", "COUNTERTitleReport"},
        new String[] {"dr_with_empty_months.json", "COUNTERDatabaseReport"});
  }

  public ParseReportsTest(String resourceName, String className) throws ClassNotFoundException {
    this.resourceName = "reports/" + resourceName;
    this.clazz = Class.forName("org.openapitools.counter50.model." + className);
  }

  @Test
  public void testParseReport() throws IOException, Counter5UtilsException {
    String str = Resources.toString(Resources.getResource(resourceName), StandardCharsets.UTF_8);
    assertThat(Counter5Utils.fromJSON(str)).isInstanceOf(clazz);
    assertThat(JsonUtil.isOfType(str, clazz)).isTrue();
  }
}
