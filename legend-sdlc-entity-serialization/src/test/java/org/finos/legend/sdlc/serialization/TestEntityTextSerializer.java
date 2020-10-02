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

import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public abstract class TestEntityTextSerializer<T extends EntityTextSerializer> extends TestEntitySerializer<T>
{
    @Test
    public void testSerializationToAndFromString() throws IOException
    {
        testSerialization(this.serializer::serializeToString, this.serializer::deserialize);
    }

    @Test
    public void testSerializationToStringFromReader() throws IOException
    {
        testSerialization(this.serializer::serializeToString, this::deserializeWithReader);
    }

    @Test
    public void testSerializationToBytesFromReader() throws IOException
    {
        testSerialization(this.serializer::serializeToBytes, this::deserializeWithReader);
    }

    @Test
    public void testSerializationToStreamFromReader() throws IOException
    {
        testSerialization(this::serializeWithStream, this::deserializeWithReader);
    }

    @Test
    public void testSerializationToWriterFromReader() throws IOException
    {
        testSerialization(this::serializeWithWriter, this::deserializeWithReader);
    }

    @Test
    public void testSerializationToWriterFromString() throws IOException
    {
        testSerialization(this::serializeWithWriter, this.serializer::deserialize);
    }

    @Test
    public void testSerializationToWriterFromStream() throws IOException
    {
        testSerialization(this::serializeToBytesWithWriter, this::deserializeWithStream);
    }

    @Test
    public void testSerializationToWriterFromBytes() throws IOException
    {
        testSerialization(this::serializeToBytesWithWriter, this.serializer::deserialize);
    }

    protected String serializeWithWriter(Entity entity) throws IOException
    {
        try (StringWriter writer = new StringWriter())
        {
            this.serializer.serialize(entity, writer);
            return writer.toString();
        }
    }

    protected byte[] serializeToBytesWithWriter(Entity entity) throws IOException
    {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
             Writer writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8))
        {
            this.serializer.serialize(entity, writer);
            return stream.toByteArray();
        }
    }

    protected Entity deserializeWithReader(String string) throws IOException
    {
        try (Reader reader = new StringReader(string))
        {
            return this.serializer.deserialize(reader);
        }
    }

    protected Entity deserializeWithReader(byte[] bytes) throws IOException
    {
        try (InputStream stream = new ByteArrayInputStream(bytes);
             Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
        {
            return this.serializer.deserialize(reader);
        }
    }
}
