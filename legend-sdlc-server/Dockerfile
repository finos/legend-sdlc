FROM openjdk:11.0.11
COPY target/legend-sdlc-server-*-shaded.jar /app/bin/
CMD java -XX:+ExitOnOutOfMemoryError -XX:MaxRAMPercentage=60 -Xss4M -cp /app/bin/*.jar -Dfile.encoding=UTF8 org.finos.legend.sdlc.server.LegendSDLCServer server /config/config.json
