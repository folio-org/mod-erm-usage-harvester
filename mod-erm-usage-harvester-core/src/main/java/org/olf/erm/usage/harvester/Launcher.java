package org.olf.erm.usage.harvester;

import com.google.common.base.Strings;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import java.io.File;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Launcher extends io.vertx.core.Launcher {

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

    // try default config.json if empty
    if (deploymentOptions.getConfig().isEmpty()) {
      try {
        String config =
            FileUtils.readFileToString(new File("config.json"), Charset.defaultCharset());
        deploymentOptions.setConfig(new JsonObject(config));
      } catch (DecodeException e) {
        LOG.error("Error decoding JSON configuration from default config.json: {}", e.getMessage());
      } catch (IOException e) {
        // ignore
      }
    }

    // override with environment variables
    String envConfig = System.getenv("CONFIG");
    if (envConfig != null) {
      try {
        deploymentOptions.setConfig(
            deploymentOptions.getConfig().mergeIn(new JsonObject(envConfig)));
      } catch (DecodeException e) {
        LOG.error(
            "Error decoding JSON configuration from environment variable 'CONFIG': {}",
            e.getMessage());
      } catch (Exception e) {
        LOG.error("Error processing environment variable 'CONFIG': {}", e.getMessage(), e);
      }
    }
    String envOkapi = System.getenv("OKAPI_URL");
    if (!Strings.isNullOrEmpty(envOkapi)) {
      try {
        deploymentOptions.setConfig(
            deploymentOptions.getConfig().mergeIn(new JsonObject().put("okapiUrl", envOkapi)));
      } catch (DecodeException e) {
        LOG.error(
            "Error decoding JSON configuration from environment variable 'OKAPI_URL': {}",
            e.getMessage());
      } catch (Exception e) {
        LOG.error("Error processing environment variable 'OKAPI_URL': {}", e.getMessage(), e);
      }
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

    // check configuration and abort if something is missing
    String[] configParams =
        new String[] {
          "okapiUrl", "tenantsPath", "reportsPath", "providerPath", "aggregatorPath", "moduleIds"
        };
    LOG.info("Using configuration:\n{}", deploymentOptions.getConfig().encodePrettily());

    try {
      ProxySelector.getDefault().select(new URI("http://google.com")).stream()
          .findFirst()
          .ifPresent(p -> LOG.info("HTTP Proxy found: {}", p.address()));
      ProxySelector.getDefault().select(new URI("https://google.com")).stream()
          .findFirst()
          .ifPresent(p -> LOG.info("HTTPS Proxy found: {}", p.address()));
    } catch (URISyntaxException e) {
      LOG.error(e.getMessage(), e);
    }

    boolean exit = false;
    for (String param : configParams) {
      Object o = deploymentOptions.getConfig().getValue(param);
      if ((o instanceof String && Strings.isNullOrEmpty((String) o)) || o == null) {
        LOG.error("Parameter '{}' missing in configuration.", param);
        exit = true;
      }
    }
    if (exit) {
      System.exit(1);
    }
  }
}
