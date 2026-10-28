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

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamWriteConstraints;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class TestUnlimitedStreamConstraints
{
    @Test
    public void everyStreamLimitIsLifted()
    {
        JsonMapper mapper = UnlimitedStreamConstraints.applyTo(JsonMapper.builder().build());

        StreamReadConstraints read = mapper.getFactory().streamReadConstraints();
        Assert.assertEquals(Integer.MAX_VALUE, read.getMaxNestingDepth());
        Assert.assertEquals(Integer.MAX_VALUE, read.getMaxStringLength());
        Assert.assertEquals(Integer.MAX_VALUE, read.getMaxNumberLength());
        Assert.assertEquals(Integer.MAX_VALUE, read.getMaxNameLength());
        Assert.assertEquals(-1L, read.getMaxDocumentLength());
        Assert.assertEquals(-1L, read.getMaxTokenCount());
        StreamWriteConstraints write = mapper.getFactory().streamWriteConstraints();
        Assert.assertEquals(Integer.MAX_VALUE, write.getMaxNestingDepth());
    }

    @Test
    public void theMapperKeepsItsOwnConfiguration()
    {
        JsonMapper mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        Assert.assertSame(mapper, UnlimitedStreamConstraints.applyTo(mapper));
        Assert.assertTrue(mapper.isEnabled(SerializationFeature.INDENT_OUTPUT));
    }

    @Test
    public void theJvmWideDefaultsAreLeftAlone()
    {
        UnlimitedStreamConstraints.applyTo(JsonMapper.builder().build());
        Assert.assertEquals(StreamReadConstraints.DEFAULT_MAX_DEPTH, StreamReadConstraints.defaults().getMaxNestingDepth());
        Assert.assertEquals(StreamWriteConstraints.DEFAULT_MAX_DEPTH, StreamWriteConstraints.defaults().getMaxNestingDepth());
    }

    @Test
    public void entityWithAPropertyNameLongerThanJacksonDefaultRoundTrips() throws Exception
    {
        String longName = String.join("", Collections.nCopies(StreamReadConstraints.DEFAULT_MAX_NAME_LEN + 1, "n"));
        assertEntityRoundTrips(Collections.singletonMap(longName, "value"));
    }

    @Test
    public void entityWithANumberLongerThanJacksonDefaultRoundTrips() throws Exception
    {
        BigInteger longNumber = new BigInteger(String.join("", Collections.nCopies(StreamReadConstraints.DEFAULT_MAX_NUM_LEN + 1, "7")));
        DefaultJsonEntitySerializer serializer = new DefaultJsonEntitySerializer();
        String json = serializer.serializeToString(entity(Collections.singletonMap("value", longNumber)));
        Map<String, ?> content = serializer.deserialize(json).getContent();
        Assert.assertEquals(longNumber, new BigInteger(String.valueOf(((Map<?, ?>) content.get("body")).get("value"))));
    }

    private static void assertEntityRoundTrips(Map<String, Object> body) throws Exception
    {
        DefaultJsonEntitySerializer serializer = new DefaultJsonEntitySerializer();
        Entity entity = entity(body);
        Assert.assertEquals(entity.getContent(), serializer.deserialize(serializer.serializeToString(entity)).getContent());
    }

    private static Entity entity(Map<String, Object> body)
    {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("_type", "function");
        content.put("name", "big");
        content.put("package", "test");
        content.put("body", body);
        return Entity.newEntity("test::big", "meta::pure::metamodel::function::ConcreteFunctionDefinition", content);
    }
}
