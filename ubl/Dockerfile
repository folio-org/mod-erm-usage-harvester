FROM image
ARG ID
ENTRYPOINT ["/usr/local/bin/docker-entrypoint"]

ENV START_CMD="./run-java.sh" \
  OKAPI_TRY_COUNT=120 \
  REGISTER_SERVICE_TRY_COUNT=120 \
  INTRODUCE_MODULE_TRY_COUNT=120 \
  CURL_CONNECT_TIMEOUT=3 \
  SERVICE_ID=${ID} \
  INSTALL_ID=${ID} \
  SERVICE_URL=http://localhost:8081/ \
  SELF_DEPLOY=false

COPY target/ModuleDescriptor.json module-descriptor.json

COPY backend-modules/assets/docker-entrypoint /usr/local/bin/
