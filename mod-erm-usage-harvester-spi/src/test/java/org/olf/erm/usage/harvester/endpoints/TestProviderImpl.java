package org.olf.erm.usage.harvester.endpoints;

import io.vertx.core.Future;

public class TestProviderImpl implements ServiceEndpoint {

  @Override
  public boolean isValidReport(String report) {
    return false;
  }

  @Override
  public Future<String> fetchSingleReport(String report, String beginDate, String endDate) {
    return Future.succeededFuture("{ }");
  }
}
