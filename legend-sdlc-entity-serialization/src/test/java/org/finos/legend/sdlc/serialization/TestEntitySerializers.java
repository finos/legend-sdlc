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

package org.finos.legend.sdlc.serialization;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

public class TestEntitySerializers
{
    @Test
    public void testGetDefaultJsonSerializer()
    {
        Assert.assertTrue(EntitySerializers.getDefaultJsonSerializer() instanceof DefaultJsonEntitySerializer);
    }

    @Test
    public void testGetAvailableSerializers()
    {
        MutableList<EntitySerializer> serializers = Lists.mutable.withAll(EntitySerializers.getAvailableSerializers());
        Assert.assertEquals(1, serializers.size());
        Assert.assertTrue(serializers.get(0) instanceof DefaultJsonEntitySerializer);
    }

    @Test
    public void testGetAvailableTextSerializers()
    {
        MutableList<EntityTextSerializer> serializers = Lists.mutable.withAll(EntitySerializers.getAvailableTextSerializers());
        Assert.assertEquals(1, serializers.size());
        Assert.assertTrue(serializers.get(0) instanceof DefaultJsonEntitySerializer);
    }

    @Test
    public void testGetAvailableSerializersByName()
    {
        Map<String, EntitySerializer> index = EntitySerializers.getAvailableSerializersByName();
        Assert.assertEquals(1, index.size());
        Assert.assertEquals(Collections.singleton("legend"), index.keySet());
        Assert.assertTrue(index.get("legend") instanceof DefaultJsonEntitySerializer);
    }

    @Test
    public void testGetAvailableTextSerializersByName()
    {
        Map<String, EntityTextSerializer> index = EntitySerializers.getAvailableTextSerializersByName();
        Assert.assertEquals(1, index.size());
        Assert.assertEquals(Collections.singleton("legend"), index.keySet());
        Assert.assertTrue(index.get("legend") instanceof DefaultJsonEntitySerializer);
    }
}
