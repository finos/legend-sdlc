FROM openjdk:11.0.12
COPY target/legend-sdlc-server-*-shaded.jar /app/bin/
CMD java -cp /app/bin/*.jar \
-XX:+ExitOnOutOfMemoryError \
-XX:MaxRAMPercentage=60 \
-Xss4M -Dfile.encoding=UTF8 \
org.finos.legend.sdlc.server.LegendSDLCServer server /config/config.json
