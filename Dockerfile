FROM folioci/alpine-jre-openjdk11:latest

ENV VERTICLE_FILE=mod-erm-usage-harvester-bundle-fat.jar \
	CONF_FILE=config.json \
	VERTICLE_HOME=/usr/verticles

ENV JAVA_OPTIONS "-Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory"

# Copy your fat jar to the container
COPY mod-erm-usage-harvester-bundle/target/${VERTICLE_FILE} mod-erm-usage-harvester-bundle/target/${CONF_FILE} ${VERTICLE_HOME}/

# USER root
# RUN chmod 755 run-java.sh
# Expose this port locally in the container.
EXPOSE 8081
