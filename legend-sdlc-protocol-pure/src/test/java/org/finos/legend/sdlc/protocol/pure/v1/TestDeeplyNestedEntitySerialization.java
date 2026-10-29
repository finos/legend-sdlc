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

package org.finos.legend.sdlc.protocol.pure.v1;

import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.serialization.EntitySerializers;
import org.finos.legend.sdlc.serialization.EntityTextSerializer;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

public class TestDeeplyNestedEntitySerialization
{
    private static final int OR_OPERANDS = 600;

    private final PureEntitySerializer pureSerializer = new PureEntitySerializer();
    private final EntityTextSerializer jsonSerializer = EntitySerializers.getDefaultJsonSerializer();

    @Test
    public void longOrChainFunctionSurvivesPureToEntityAndJsonRoundTrip() throws Exception
    {
        String pure = orChainFunction(OR_OPERANDS);

        Entity entity = this.pureSerializer.deserialize(pure);
        int depth = jsonDepth(entity.getContent());
        Assert.assertTrue("expected the entity content to be deeper than Jackson's default limit, was " + depth, depth > 1000);

        String json = this.jsonSerializer.serializeToString(entity);
        Entity reread = this.jsonSerializer.deserialize(json);
        Assert.assertEquals(entity.getPath(), reread.getPath());
        Assert.assertEquals(entity.getContent(), reread.getContent());
    }

    @Test
    public void longOrChainEntityConvertsBackToPure() throws Throwable
    {
        Entity entity = this.pureSerializer.deserialize(orChainFunction(OR_OPERANDS));
        Entity reread = this.jsonSerializer.deserialize(this.jsonSerializer.serializeToString(entity));

        // Converting back to Pure recurses through Jackson's polymorphic deserializers and overflows a 1 MB stack
        // near this depth; this test is about Jackson's limits, not the stack size.
        String pure = onLargeStack(() -> this.pureSerializer.serializeToString(reread));
        Assert.assertTrue(pure, pure.contains("$x == " + (OR_OPERANDS - 1)));
        Assert.assertEquals(entity.getContent(), this.pureSerializer.deserialize(pure).getContent());
    }

    private static <T> T onLargeStack(Callable<T> callable) throws Throwable
    {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(null, () ->
        {
            try
            {
                result.set(callable.call());
            }
            catch (Throwable t)
            {
                failure.set(t);
            }
        }, "large-stack", 64L << 20);
        thread.start();
        thread.join();
        if (failure.get() != null)
        {
            throw failure.get();
        }
        return result.get();
    }

    static String orChainFunction(int n)
    {
        StringBuilder builder = new StringBuilder("function test::deep(x:Integer[1]):Boolean[1]\n{\n  $x == 0");
        for (int i = 1; i < n; i++)
        {
            builder.append(" || $x == ").append(i);
        }
        return builder.append("\n}\n").toString();
    }

    static int jsonDepth(Object value)
    {
        if (value instanceof Map)
        {
            int max = 0;
            for (Object v : ((Map<?, ?>) value).values())
            {
                max = Math.max(max, jsonDepth(v));
            }
            return max + 1;
        }
        if (value instanceof List)
        {
            int max = 0;
            for (Object v : (List<?>) value)
            {
                max = Math.max(max, jsonDepth(v));
            }
            return max + 1;
        }
        return 0;
    }
}
