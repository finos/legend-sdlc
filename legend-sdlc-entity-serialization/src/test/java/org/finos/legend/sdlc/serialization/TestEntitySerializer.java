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

import org.finos.legend.sdlc.domain.model.TestTools;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

public abstract class TestEntitySerializer<T extends EntitySerializer>
{
    protected final T serializer = getSerializer();

    @Test
    public void testName()
    {
        Assert.assertEquals(getExpectedName(), this.serializer.getName());
    }

    @Test
    public void testDefaultFileExtension()
    {
        Assert.assertEquals(getExpectedDefaultFileExtension(), this.serializer.getDefaultFileExtension());
    }

    @Test
    public void testSerializationToAndFromBytes() throws IOException
    {
        testSerialization(this.serializer::serializeToBytes, this.serializer::deserialize);
    }

    @Test
    public void testSerializationToBytesFromStream() throws IOException
    {
        testSerialization(this.serializer::serializeToBytes, this::deserializeWithStream);
    }

    @Test
    public void testSerializationToAndFromStream() throws IOException
    {
        testSerialization(this::serializeWithStream, this::deserializeWithStream);
    }

    @Test
    public void testSerializationToStreamFromBytes() throws IOException
    {
        testSerialization(this::serializeWithStream, this.serializer::deserialize);
    }

    protected abstract T getSerializer();

    protected abstract String getExpectedName();

    protected abstract String getExpectedDefaultFileExtension();

    protected byte[] serializeWithStream(Entity entity) throws IOException
    {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream())
        {
            this.serializer.serialize(entity, stream);
            return stream.toByteArray();
        }
    }

    protected Entity deserializeWithStream(byte[] bytes) throws IOException
    {
        try (InputStream stream = new ByteArrayInputStream(bytes))
        {
            return this.serializer.deserialize(stream);
        }
    }

    protected static <T> void testSerialization(IOFunction<? super Entity, ? extends T> serializer, IOFunction<? super T, ? extends Entity> deserializer) throws IOException
    {
        for (Entity entity : getTestEntities())
        {
            T serialization = serializer.apply(entity);
            Entity deserialization = deserializer.apply(serialization);
            assertEntitiesEqualButNotSame(entity.getPath(), entity, deserialization);
        }
    }

    private static void assertEntitiesEqualButNotSame(String message, Entity expected, Entity actual)
    {
        Assert.assertNotSame(message, expected, actual);
        TestTools.assertEntitiesEquivalent(message, expected, actual);
    }

    private static List<Entity> getTestEntities()
    {
        return Arrays.asList(
                TestTools.newClassEntity("EmptyClass", "model::domain::test::empty"),
                TestTools.newClassEntity("EmptyClass2", "model::domain::test::empty"),
                TestTools.newClassEntity("ClassWith1Property", "model::domain::test::notEmpty", TestTools.newProperty("prop1", "String", 0, 1)),
                TestTools.newClassEntity("ClassWith2Properties", "model::domain::test::notEmpty", Arrays.asList(TestTools.newProperty("prop2", "Integer", 1, 1), TestTools.newProperty("prop3", "Date", 0, 1))),
                TestTools.newEnumerationEntity("MusicGenre", "model::domain::test::enums", "ROCK", "SWING", "DISCO", "COJUNTO", "ZYDECO", "INDUSTRIAL"),
                TestTools.newEnumerationEntity("ArtMovements", "model::domain::test::enums", "ROMANTICISM", "POST_IMPRESSIONISM", "ART_NOUVEAU", "PANIC", "ART_DECO")
        );
    }

    protected interface IOFunction<T, R>
    {
        R apply(T t) throws IOException;
    }
}
