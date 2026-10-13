package org.olf.erm.usage.harvester.endpoints;

import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.FetchItem;

/** An error handling strategy {@link ServiceEndpoint} consumers can use to handle exceptions. */
@FunctionalInterface
public interface ErrorHandlingStrategy {

  /**
   * Handles the error on report fetching.
   *
   * @param fetchItem the fetch item to handle the error for
   * @param retryCount the number of retries already performed
   * @param t the {@link Throwable} to take care of
   * @return the error handling result
   */
  ErrorHandlingResult handleFetchError(FetchItem fetchItem, int retryCount, Throwable t);

  /**
   * @return The current default error handling strategy. This will be removed as soon as {@link
   *     ServiceEndpoint} implementation specific error handling strategies are in place.
   */
  static ErrorHandlingStrategy create(UsageDataProvider provider) {
    return new DefaultErrorHandlingStrategy(provider);
  }
}
