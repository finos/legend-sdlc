FROM eclipse-temurin:11.0.17_8-jdk-jammy
COPY target/legend-sdlc-server-fs-*-shaded.jar /app/bin/
COPY src/main/resources/docker/config /config
CMD java -cp /app/bin/*.jar \
-XX:+ExitOnOutOfMemoryError \
-XX:MaxRAMPercentage=60 \
-Xss4M \
-Dfile.encoding=UTF8 \
org.finos.legend.sdlc.server.startup.LegendSDLCServerFS server /config/config.json