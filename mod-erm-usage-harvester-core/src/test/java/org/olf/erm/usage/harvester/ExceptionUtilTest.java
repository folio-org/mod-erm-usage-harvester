package org.olf.erm.usage.harvester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.ExceptionUtil.getMessageOrToString;

import org.junit.Test;

public class ExceptionUtilTest {

  @Test
  public void testGetMessageOrToString() {
    Exception e1 = new Exception("test");
    Exception e2 = new Exception();
    assertThat(getMessageOrToString(e1)).isEqualTo("test");
    assertThat(getMessageOrToString(e2)).isEqualTo("java.lang.Exception");
  }
}
