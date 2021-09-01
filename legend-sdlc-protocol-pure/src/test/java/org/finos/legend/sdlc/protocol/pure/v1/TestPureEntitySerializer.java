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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.eclipse.collections.impl.utility.ArrayIterate;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.engine.shared.core.operational.errorManagement.EngineException;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.serialization.EntitySerializers;
import org.finos.legend.sdlc.serialization.EntityTextSerializer;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public class TestPureEntitySerializer
{
    private final JsonMapper jsonMapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
            .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
            .build();

    private static final Pattern LINE_BREAK = Pattern.compile("\\R");

    private final PureEntitySerializer pureSerializer = new PureEntitySerializer();
    private final EntityTextSerializer defaultJsonSerializer = EntitySerializers.getDefaultJsonSerializer();

    @Test
    public void testEntitySerializers()
    {
        Assert.assertEquals(1, Iterate.count(EntitySerializers.getAvailableSerializers(), s -> s instanceof PureEntitySerializer));
        Assert.assertEquals(1, Iterate.count(EntitySerializers.getAvailableTextSerializers(), s -> s instanceof PureEntitySerializer));
        Assert.assertTrue(EntitySerializers.getAvailableSerializersByName().get("pure") instanceof PureEntitySerializer);
        Assert.assertTrue(EntitySerializers.getAvailableTextSerializersByName().get("pure") instanceof PureEntitySerializer);
    }

    @Test
    public void testSerializeClass()
    {
        testSerialize("class", "TestClass");
        testPureSyntaxError("PARSER error at [5:3]: Unexpected token", "class", "TestBadClass");
    }

    @Test
    public void testSerializeAssociation()
    {
        testSerialize("association", "TestAssociation");
        testPureSyntaxError("PARSER error at [4:37]: Unexpected token", "association", "TestBadAssociation");
    }

    @Test
    public void testSerializeEnumeration()
    {
        testSerialize("enumeration", "TestEnumeration");
    }

    @Test
    public void testM2MMapping()
    {
        testSerialize("mapping", "m2m", "TestMapping");
    }

    @Test
    public void testRelationalMapping()
    {
        testSerialize("mapping", "relational", "TestMapping");
    }

    @Test
    public void testImportsNotAllowed()
    {
        String pureCode = readTextFromResource(buildResourceName("invalid", "TestImports.pure"));
        RuntimeException e = Assert.assertThrows(RuntimeException.class, () -> this.pureSerializer.deserialize(pureCode));
        Assert.assertEquals("Imports in Pure files are not currently supported", e.getMessage());
    }

    @Test
    public void testMultipleElementsNotAllowed()
    {
        String pureCode = readTextFromResource(buildResourceName("invalid", "TestMultiElement.pure"));
        RuntimeException e = Assert.assertThrows(RuntimeException.class, () -> this.pureSerializer.deserialize(pureCode));
        Assert.assertEquals("Expected one element, found 3", e.getMessage());

        String pureCode2 = readTextFromResource(buildResourceName("invalid", "TestMultiSection.pure"));
        RuntimeException e2 = Assert.assertThrows(RuntimeException.class, () -> this.pureSerializer.deserialize(pureCode2));
        Assert.assertEquals("Expected one element, found 2", e2.getMessage());
    }

    @Test
    public void testNonPureEntity()
    {
        Entity entity = readEntityFromJsonResource(buildResourceName("invalid", "TestNonPureEntity.json"));
        Assert.assertFalse(this.pureSerializer.canSerialize(entity));
        IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, () -> this.pureSerializer.serializeToString(entity));
        String message = e.getMessage();
        String expectedStart = "Could not convert entity model::otherthings::AnotherThing with classifier meta::notpure::something::SomeType to class PackageableElement";
        if (!message.startsWith(expectedStart))
        {
            // We don't really want these to be equal, this is just to produce a decent failure message
            Assert.assertEquals(expectedStart, message);
        }
    }

    private void testSerialize(String... names)
    {
        String baseName = buildResourceName(names);
        Entity fullEntity = readEntityFromJsonResource(baseName + "_full.json");
        Entity reducedEntity = readEntityFromJsonResource(baseName + "_reduced.json");
        String pureCode = readTextFromResource(baseName + ".pure");

        Assert.assertTrue(this.pureSerializer.canSerialize(fullEntity));
        assertTextEquivalent(pureCode, this.pureSerializer.serializeToString(fullEntity));
        Assert.assertTrue(this.pureSerializer.canSerialize(reducedEntity));
        assertTextEquivalent(pureCode, this.pureSerializer.serializeToString(reducedEntity));
        assertEntitiesEqual(fullEntity, this.pureSerializer.deserialize(pureCode));
    }

    private void testPureSyntaxError(String expectedErrorMessage, String... names)
    {
        String pureCode = readTextFromResource(buildResourceName(names) + ".pure");
        EngineException e = Assert.assertThrows(EngineException.class, () -> this.pureSerializer.deserialize(pureCode));
        Assert.assertEquals(expectedErrorMessage, EngineException.buildPrettyErrorMessage(e.getMessage(), e.getSourceInformation(), e.getErrorType()));
    }

    private String buildResourceName(String... names)
    {
        return ArrayIterate.makeString(names, "pure-entity-serializer-test/", "/", "");
    }

    private String readTextFromResource(String resourceName)
    {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getResourceInputStream(resourceName), StandardCharsets.UTF_8)))
        {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1)
            {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error reading text from: " + resourceName, e);
        }
    }

    private Entity readEntityFromJsonResource(String resourceName)
    {
        try (InputStream stream = getResourceInputStream(resourceName))
        {
            return this.defaultJsonSerializer.deserialize(stream);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error reading entity from: " + resourceName, e);
        }
    }

    private InputStream getResourceInputStream(String resourceName) throws IOException
    {
        URL url = getClass().getClassLoader().getResource(resourceName);
        if (url == null)
        {
            throw new RuntimeException("Could not find: " + resourceName);
        }
        return url.openStream();
    }

    private void assertEntitiesEqual(Entity expected, Entity actual)
    {
        String expectedJson;
        String actualJson;
        try
        {
            expectedJson = this.jsonMapper.writeValueAsString(expected);
            actualJson = this.jsonMapper.writeValueAsString(actual);
        }
        catch (JsonProcessingException e)
        {
            throw new RuntimeException(e);
        }
        Assert.assertEquals(expectedJson, actualJson);
    }

    private void assertTextEquivalent(String expected, String actual)
    {
        Assert.assertEquals(normalizeLineBreaks(expected), normalizeLineBreaks(actual));
    }

    private static String normalizeLineBreaks(String string)
    {
        return LINE_BREAK.matcher(string).replaceAll("\n");
    }
}
