FROM folioci/alpine-jre-openjdk21:latest

# Install latest patch versions of packages: https://pythonspeed.com/articles/security-updates-in-docker/
USER root
RUN apk upgrade --no-cache
USER folio

ENV VERTICLE_FILE=mod-erm-usage-harvester-bundle-fat.jar
ENV VERTICLE_HOME=/usr/verticles

ENV OKAPI_URL=http://10.0.2.15:9130

# Copy your fat jar to the container
COPY mod-erm-usage-harvester-bundle/target/${VERTICLE_FILE} ${VERTICLE_HOME}/

# Expose this port locally in the container.
EXPOSE 8081
