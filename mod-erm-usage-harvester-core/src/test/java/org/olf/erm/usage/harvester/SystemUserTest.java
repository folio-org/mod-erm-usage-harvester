package org.olf.erm.usage.harvester;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.json.JsonObject;
import org.junit.Rule;
import org.junit.Test;
import uk.org.webcompere.systemstubs.rules.EnvironmentVariablesRule;

public class SystemUserTest {

  @Rule public EnvironmentVariablesRule envs = new EnvironmentVariablesRule();

  @Test
  public void testToJsonObject() {
    final String tenantId = "tenanta";
    SystemUser su1 = new SystemUser(tenantId);
    assertThat(su1.toJsonObject())
        .isEqualTo(new JsonObject().put("username", null).put("password", null));

    envs.set("TENANTA_USER_NAME", "user");
    envs.set("TENANTA_USER_PASS", "pass");
    SystemUser su2 = new SystemUser(tenantId);
    assertThat(su2.toJsonObject())
        .isEqualTo(new JsonObject().put("username", "user").put("password", "pass"));

    SystemUser su3 = new SystemUser("user2", "pass2");
    assertThat(su3.toJsonObject())
        .isEqualTo(new JsonObject().put("username", "user2").put("password", "pass2"));
  }
}
