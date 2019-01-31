package org.folio.rest.impl;

import org.folio.rest.resource.interfaces.PostDeployVerticle;
import org.folio.rest.security.AES;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class PostDeployImpl implements PostDeployVerticle {

  @Override
  public void init(Vertx arg0, Context arg1, Handler<AsyncResult<Boolean>> arg2) {
    // set secret key here
    ErmUsageHarvesterAPI.setSecretKey(AES.getSecretKeyObject("z5+9stwTtOdOPwVvCONFig"));

    arg2.handle(Future.succeededFuture(true));
  }
}
