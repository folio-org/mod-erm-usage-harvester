package org.olf.erm.usage.harvester;

public class ExceptionUtil {

  private ExceptionUtil() {}

  public static String getMessageOrToString(Throwable t) {
    return (t.getMessage() != null) ? t.getMessage() : t.toString();
  }
}
