# mod-erm-usage-harvester

Copyright (C) 2018 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.


# Installation

```
git clone ...
cd mod-erm-usage-harvester
mvn clean install
```

# Run plain jar

```
cd mod-erm-usage-harvester-core
java -jar target/mod-erm-usage-harvester-core-fat.jar -conf target/config.json
```

configuration via JSON file:
```json
{
  "okapiUrl": "http://localhost:9130",
  "tenantsPath": "/_/proxy/tenants",
  "reportsPath": "/counter-reports",
  "providerPath": "/usage-data-providers",
  "aggregatorPath": "/aggregator-settings",
  "moduleId": "mod-erm-usage-harvester-0.1.0"
}
```


# Run via Docker

### Build docker image

```
$ docker build -t mod-erm-usage-harvester .
$ docker run -t -i -p 8081:8081 -e DB_USERNAME=folio_admin -e DB_PASSWORD=folio_admin -e DB_HOST=172.17.0.1 -e DB_PORT=5432 -e DB_DATABASE=okapi_modules mod-erm-usage-harvester
```

### Change config file

```
$ cd mod-erm-usage-harvester-core/target
```

Change _okapiUrl_ in file _config.json_ to your docker hosts IP address, e.g. `"okapiUrl": "http://172.17.0.1:9130"`.


### Register ModuleDescriptor

```
$ curl -w '\n' -X POST -D - -H "Content-type: application/json" -d @ModuleDescriptor.json http://localhost:9130/_/proxy/modules
```

### Activate module for tenant (do this before registering DeploymentDescriptor)

```
$ curl -w '\n' -X POST -D - -H "Content-type: application/json" -d '{ "id": "mod-erm-usage-harvester-0.1.0"}' http://localhost:9130/_/proxy/tenants/diku/modules
```

### Register DeploymentDescriptor

Change _nodeId_ in _DockerDeploymentDescriptor.json_ to e.g. your hosts IP address (e.g. 10.0.2.15). Then execute:

```
$ curl -w '\n' -X POST -D - -H "Content-type: application/json" -d @DockerDeploymentDescriptor.json http://localhost:9130/_/discovery/modules
```

## Additional information

### Issue tracker

See project [MODERM](https://issues.folio.org/browse/MODERM)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker).

### Other documentation

Other [modules](https://dev.folio.org/source-code/#server-side) are described,
with further FOLIO Developer documentation at [dev.folio.org](https://dev.folio.org/)

