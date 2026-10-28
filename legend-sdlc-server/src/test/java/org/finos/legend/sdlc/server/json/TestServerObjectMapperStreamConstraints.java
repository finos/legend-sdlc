// Copyright 2026 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.sdlc.server.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.dropwizard.core.setup.Bootstrap;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.server.BaseLegendSDLCServer;
import org.finos.legend.sdlc.server.LegendSDLCServer;
import org.finos.legend.sdlc.server.config.LegendSDLCServerConfiguration;
import org.finos.legend.sdlc.server.depot.DepotConfiguration;
import org.finos.legend.sdlc.server.depot.api.DepotMetadataApi;
import org.finos.legend.sdlc.server.depot.model.DepotProjectId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestServerObjectMapperStreamConstraints
{
    // two JSON levels per step: about 1200, past Jackson's default limit of 1000
    private static final int STEPS = 600;

    @Test
    public void theJerseyMapperReadsAndWritesEntitiesDeeperThanJacksonDefault() throws Exception
    {
        LegendSDLCServer server = new LegendSDLCServer(BaseLegendSDLCServer.GITLAB_MODE);
        Bootstrap<LegendSDLCServerConfiguration> bootstrap = new Bootstrap<>(server);
        server.initialize(bootstrap);

        assertRoundTrips(bootstrap.getObjectMapper());
    }

    @Test
    public void theDepotApiReadsEntitiesDeeperThanJacksonDefault() throws Exception
    {
        // built by hand: a default mapper would refuse to write it
        StringBuilder json = new StringBuilder("[{\"path\":\"test::deep\",\"classifierPath\":\"meta::pure::metamodel::function::ConcreteFunctionDefinition\",\"content\":{\"_type\":\"function\",\"name\":\"deep\",\"package\":\"test\",\"body\":");
        for (int i = 0; i < 2 * STEPS; i++)
        {
            json.append('[');
        }
        for (int i = 0; i < 2 * STEPS; i++)
        {
            json.append(']');
        }
        json.append("}}]");
        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);

        HttpServer depot = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        depot.createContext("/api/projects/org.test/deep/versions/1.0.0", exchange ->
        {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody())
            {
                out.write(body);
            }
        });
        depot.start();
        try
        {
            DepotConfiguration configuration = DepotConfiguration.newConfiguration(
                    DepotConfiguration.ServerConfiguration.newServerConfiguration("http", depot.getAddress().getHostString(), depot.getAddress().getPort()),
                    builder -> builder);
            List<Entity> entities = new DepotMetadataApi(configuration).getEntities(DepotProjectId.newDepotProjectId("org.test", "deep"), "1.0.0");
            Assertions.assertEquals(1, entities.size());
            Assertions.assertEquals("test::deep", entities.get(0).getPath());
        }
        finally
        {
            depot.stop(0);
        }
    }

    private static void assertRoundTrips(ObjectMapper mapper) throws Exception
    {
        Map<String, Object> entity = nestedEntityAsMap();
        String json = mapper.writeValueAsString(entity);
        Assertions.assertEquals(entity, mapper.readValue(json, new TypeReference<Map<String, Object>>() {}));
    }

    private static Map<String, Object> nestedEntityAsMap()
    {
        Map<String, Object> inner = Collections.singletonMap("value", 1);
        for (int i = 0; i < STEPS; i++)
        {
            inner = Collections.singletonMap("parameters", Collections.singletonList(inner));
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("_type", "function");
        content.put("name", "deep");
        content.put("package", "test");
        content.put("body", inner);
        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("path", "test::deep");
        entity.put("classifierPath", "meta::pure::metamodel::function::ConcreteFunctionDefinition");
        entity.put("content", content);
        return entity;
    }
}
