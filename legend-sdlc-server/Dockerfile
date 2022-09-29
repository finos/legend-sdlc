FROM openjdk:11.0.16-bullseye
COPY target/legend-sdlc-server-*-shaded.jar /app/bin/
COPY src/main/resources/docker/config /config
CMD java -cp /app/bin/*.jar \
-XX:+ExitOnOutOfMemoryError \
-XX:MaxRAMPercentage=60 \
-Xss4M \
-Dfile.encoding=UTF8 \
org.finos.legend.sdlc.server.LegendSDLCServer server /config/config.json
