package org.olf.erm.usage.harvester.errorhandling;

import io.vertx.core.Context;
import io.vertx.core.Future;

/** Loads the error handling settings a harvest run uses. */
@FunctionalInterface
public interface ErrorHandlingLoader {

  /**
   * @param context the Vert.x context to load the settings on
   * @return a future with the resolved settings
   */
  Future<ErrorHandlingSettings> load(Context context);

  /**
   * @param tenantId the tenant whose settings are loaded
   * @return a loader that reads the tenant's settings from the database
   */
  static ErrorHandlingLoader forTenant(final String tenantId) {
    return context -> ErrorHandlingPgUtil.get(context, tenantId).map(ErrorHandlingSettings::of);
  }
}
