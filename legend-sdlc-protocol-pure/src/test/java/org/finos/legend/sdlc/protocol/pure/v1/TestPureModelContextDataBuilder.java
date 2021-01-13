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

package org.finos.legend.sdlc.protocol.pure.v1;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.finos.legend.engine.protocol.Protocol;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.context.SDLC;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Class;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.Mapping;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.Objects;

public class TestPureModelContextDataBuilder
{
    @Test
    public void testEmpty()
    {
        PureModelContextDataBuilder builder = PureModelContextDataBuilder.newBuilder();
        Assert.assertEquals(0, builder.getElementCount());

        PureModelContextData pureModelContextData = builder.build();
        Assert.assertNull(pureModelContextData.serializer);
        Assert.assertNull(pureModelContextData.origin);
        Assert.assertEquals(Collections.emptyList(), pureModelContextData.getElements());
    }

    @Test
    public void testProtocol()
    {
        for (String protocol : Lists.immutable.with("pure:1", "pure:1.0.0", "pure:X.X.X", "something:13.17.19", "anything:9999999.123456789.1000000001"))
        {
            int index = protocol.indexOf(':');
            String name = protocol.substring(0, index);
            String version = protocol.substring(index + 1);
            Protocol expected = new Protocol(name, version);
            Assert.assertSame(protocol, expected, PureModelContextDataBuilder.newBuilder().withProtocol(expected).build().serializer);
            Assert.assertSame(protocol, expected, PureModelContextDataBuilder.newBuilder().withProtocol(expected).build().origin.serializer);
            Assert.assertNull(protocol, PureModelContextDataBuilder.newBuilder().withProtocol(expected).build().origin.sdlcInfo);
            Assert.assertEquals(protocol, expected, PureModelContextDataBuilder.newBuilder().withProtocol(name, version).build().serializer);
            Assert.assertEquals(protocol, expected, PureModelContextDataBuilder.newBuilder().withProtocol(name, version).build().origin.serializer);
            Assert.assertNull(protocol, PureModelContextDataBuilder.newBuilder().withProtocol(name, version).build().origin.sdlcInfo);
        }
    }

    @Test
    public void testSDLC()
    {
        for (String sdlc : Lists.immutable.with("proj1:abcd", "proj2:efgh", "something:13.17.19", "anything:9999999.123456789.1000000001"))
        {
            int index = sdlc.indexOf(':');
            String projectId = sdlc.substring(0, index);
            String revisionOrVersionID = sdlc.substring(index + 1);
            TestSDLC expected = new TestSDLC(projectId, revisionOrVersionID);
            Assert.assertSame(sdlc, expected, PureModelContextDataBuilder.newBuilder().withSDLC(expected).build().origin.sdlcInfo);
            Assert.assertNull(sdlc, PureModelContextDataBuilder.newBuilder().withSDLC(expected).build().origin.serializer);
        }
    }

    @Test
    public void testProtocolAndSDLC()
    {
        for (String protocol : Lists.immutable.with("pure:1", "pure:1.0.0", "pure:X.X.X", "something:13.17.19", "anything:9999999.123456789.1000000001"))
        {
            int index = protocol.indexOf(':');
            String protocolName = protocol.substring(0, index);
            String protocolVersion = protocol.substring(index + 1);
            Protocol expectedProtocol = new Protocol(protocolName, protocolVersion);
            for (String sdlc : Lists.immutable.with("proj1:abcd", "proj2:efgh", "something:13.17.19", "anything:9999999.123456789.1000000001"))
            {
                String message = protocol + " / " + sdlc;

                index = sdlc.indexOf(':');
                String projectId = sdlc.substring(0, index);
                String revisionOrVersionID = sdlc.substring(index + 1);
                TestSDLC expectedSDLC = new TestSDLC(projectId, revisionOrVersionID);

                Assert.assertSame(message, expectedProtocol, PureModelContextDataBuilder.newBuilder().withProtocol(expectedProtocol).withSDLC(expectedSDLC).build().serializer);
                Assert.assertSame(message, expectedProtocol, PureModelContextDataBuilder.newBuilder().withProtocol(expectedProtocol).withSDLC(expectedSDLC).build().origin.serializer);
                Assert.assertSame(message, expectedSDLC, PureModelContextDataBuilder.newBuilder().withProtocol(expectedProtocol).withSDLC(expectedSDLC).build().origin.sdlcInfo);

                Assert.assertEquals(message, expectedProtocol, PureModelContextDataBuilder.newBuilder().withProtocol(protocolName, protocolVersion).withSDLC(expectedSDLC).build().serializer);
                Assert.assertEquals(message, expectedProtocol, PureModelContextDataBuilder.newBuilder().withProtocol(protocolName, protocolVersion).withSDLC(expectedSDLC).build().origin.serializer);
                Assert.assertSame(message, expectedSDLC, PureModelContextDataBuilder.newBuilder().withProtocol(protocolName, protocolVersion).withSDLC(expectedSDLC).build().origin.sdlcInfo);
            }
        }
    }

