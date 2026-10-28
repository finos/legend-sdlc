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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamWriteConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Entities (deeply nested lambdas) and execution plans have no size we can bound, and existing entity files can
 * exceed the stream limits Jackson 2.15+ applies by default, so every limit is lifted, not only nesting depth.
 * <p>
 * Applied per mapper, never to the JVM-wide defaults, so it cannot leak into other Maven plugins sharing the JVM.
 * <p>
 * Lifted only on Jackson 2.18+, the first with every builder method used here ({@code maxTokenCount} is the
 * newest); on 2.15 to 2.17 they would throw {@link NoSuchMethodError}, so a consumer pinning an older Jackson
 * keeps that version's own limits.
 */
public final class UnlimitedStreamConstraints
{
    private static final boolean CAN_LIFT_CONSTRAINTS = hasNewestConstraintApi();

    private UnlimitedStreamConstraints()
    {
    }

    public static <M extends ObjectMapper> M applyTo(M mapper)
    {
        if (CAN_LIFT_CONSTRAINTS)
        {
            Constraints.applyTo(mapper.getFactory());
        }
        return mapper;
    }

    private static boolean hasNewestConstraintApi()
    {
        try
        {
            Class.forName("com.fasterxml.jackson.core.StreamReadConstraints$Builder", false, JsonFactory.class.getClassLoader())
                    .getMethod("maxTokenCount", long.class);
            return true;
        }
        catch (ClassNotFoundException | NoSuchMethodException e)
        {
            return false;
        }
    }

    private static final class Constraints
    {
        private static final StreamReadConstraints READ = StreamReadConstraints.builder()
                .maxNestingDepth(Integer.MAX_VALUE)
                .maxDocumentLength(-1L)
                .maxTokenCount(-1L)
                .maxNumberLength(Integer.MAX_VALUE)
                .maxStringLength(Integer.MAX_VALUE)
                .maxNameLength(Integer.MAX_VALUE)
                .build();
        private static final StreamWriteConstraints WRITE = StreamWriteConstraints.builder()
                .maxNestingDepth(Integer.MAX_VALUE)
                .build();

        private static void applyTo(JsonFactory factory)
        {
            factory.setStreamReadConstraints(READ);
            factory.setStreamWriteConstraints(WRITE);
        }
    }
}
