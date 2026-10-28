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

package org.finos.legend.sdlc.serialization;

import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class TestDefaultJsonEntitySerializerDeepNesting
{
    private final DefaultJsonEntitySerializer serializer = new DefaultJsonEntitySerializer();

    @Test
    public void writesEntityNestedDeeperThanJacksonDefault() throws Exception
    {
        // two JSON levels per step: about 1200 levels, past the 1000 default but well inside a thread's stack
        Entity entity = nestedEntity(600);
        String json = this.serializer.serializeToString(entity);
        Assert.assertEquals(entity.getContent(), this.serializer.deserialize(json).getContent());
    }

    @Test
    public void readsEntityFileNestedDeeperThanJacksonDefault() throws Exception
    {
        int depth = 1200;
        StringBuilder json = new StringBuilder("{\"classifierPath\":\"meta::pure::metamodel::function::ConcreteFunctionDefinition\",\"content\":{\"_type\":\"function\",\"name\":\"deep\",\"package\":\"test\",\"body\":");
        for (int i = 0; i < depth; i++)
        {
            json.append("[");
        }
        for (int i = 0; i < depth; i++)
        {
            json.append("]");
        }
        json.append("}}");
        Entity entity = this.serializer.deserialize(json.toString());
        Assert.assertEquals("test::deep", entity.getPath());
    }

    static Entity nestedEntity(int depth)
    {
        Map<String, Object> inner = Collections.singletonMap("value", 1);
        for (int i = 0; i < depth; i++)
        {
            inner = Collections.singletonMap("parameters", Collections.singletonList(inner));
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("_type", "function");
        content.put("name", "deep");
        content.put("package", "test");
        content.put("body", inner);
        return Entity.newEntity("test::deep", "meta::pure::metamodel::function::ConcreteFunctionDefinition", content);
    }
}
