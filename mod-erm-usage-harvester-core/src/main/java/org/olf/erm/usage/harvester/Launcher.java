package org.olf.erm.usage.harvester;

import com.google.common.base.Strings;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import org.folio.rest.RestLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Launcher extends RestLauncher {

  private static final Logger LOG = LoggerFactory.getLogger(Launcher.class);

  public static void main(String[] args) {
    new Launcher().dispatch(args);
  }

  @Override
  public void beforeDeployingVerticle(DeploymentOptions deploymentOptions) {
    super.beforeDeployingVerticle(deploymentOptions);

    if (deploymentOptions.getConfig() == null) {
      deploymentOptions.setConfig(new JsonObject());
    }

    String envOkapi = System.getenv("OKAPI_URL");
    if (!Strings.isNullOrEmpty(envOkapi)) {
      deploymentOptions.getConfig().put("okapiUrl", envOkapi);
    }

    if (Strings.isNullOrEmpty(deploymentOptions.getConfig().getString("okapiUrl"))) {
      LOG.error("Environment variable 'OKAPI_URL' not set");
      System.exit(1);
    }

    // override port from command line
    getProcessArguments().stream()
        .filter(arg -> arg.startsWith("-Dhttp.port="))
        .findFirst()
        .ifPresent(
            line -> {
              try {
                int parseInt = Integer.parseInt(line.split("=")[1]);
                deploymentOptions.getConfig().put("http.port", parseInt);
              } catch (Exception e) {
                // ignore
              }
            });

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
      LOG.error(e.getMessage(), e);
    }
  }
}
