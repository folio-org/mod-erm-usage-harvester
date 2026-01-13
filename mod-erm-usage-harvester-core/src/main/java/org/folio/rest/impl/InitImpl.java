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
    // Read okapiUrl from OKAPI_URL environment variable
    String okapiUrl = System.getenv("OKAPI_URL");
    if (Strings.isNullOrEmpty(okapiUrl)) {
      LOG.info("Environment variable 'OKAPI_URL' not set");
    } else {
      LOG.info("Setting okapiUrl from environment variable OKAPI_URL: {}", okapiUrl);
      context.config().put("okapiUrl", okapiUrl);
    }

    // Validate that okapiUrl is configured
    okapiUrl = context.config().getString("okapiUrl");
    if (Strings.isNullOrEmpty(okapiUrl)) {
      return false;
    }

    LOG.info("Using okapiUrl: {}", okapiUrl);
    return true;
  }

  private void logProxyConfiguration() {
    try {
      ProxySelector.getDefault().select(new URI("http://google.com")).stream()
          .filter(p -> p.address() != null)
          .findFirst()
          .ifPresent(p -> LOG.info("HTTP Proxy found: {}", p.address()));

      ProxySelector.getDefault().select(new URI("https://google.com")).stream()
          .filter(p -> p.address() != null)
          .findFirst()
          .ifPresent(p -> LOG.info("HTTPS Proxy found: {}", p.address()));
    } catch (URISyntaxException e) {
      LOG.error("Error checking proxy configuration: {}", e.getMessage(), e);
    }
  }
}