    @Test
    public void testProtocolSDLCAndEntities() throws Exception
    {
        String protocolName = "someProtocol";
        String protocolVersion = "3.2.1";
        String project = "someProject";
        String revisionId = "1234567890abcdef";
        PureModelContextDataBuilder builder = PureModelContextDataBuilder.newBuilder();
        Assert.assertEquals(0, builder.getElementCount());
        try (EntityLoader entityLoader = EntityLoader.newEntityLoader(Paths.get(Objects.requireNonNull(getClass().getClassLoader().getResource("pure-model-context-data-builder-test-model")).toURI())))
        {
            builder.withProtocol(protocolName, protocolVersion)
                    .withSDLC(new TestSDLC(project, revisionId))
                    .withEntities(entityLoader.getAllEntities());
        }
        Assert.assertEquals(3, builder.getElementCount());

        PureModelContextData pureModelContextData = builder.build();
        Assert.assertEquals(new Protocol(protocolName, protocolVersion), pureModelContextData.serializer);
        Assert.assertEquals(new Protocol(protocolName, protocolVersion), pureModelContextData.origin.serializer);
        Assert.assertEquals(new TestSDLC(project, revisionId), pureModelContextData.origin.sdlcInfo);

        Assert.assertEquals(3, pureModelContextData.getElements().size());

        String expectedSourceClass = "Class model::domain::Source\n" +
                "{\n" +
                "  oneName: String[1];\n" +
                "  anotherName: String[0..1];\n" +
                "  oneDate: StrictDate[0..1];\n" +
                "  anotherDate: StrictDate[0..1];\n" +
                "  oneNumber: Integer[0..1];\n" +
                "  anotherNumber: Integer[0..1];\n" +
                "}\n";
        String expectedTargetClass = "Class model::domain::Target\n" +
                "{\n" +
                "  name: String[1];\n" +
                "  date: StrictDate[0..1];\n" +
                "  number: Integer[0..1];\n" +
                "}\n";
        PureProtocolHelper.assertElementsEqual(
                Sets.mutable.with(expectedSourceClass, expectedTargetClass),
                pureModelContextData.getElementsOfType(Class.class));

        String expectedMapping = "Mapping model::mapping::SourceToTargetM2M\n" +
                "(\n" +
                "  *model::domain::Target[model_domain_Target]: Pure\n" +
                "  {\n" +
                "    ~src model::domain::Source\n" +
                "    name: $src.oneName,\n" +
                "    date: $src.anotherDate,\n" +
                "    number: $src.oneNumber\n" +
                "  }\n" +
                ")\n";
        PureProtocolHelper.assertElementsEqual(
                Sets.mutable.with(expectedMapping),
                pureModelContextData.getElementsOfType(Mapping.class));
    }

    private static class TestSDLC extends SDLC
    {
        private final String project;

        private TestSDLC(String project, String revisionOrVersionId)
        {
            this.project = project;
            this.baseVersion = revisionOrVersionId;
        }

        @Override
        public boolean equals(Object other)
        {
            return super.equals(other) &&
                    (other instanceof TestSDLC) &&
                    Objects.equals(this.project, ((TestSDLC)other).project);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(this.project, this.baseVersion);
        }
    }
}
