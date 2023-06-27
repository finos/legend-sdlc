// Copyright 2023 Goldman Sachs
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

package org.finos.legend.sdlc.server.backend.simple.canned;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.engine.protocol.pure.v1.PureProtocolObjectMapperFactory;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.server.backend.simple.domain.model.entity.SimpleBackendEntity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

public class StaticEntities
{
    public static String load(String resourceName) throws Exception
    {
        InputStream resourceAsStream = null;
        try
        {
            String fullName = "/backend/simple/canned/" + resourceName;
            resourceAsStream = StaticEntities.class.getResourceAsStream(fullName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(resourceAsStream));
            String contents = reader.lines().collect(Collectors.joining(System.lineSeparator()));
            return contents;
        }
        finally
        {
            if (resourceAsStream != null)
            {
                resourceAsStream.close();
            }
        }
    }

    public static MutableMap<String, Entity> loadEntities(String resourceName) throws Exception
    {
        String rawJSON = StaticEntities.load(resourceName);
        ObjectMapper newObjectMapper = PureProtocolObjectMapperFactory.getNewObjectMapper();
        TypeReference<List<SimpleBackendEntity>> typeReference = new TypeReference<List<SimpleBackendEntity>>()
        {
        };
        List<SimpleBackendEntity> simpleBackendEntities = newObjectMapper.readValue(rawJSON, typeReference);

        MutableMap<String, Entity> simpleBackendEntitiesMap = Maps.mutable.empty();
        simpleBackendEntities.forEach(entity -> simpleBackendEntitiesMap.put(entity.getPath(), entity));

        return simpleBackendEntitiesMap;
    }

    public static void main(String[] args) throws Exception
    {
        StaticEntities.loadEntities("tour.json");
    }
}
