package org.olf.erm.usage.harvester.endpoints;

import io.vertx.core.Future;
import java.util.Collections;
import java.util.List;
import org.folio.rest.jaxrs.model.CounterReport;

public class TestProviderImpl implements ServiceEndpoint {

  @Override
  public Future<List<CounterReport>> fetchReport(String report, String beginDate, String endDate) {
    return Future.succeededFuture(Collections.emptyList());
  }
}
