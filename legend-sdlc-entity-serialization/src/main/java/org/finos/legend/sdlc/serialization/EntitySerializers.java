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

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.impl.utility.LazyIterate;

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
        return ServiceLoader.load(EntitySerializer.class);
    }

    public static Iterable<EntityTextSerializer> getAvailableTextSerializers()
    {
        return LazyIterate.selectInstancesOf(getAvailableSerializers(), EntityTextSerializer.class);
    }

    public static Map<String, EntitySerializer> getAvailableSerializersByName()
    {
        return indexByName(getAvailableSerializers());
    }

    public static Map<String, EntityTextSerializer> getAvailableTextSerializersByName()
    {
        return indexByName(getAvailableTextSerializers());
    }

    private static <T extends EntitySerializer> Map<String, T> indexByName(Iterable<T> serializers)
    {
        Map<String, T> result = Maps.mutable.empty();
        for (T serializer : serializers)
        {
            String name = serializer.getName();
            T old = result.put(name, serializer);
            if (old != null)
            {
                throw new IllegalArgumentException("Multiple serializers named \"" + name + "\"");
            }
        }
        return result;
    }
}
