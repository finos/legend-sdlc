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

package org.finos.legend.sdlc.extensions.collection.entity.serializer;

import org.finos.legend.sdlc.protocol.pure.v1.PureEntitySerializer;
import org.finos.legend.sdlc.serialization.DefaultJsonEntitySerializer;
import org.finos.legend.sdlc.serialization.EntitySerializer;
import org.finos.legend.sdlc.serialization.EntitySerializers;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestExtensions
{

    @Test
    public void testPlanGeneratorExtensionArePresent()
    {
        Map<String, EntitySerializer> serializersByName = EntitySerializers.getAvailableSerializersByName();

        Set<String> expectedNames = Stream.of("pure", "legend").collect(Collectors.toSet());
        Assert.assertEquals(expectedNames, serializersByName.keySet());

        Set<Class<? extends EntitySerializer>> expectedClasses = Stream.of(PureEntitySerializer.class, DefaultJsonEntitySerializer.class).collect(Collectors.toSet());
        Set<Class<? extends EntitySerializer>> actualClasses = serializersByName.values().stream().map(EntitySerializer::getClass).collect(Collectors.toSet());
        Assert.assertEquals(expectedClasses, actualClasses);
    }
}
