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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.utility.LazyIterate;

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
        return ServiceLoader.load(EntitySerializer.class);
    }

    public static Iterable<EntityTextSerializer> getAvailableTextSerializers()
    {
        return LazyIterate.selectInstancesOf(getAvailableSerializers(), EntityTextSerializer.class);
    }

    public static Map<String, List<EntitySerializer>> getAvailableSerializersByExtension()
    {
        return indexByExtension(getAvailableSerializers());
    }

    public static Map<String, List<EntityTextSerializer>> getAvailableTextSerializersByExtension()
    {
        return indexByExtension(getAvailableTextSerializers());
    }

    private static <T extends EntitySerializer> Map<String, List<T>> indexByExtension(Iterable<T> serializers)
    {
        MutableMap<String, List<T>> result = Maps.mutable.empty();
        serializers.forEach(s -> result.getIfAbsentPut(s.getDefaultFileExtension(), Lists.mutable::empty).add(s));
        return result;
    }
}
