package org.olf.erm.usage.harvester.worker;

import org.folio.rest.jaxrs.model.CounterReport;
import org.olf.erm.usage.harvester.Messages;

record LoggerContext(String tenantId, String usageDataProviderLabel) {

  /**
   * Context aware message formatting, adding the {@code tenantId} and the {@code
   * usageDataProviderLabel} to the log messages.
   */
  String createMsg(final String pattern, final Object... args) {
    return Messages.createTenantProviderMsg(tenantId, usageDataProviderLabel, pattern, args);
  }

  /**
   * @param cr the counter report
   * @return the string representation of the 'counter report'
   */
  static String counterReportToString(final CounterReport cr) {
    return cr.getReportName() + " " + cr.getYearMonth();
  }
}
