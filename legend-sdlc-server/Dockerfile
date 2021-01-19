FROM openjdk:11
COPY target/legend-sdlc-server-*-shaded.jar /app/bin/
CMD java -Xmx2G -Xms256M -Xss4M -cp /app/bin/*.jar -Dfile.encoding=UTF8 org.finos.legend.sdlc.server.LegendSDLCServer server /config/config.json
