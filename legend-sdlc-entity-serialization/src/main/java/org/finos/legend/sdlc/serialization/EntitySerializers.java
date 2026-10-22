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

package org.finos.legend.sdlc.serialization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Access to the registered {@link EntitySerializer}s. The no-argument lookup methods use the thread context
 * classloader (the {@link ServiceLoader} convention, which Maven plugin executions rely on); code that must not
 * depend on ambient thread state — anything reachable from a shared-JVM embedding (re-architecture section 4.5) —
 * should use the overloads taking an explicit {@link ClassLoader}.
 */
public class EntitySerializers
{
    private EntitySerializers()
    {
    }

    public static EntityTextSerializer getDefaultJsonSerializer()
    {
        return new DefaultJsonEntitySerializer();
    }

    public static Iterable<EntitySerializer> getAvailableSerializers()
    {
        return getAvailableSerializers(Thread.currentThread().getContextClassLoader());
    }

    public static Iterable<EntitySerializer> getAvailableSerializers(ClassLoader classLoader)
    {
        List<EntitySerializer> serializers = new ArrayList<>();
        ServiceLoader.load(EntitySerializer.class, classLoader).forEach(serializers::add);
        return serializers;
    }

    public static Iterable<EntityTextSerializer> getAvailableTextSerializers()
    {
        return getAvailableTextSerializers(Thread.currentThread().getContextClassLoader());
    }

    public static Iterable<EntityTextSerializer> getAvailableTextSerializers(ClassLoader classLoader)
    {
        List<EntityTextSerializer> serializers = new ArrayList<>();
        ServiceLoader.load(EntitySerializer.class, classLoader).forEach(s ->
        {
            if (s instanceof EntityTextSerializer)
            {
                serializers.add((EntityTextSerializer) s);
            }
        });
        return serializers;
    }

    public static Map<String, EntitySerializer> getAvailableSerializersByName()
    {
        return getAvailableSerializersByName(Thread.currentThread().getContextClassLoader());
    }

    public static Map<String, EntitySerializer> getAvailableSerializersByName(ClassLoader classLoader)
    {
        Map<String, EntitySerializer> result = new HashMap<>();
        ServiceLoader.load(EntitySerializer.class, classLoader).forEach(s ->
        {
            String name = s.getName();
            EntitySerializer old = result.put(name, s);
            if (old != null)
            {
                throw new IllegalArgumentException("Multiple serializers named \"" + name + "\"");
            }
        });
        return result;
    }

    public static Map<String, EntityTextSerializer> getAvailableTextSerializersByName()
    {
        return getAvailableTextSerializersByName(Thread.currentThread().getContextClassLoader());
    }

    public static Map<String, EntityTextSerializer> getAvailableTextSerializersByName(ClassLoader classLoader)
    {
        Map<String, EntityTextSerializer> result = new HashMap<>();
        ServiceLoader.load(EntitySerializer.class, classLoader).forEach(s ->
        {
            if (s instanceof EntityTextSerializer)
            {
                String name = s.getName();
                EntitySerializer old = result.put(name, (EntityTextSerializer) s);
                if (old != null)
                {
                    throw new IllegalArgumentException("Multiple serializers named \"" + name + "\"");
                }
            }
        });
        return result;
    }
}
