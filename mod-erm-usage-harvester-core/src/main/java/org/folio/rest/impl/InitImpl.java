package org.folio.rest.impl;

import com.google.common.base.Strings;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import org.folio.rest.resource.interfaces.InitAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InitImpl implements InitAPI {

  private static final Logger LOG = LoggerFactory.getLogger(InitImpl.class);

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> resultHandler) {
    if (Boolean.TRUE.equals(context.config().getBoolean("testing"))) {
      LOG.info("Skipping InitImpl (testing==true)");
      resultHandler.handle(Future.succeededFuture(true));
      return;
    }

    if (!configureOkapiUrl(context)) {
      resultHandler.handle(Future.failedFuture("okapiUrl configuration is required"));
      return;
    }

    logProxyConfiguration();

    resultHandler.handle(Future.succeededFuture(true));
  }

  private boolean configureOkapiUrl(Context context) {
    String okapiUrl = System.getenv("OKAPI_URL");
    if (!Strings.isNullOrEmpty(okapiUrl)) {
      LOG.info("Setting okapiUrl from environment variable OKAPI_URL: {}", okapiUrl);
      context.config().put("okapiUrl", okapiUrl);
    } else {
      // OKAPI_URL not set, check if okapiUrl was configured via other means
      // (e.g. -conf parameter, DeploymentOptions, or vertx-config module)
      okapiUrl = context.config().getString("okapiUrl");
      if (Strings.isNullOrEmpty(okapiUrl)) {
        return false;
      }
    }

    LOG.info("Using okapiUrl: {}", okapiUrl);
    return true;
  }

  private void logProxyConfiguration() {
    logProxy("http://google.com", "HTTP");
    logProxy("https://google.com", "HTTPS");
  }

  private void logProxy(String uri, String protocol) {
    try {
      ProxySelector.getDefault().select(new URI(uri)).stream()
          .filter(p -> p.address() != null)
          .findFirst()
          .ifPresent(p -> LOG.info("{} Proxy found: {}", protocol, p.address()));
    } catch (URISyntaxException e) {
      LOG.error("Error checking {} proxy configuration: {}", protocol, e.getMessage(), e);
    }
  }
}
