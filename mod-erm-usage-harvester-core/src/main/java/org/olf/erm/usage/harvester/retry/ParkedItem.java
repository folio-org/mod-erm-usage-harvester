package org.olf.erm.usage.harvester.retry;

import java.time.Instant;
import org.olf.erm.usage.harvester.FetchItem;

/**
 * An item that a run could not harvest yet and that is to be retried later, in a separate retry
 * run.
 *
 * @param item the fetch item to retry
 * @param retryCount the retry counter the item gets in the retry run
 * @param dueAt the earliest point in time the item may be retried
 * @param reason a short description of why the item is retried, for logs
 */
public record ParkedItem(FetchItem item, int retryCount, Instant dueAt, String reason) {}
