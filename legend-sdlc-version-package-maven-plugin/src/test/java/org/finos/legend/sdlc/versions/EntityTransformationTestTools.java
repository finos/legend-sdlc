// Copyright 2021 Goldman Sachs
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

package org.finos.legend.sdlc.versions;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.junit.Assert;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

class EntityTransformationTestTools
{
    static List<Entity> transformEntities(List<Entity> entities, Function<? super String, ? extends String> pathTransform)
    {
        return entities.stream().map(e -> transformEntity(e, pathTransform)).collect(Collectors.toList());
    }

    static Entity transformEntity(Entity entity, Function<? super String, ? extends String> pathTransform)
    {
        String newPath = pathTransform.apply(entity.getPath());
        int lastColon = newPath.lastIndexOf(':');
        Map<String, Object> newContent = new HashMap<>(entity.getContent().size());
        entity.getContent().forEach((k, v) -> newContent.put(k, transformValue(v, pathTransform)));
        newContent.put("package", (lastColon == -1) ? "::" : newPath.substring(0, lastColon - 1));
        newContent.put("name", (lastColon == -1) ? newPath : newPath.substring(lastColon + 1));
        return Entity.newEntity(newPath, entity.getClassifierPath(), newContent);
    }

    private static Object transformValue(Object value, Function<? super String, ? extends String> pathTransform)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof String)
        {
            String string = (String) value;
            return (!string.startsWith("meta::") && string.matches("^(\\w[\\w$]*::)+\\w[\\w$]*$")) ? pathTransform.apply(string) : string;
        }
        if (value instanceof List)
        {
            return ((List<?>) value).stream().map(v -> transformValue(v, pathTransform)).collect(Collectors.toList());
        }
        if (value instanceof Map)
        {
            Map<?, ?> map = (Map<?, ?>) value;
            Map<Object, Object> newMap = new HashMap<>();
            map.forEach((k, v) -> newMap.put(k, transformValue(v, pathTransform)));
            if (map.containsKey("package") && map.containsKey("name"))
            {
                String oldPath = map.get("package") + "::" + map.get("name");
                String newPath = pathTransform.apply(oldPath);
                int lastColon = newPath.lastIndexOf(':');
                newMap.put("package", (lastColon == -1) ? "::" : newPath.substring(0, lastColon - 1));
                newMap.put("name", newPath.substring(lastColon + 1));
            }
            return newMap;
        }
        return value;
    }

    static void assertEntitiesEquivalent(List<? extends Entity> expected, List<? extends Entity> actual)
    {
        ObjectMapper jsonMapper = new ObjectMapper()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.INDENT_OUTPUT, true)
                .configure(SerializationFeature.CLOSE_CLOSEABLE, false);

        List<Entity> sortedExpected = new ArrayList<>(expected);
        sortedExpected.sort(Comparator.comparing(Entity::getPath));
        String expectedJson;
        try
        {
            expectedJson = jsonMapper.writeValueAsString(sortedExpected);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error generating expected entities JSON", e);
        }

        List<Entity> sortedActual = new ArrayList<>(actual);
        sortedActual.sort(Comparator.comparing(Entity::getPath));
        String actualJson;
        try
        {
            actualJson = jsonMapper.writeValueAsString(sortedActual);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error generating actual entities JSON", e);
        }

        Assert.assertEquals(expectedJson, actualJson);
    }

    static Path getResource(ClassLoader classLoader, String resourceName)
    {
        URL resource = classLoader.getResource(resourceName);
        if (resource == null)
        {
            throw new RuntimeException("Could not find resource: " + resourceName);
        }
        try
        {
            return Paths.get(resource.toURI());
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException(e);
        }
    }
}
