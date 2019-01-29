FROM ubirch/java
ARG JAR_LIBS
ARG JAR_FILE
ARG VERSION
ARG BUILD
ARG SERVICE_NAME

LABEL "com.ubirch.service"="${SERVICE_NAME}"
LABEL "com.ubirch.version"="${VERSION}"
LABEL "com.ubirch.build"="${BUILD}"

EXPOSE 8080
EXPOSE 9010
EXPOSE 2552
EXPOSE 9020

ENV _JAVA_OPTIONS "-Xms128m -Xmx256m -Djava.awt.headless=true"

ENTRYPOINT [ \
  "/usr/bin/java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Djava.rmi.server.hostname=localhost", \
  "-Dcom.sun.management.jmxremote", \
  "-Dcom.sun.management.jmxremote.port=9010", \
  "-Dcom.sun.management.jmxremote.rmi.port=9010", \
  "-Dcom.sun.management.jmxremote.local.only=false", \
  "-Dcom.sun.management.jmxremote.authenticate=false", \
  "-Dcom.sun.management.jmxremote.ssl=false", \
  "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=9020", \
  "-jar", "/usr/share/service/main.jar" \
]

# Add Maven dependencies (not shaded into the artifact; Docker-cached)
COPY ${JAR_LIBS} /usr/share/service/lib
# Add the service itself
COPY ${JAR_FILE} /usr/share/service/main.jar