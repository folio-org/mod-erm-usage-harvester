package org.olf.erm.usage.harvester;

import java.util.function.Supplier;
import org.slf4j.helpers.MessageFormatter;

public class Messages {

  static final String ERR_MSG_STATUS = "Received status code %s, %s from %s";
  static final String ERR_MSG_DECODE = "Error decoding response from %s, %s";
  static final String MSG_RESPONSE_BODY_IS_NULL = "Response body is null";

  public static String createMsgStatus(int statusCode, String statusMessage, String url) {
    return String.format(ERR_MSG_STATUS, statusCode, statusMessage, url);
  }

  public static String createErrMsgDecode(String url, String message) {
    return String.format(ERR_MSG_DECODE, url, message);
  }

  public static String createTenantMsg(String tenant, String pattern, Object... args) {
    return String.format("Tenant: %s, %s", tenant, format(pattern, args));
  }

  public static String createProviderMsg(String provider, String pattern, Object... args) {
    return String.format("Provider: %s, %s", provider, format(pattern, args));
  }

  public static Supplier<String> createTenantProviderMsg(
      String tenant, String provider, String pattern, Object... args) {
    return () ->
        String.format("Tenant: %s, Provider: %s, %s", tenant, provider, format(pattern, args));
  }

  public static String format(String pattern, Object... args) {
    return MessageFormatter.arrayFormat(pattern, args).getMessage();
  }

  private Messages() {}
}
