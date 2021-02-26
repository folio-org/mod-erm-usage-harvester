package org.olf.erm.usage.harvester.bundle;

import io.reactivex.Single;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class RxJavaTest {

  @Test
  public void testIgnoreElementMethodAvailable(TestContext context) {
    Async async = context.async();
    Single.just("").ignoreElement().subscribe(async::complete, context::fail);
  }
}
