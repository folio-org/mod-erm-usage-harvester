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
    try {
      // get default config
      String config = FileUtils.readFileToString(new File("config.json"), Charset.defaultCharset());
      JsonObject combined = new JsonObject(config).mergeIn(deploymentOptions.getConfig());
      deploymentOptions.setConfig(combined);
    } catch (DecodeException e) {
      System.err.println("Couldnt decode JSON configuration");
    } catch (FileNotFoundException e) {
      // ignore
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    // check configuration and abort if something is missing
    String[] configParams = new String[] {"okapiUrl", "tenantsPath", "reportsPath", "providerPath",
        "aggregatorPath", "moduleIds", "loginPath", "requiredPerm"};
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
