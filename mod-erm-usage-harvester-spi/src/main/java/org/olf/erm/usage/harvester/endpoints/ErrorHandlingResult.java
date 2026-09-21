package org.olf.erm.usage.harvester.endpoints;

import java.util.List;
import org.folio.rest.jaxrs.model.CounterReport;
import org.olf.erm.usage.harvester.FetchItem;

/** The different error handling results we can return, indicating how to react to them */
public sealed interface ErrorHandlingResult {

  /**
   * Retry to fetch the items, but throttling the concurrency
   *
   * @param items the items to retry
   * @param retryCount the retry counter for the items
   */
  record Throttle(List<FetchItem> items, int retryCount) implements ErrorHandlingResult {}

  /**
   * Retry to fetch the items
   *
   * @param items the items to retry
   */
  record RetryNow(List<FetchItem> items) implements ErrorHandlingResult {}

  /**
   * Contains the failed reports and also throttles the concurrency
   *
   * @param reports the failed reports
   */
  record GiveUpSlowDown(List<CounterReport> reports) implements ErrorHandlingResult {}

  /**
   * Contains the failed reports, nothing else
   *
   * @param reports the failed reports
   */
  record GiveUp(List<CounterReport> reports) implements ErrorHandlingResult {}
}
