package org.olf.erm.usage.harvester.endpoints.exceptions;

import org.folio.rest.jaxrs.model.ApiException;

@FunctionalInterface
public interface ApiExceptionProvider {

  /**
   * @return the serializable API exception
   */
  ApiException toApiException();
}
