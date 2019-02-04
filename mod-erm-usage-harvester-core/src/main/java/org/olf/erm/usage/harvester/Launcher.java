package org.olf.erm.usage.harvester;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import org.apache.commons.io.FileUtils;
import com.google.common.base.Strings;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

public class Launcher extends io.vertx.core.Launcher {

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
        System.err.println("Error decoding JSON configuration from default config.json");
        System.err.println(e.getMessage());
      } catch (FileNotFoundException e) {
        // ignore
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    // override with environment variable
    String envConfig = System.getenv("CONFIG");
    if (envConfig != null) {
      try {
        deploymentOptions.setConfig(
            deploymentOptions.getConfig().mergeIn(new JsonObject(envConfig)));
      } catch (DecodeException e) {
        System.err.println("Error decoding JSON configuration from environment variable 'CONFIG'");
        System.err.println(e.getMessage());
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    // override port from command line
    getProcessArguments()
        .stream()
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
          "okapiUrl",
          "tenantsPath",
          "reportsPath",
          "providerPath",
          "aggregatorPath",
          "moduleIds",
          "loginPath",
          "requiredPerm"
        };
    System.out.println("Using configuration:\n" + deploymentOptions.getConfig().encodePrettily());

    boolean exit = false;
    for (String param : configParams) {
      Object o = deploymentOptions.getConfig().getValue(param);
      if ((o instanceof String && Strings.isNullOrEmpty((String) o)) || o == null) {
        System.err.println("Parameter '" + param + "' missing in configuration.");
        exit = true;
      }
    }
    if (exit) {
      System.exit(1);
    }
  }
}
