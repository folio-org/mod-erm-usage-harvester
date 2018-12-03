FROM folioci/openjdk8-jre:latest

ENV VERTICLE_FILE mod-erm-usage-harvester-core-fat.jar
ENV CONF_FILE config.json

ENV VERTICLE_HOME /usr/verticles

# Copy your fat jar to the container
COPY mod-erm-usage-harvester-core/target/${VERTICLE_FILE} ${VERTICLE_HOME}/${VERTICLE_FILE}
COPY mod-erm-usage-harvester-core/target/${CONF_FILE} ${VERTICLE_HOME}/${CONF_FILE}

# Expose this port locally in the container.
EXPOSE 8081
