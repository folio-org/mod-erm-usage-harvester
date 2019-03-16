FROM folioci/openjdk8-jre:latest

ENV VERTICLE_FILE mod-erm-usage-harvester-bundle-fat.jar
ENV CONF_FILE config.json

ENV VERTICLE_HOME /usr/verticles

# Copy your fat jar to the container
COPY mod-erm-usage-harvester-bundle/target/${VERTICLE_FILE} ${VERTICLE_HOME}/${VERTICLE_FILE}
COPY mod-erm-usage-harvester-bundle/target/${CONF_FILE} ${VERTICLE_HOME}/${CONF_FILE}

# replace run-java.sh with proxy functions 
COPY docker/run-java.sh ${VERTICLE_HOME}/run-java.sh
# USER root
# RUN chmod 755 run-java.sh

# Expose this port locally in the container.
EXPOSE 8081
