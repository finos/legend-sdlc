// Copyright 2020 Goldman Sachs
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

package org.finos.legend.sdlc.domain.model;

import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.junit.Assert;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class TestTools
{
    private static final String ANY = "meta::pure::metamodel::type::Any";

    public static <T extends Comparable<? super T>> void assertCompareTo(int expected, T obj1, T obj2)
    {
        Assert.assertEquals(Integer.signum(expected), Integer.signum(obj1.compareTo(obj2)));
    }

    public static void assertEntitiesEquivalent(Entity expected, Entity actual)
    {
        assertEntitiesEquivalent(null, expected, actual);
    }

    public static void assertEntitiesEquivalent(String message, Entity expected, Entity actual)
    {
        if ((expected == null) || (actual == null))
        {
            Assert.assertEquals(message, expected, actual);
            return;
        }

        if (!Objects.equals(expected.getPath(), actual.getPath()) || !Objects.equals(expected.getClassifierPath(), actual.getClassifierPath()) || !Objects.equals(expected.getContent(), actual.getContent()))
        {
            // We know Assert.assertEquals will fail; we call it to generate a good message
            Assert.assertEquals(message, expected, actual);
        }
    }

    public static void assertEntitiesEquivalent(Iterable<Entity> expectedEntities, Iterable<Entity> actualEntities)
    {
        assertEntitiesEquivalent(null, expectedEntities, actualEntities);
    }

    public static void assertEntitiesEquivalent(String message, Iterable<Entity> expectedEntities, Iterable<Entity> actualEntities)
    {
        SortedMap<String, Entity> expectedByPath = new TreeMap<>();
        for (Entity expectedEntity : expectedEntities)
        {
            Entity old = expectedByPath.put(expectedEntity.getPath(), expectedEntity);
            if (old != null)
            {
                throw new IllegalArgumentException("expected entities contain multiple entities with path: " + expectedEntity.getPath());
            }
        }

        SortedSet<String> notFound = new TreeSet<>();
        SortedSet<String> notEqual = new TreeSet<>();
        for (Entity actualEntity : actualEntities)
        {
            Entity expectedEntity = expectedByPath.remove(actualEntity.getPath());
            if (expectedEntity == null)
            {
                notFound.add(actualEntity.getPath());
            }
            else if (!Objects.equals(expectedEntity.getClassifierPath(), actualEntity.getClassifierPath()) ||
                    !Objects.equals(expectedEntity.getContent(), actualEntity.getContent()))
            {
                notEqual.add(actualEntity.getPath());
            }
        }

        if (!notFound.isEmpty() || !notEqual.isEmpty() || !expectedByPath.isEmpty())
        {
            StringBuilder builder = new StringBuilder();
            if (message != null)
            {
                builder.append(message).append('\n');
            }
            if (!expectedByPath.isEmpty())
            {
                builder.append("Expected, not actual:");
                expectedByPath.keySet().forEach(path -> builder.append("\n\t").append(path));
                builder.append('\n');
            }
            if (!notFound.isEmpty())
            {
                builder.append("Actual, not expected:");
                notFound.forEach(path -> builder.append("\n\t").append(path));
                builder.append('\n');
            }
            if (!notEqual.isEmpty())
            {
                builder.append("Not equivalent:");
                notEqual.forEach(path -> builder.append("\n\t").append(path));
                builder.append('\n');
            }
            Assert.fail(builder.toString());
        }
    }

    public static Entity newClassEntity(String name, String pkg)
    {
        return newClassEntity(name, pkg, null, null);
    }

    public static Entity newClassEntity(String name, String pkg, Map<String, ?> property)
    {
        return newClassEntity(name, pkg, Collections.singletonList(property), null);
    }

    public static Entity newClassEntity(String name, String pkg, List<? extends Map<String, ?>> properties)
    {
        return newClassEntity(name, pkg, properties, null);
    }

    public static Entity newClassEntity(String name, String pkg, List<? extends Map<String, ?>> properties, List<String> superTypes)
    {
        Map<String, Object> content = new HashMap<>(5);
        content.put("_type", "class");
        content.put("name", name);
        content.put("package", pkg);
        content.put("properties", (properties == null) ? Collections.emptyList() : properties);
        content.put("superTypes", ((superTypes == null) || superTypes.isEmpty()) ? Collections.singletonList(ANY) : superTypes);
        return Entity.newEntity(
                pkg + "::" + name,
                "meta::pure::metamodel::type::Class",
                content
        );
    }

    public static Entity newEnumerationEntity(String name, String pkg, String... values)
    {
        Map<String, Object> content = new HashMap<>(4);
        content.put("_type", "Enumeration");
        content.put("name", name);
        content.put("package", pkg);
        content.put("values", (values == null) ? Collections.emptyList() : Arrays.stream(values).map(v -> Collections.singletonMap("value", v)).collect(Collectors.toList()));
        return Entity.newEntity(
                pkg + "::" + name,
                "meta::pure::metamodel::type::Enumeration",
                content
        );
    }

    public static Map<String, ?> newProperty(String name, String type, int minMult, int maxMult)
    {
        Map<String, Object> map = new HashMap<>(3);
        map.put("name", name);
        map.put("type", type);
        map.put("multiplicity", newMultiplicity(minMult, maxMult));
        return map;
    }

    private static Map<String, ?> newMultiplicity(int minMult, int maxMult)
    {
        Map<String, Object> map = new HashMap<>(2);
        if (minMult >= 0)
        {
            map.put("lowerBound", minMult);
        }
        if (maxMult >= 0)
        {
            map.put("upperBound", maxMult);
        }
        return map;
    }
}
