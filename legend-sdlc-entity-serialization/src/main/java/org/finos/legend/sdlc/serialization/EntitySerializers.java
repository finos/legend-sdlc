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
        List<EntitySerializer> serializers = new ArrayList<>();
        ServiceLoader.load(EntitySerializer.class).forEach(serializers::add);
        return serializers;
    }

    public static Iterable<EntityTextSerializer> getAvailableTextSerializers()
    {
        List<EntityTextSerializer> serializers = new ArrayList<>();
        ServiceLoader.load(EntitySerializer.class).forEach(s ->
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
        Map<String, EntitySerializer> result = new HashMap<>();
        ServiceLoader.load(EntitySerializer.class).forEach(s ->
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
        Map<String, EntityTextSerializer> result = new HashMap<>();
        ServiceLoader.load(EntitySerializer.class).forEach(s ->
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
