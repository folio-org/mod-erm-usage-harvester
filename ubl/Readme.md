# UBL Deployment-Workflow

This document describes the deployment workflow of Leipzig University Library(UBL) for inhouse developed backend-modules for Folio.

## CI-Pipeline

This will be done by the GitLab-CI pipeline described in `.gitlab-ci.yml`

1. The JAR is build by job `openjdk_8`. It utilizes the official docker-image `maven:3.6.0-jdk-8`, sets up all requirements throughout the build-process and executes `mvn`.
1. A Docker-Image is created in job `build_image`
    1. The JAR is stuffed into a docker-image described by the default `Dockerfile` which creates an image equivalent to the official docker-images under *folioorg/*. _Be aware that this image is not pushed to the official registry. it is only used internally to base upon._
    1. Basing upon the image from above a new image is created which holds the `ModuleDescriptor.json` and a alternative `docker-entrypoint` in order to register to okapi on startup, see [backend-modules]
1. Depending on whether the push was on `master` or not the created image will be published by either `publish_alpha_image` or `publish_staging_image`.
    * Alpha-Images will be published to the Alpha-Repository with the Branch-Name as its Image-Tag.
    * Staging-Images will be published to the Staging-Repository with `staging` as the Image-Tag.
1. Depending on whether the push was on `master` or not the created image will be deployed by either `deploy_alpha` or `deploy_staging`.
    * `deploy_alpha` will take the Docker-Image built by `build_alpha_image` and deploy it to the namespace `folio-alpha`.
    * `deploy_staging` wil take the Docker-Image built by `build_staging_image` and deploy it to the namespace `folioe-staging`.

## Storage

In order to not pollute the batabase we provide a separate database to the module. This Database has no persistent storage either and will be overwritten if redeployed.

## Base-Components

Inhouse developed components require a basic Folio-Setup with latest Okapi and backend-modules required by but not developed UBL. This setup of components already exists in each namespace and is deployed by [helmchart]

* `folio-alpha` is the k8s-namepspace available for *preview* within development cicle. There is no persistent storage available for either okapi nor the backend-modules, so that redeploying these components will result in permanent data-loss.
* `folio-staging` is the k8s-namespace available for *review* within the development cicle. The database for okapi relys on persistent storage and will be reused after redeploying these components.
* `folio-demo` is the k8s-namespace available for presenting bleeding-edge development. The database for okapi relys on persistent storage and will be reused after redeploying these components.

[backend-modules]: https://git.sc.uni-leipzig.de/ubl/amsl/deployment/images/backend-modules
[helmchart]: https://git.sc.uni-leipzig.de/ubl/amsl/deployment/orchestration/folio-devops/