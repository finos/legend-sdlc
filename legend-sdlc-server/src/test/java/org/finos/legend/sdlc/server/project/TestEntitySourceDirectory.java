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

package org.finos.legend.sdlc.server.project;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.serialization.EntitySerializers;
import org.finos.legend.sdlc.serialization.EntityTextSerializer;
import org.finos.legend.sdlc.server.project.ProjectStructure.EntitySourceDirectory;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

public class TestEntitySourceDirectory
{
    private static final ImmutableList<String> DIRECTORIES = Lists.immutable.with("/src/main/legend", "/src/main/resources/entities", "/a/b/c/d", "/abc");

    @Test
    public void testGetDirectory()
    {
        for (String directory : DIRECTORIES)
        {
            Assert.assertSame(directory, ProjectStructure.newEntitySourceDirectory(directory, new TestSerializer()).getDirectory());
        }
    }

    @Test
    public void testIsPossiblyEntityFilePath()
    {
        for (String directory : DIRECTORIES)
        {
            EntitySourceDirectory sourceDirectory = ProjectStructure.newEntitySourceDirectory(directory, new TestSerializer());
            Assert.assertTrue(sourceDirectory.isPossiblyEntityFilePath(directory + "/model/classes/MyClass.json"));
            Assert.assertTrue(sourceDirectory.isPossiblyEntityFilePath(directory + "/model/accounts/relationships/MyAssociation.json"));
            Assert.assertTrue(sourceDirectory.isPossiblyEntityFilePath(directory + "/EntityWithoutPackage.json"));
            Assert.assertFalse(sourceDirectory.isPossiblyEntityFilePath(directory + "/model/classes/MyClass.pure"));
            Assert.assertFalse(sourceDirectory.isPossiblyEntityFilePath(directory + "/model/classes"));
            Assert.assertFalse(sourceDirectory.isPossiblyEntityFilePath(directory + "/model/classes/"));

            String notDirectory = "/something/else";
            Assert.assertNotEquals(directory, notDirectory);
            Assert.assertFalse(sourceDirectory.isPossiblyEntityFilePath(notDirectory + "/model/classes/MyClass.json"));
            Assert.assertFalse(sourceDirectory.isPossiblyEntityFilePath(notDirectory + "/model/accounts/relationships/MyAssociation.json"));
            Assert.assertFalse(sourceDirectory.isPossiblyEntityFilePath(notDirectory + "/EntityWithoutPackage.json"));
        }
    }

    @Test
    public void testEntityPathToFilePath()
    {
        for (String directory : DIRECTORIES)
        {
            EntitySourceDirectory sourceDirectory = ProjectStructure.newEntitySourceDirectory(directory, new TestSerializer());
            Assert.assertEquals(directory + "/model/classes/MyClass.json", sourceDirectory.entityPathToFilePath("model::classes::MyClass"));
            Assert.assertEquals(directory + "/model/accounts/relationships/MyAssociation.json", sourceDirectory.entityPathToFilePath("model::accounts::relationships::MyAssociation"));
            Assert.assertEquals(directory + "/EntityWithoutPackage.json", sourceDirectory.entityPathToFilePath("EntityWithoutPackage"));
        }
    }

    @Test
    public void testPackagePathToFilePath()
    {
        for (String directory : DIRECTORIES)
        {
            EntitySourceDirectory sourceDirectory = ProjectStructure.newEntitySourceDirectory(directory, new TestSerializer());
            Assert.assertEquals(directory + "/model/classes", sourceDirectory.packagePathToFilePath("model::classes"));
            Assert.assertEquals(directory + "/model/accounts/relationships", sourceDirectory.packagePathToFilePath("model::accounts::relationships"));
        }
    }
    
    @Test
    public void testFilePathToEntityPath()
    {
        for (String directory : DIRECTORIES)
        {
            EntitySourceDirectory sourceDirectory = ProjectStructure.newEntitySourceDirectory(directory, new TestSerializer());
            Assert.assertEquals("model::classes::MyClass", sourceDirectory.filePathToEntityPath(directory + "/model/classes/MyClass.json"));
            Assert.assertEquals("model::accounts::relationships::MyAssociation", sourceDirectory.filePathToEntityPath(directory + "/model/accounts/relationships/MyAssociation.json"));
            Assert.assertEquals("EntityWithoutPackage", sourceDirectory.filePathToEntityPath(directory + "/EntityWithoutPackage.json"));
        }
    }

    @Test
    public void testGetSerializer()
    {
        TestSerializer testSerializer = new TestSerializer();
        Assert.assertSame(testSerializer, ProjectStructure.newEntitySourceDirectory("/a/b/c", testSerializer).getSerializer());

        EntityTextSerializer jsonSerializer = EntitySerializers.getDefaultJsonSerializer();
        Assert.assertSame(jsonSerializer, ProjectStructure.newEntitySourceDirectory("/x/y/z", jsonSerializer).getSerializer());
    }

    private static class TestSerializer implements EntityTextSerializer
    {
        private final EntityTextSerializer delegate = EntitySerializers.getDefaultJsonSerializer();

        @Override
        public String getName()
        {
            return "test";
        }

        @Override
        public String getDefaultFileExtension()
        {
            return this.delegate.getDefaultFileExtension();
        }

        @Override
        public boolean canSerialize(Entity entity)
        {
            return this.delegate.canSerialize(entity);
        }

        @Override
        public void serialize(Entity entity, Writer writer) throws IOException
        {
            this.delegate.serialize(entity, writer);
        }

        @Override
        public Entity deserialize(Reader reader) throws IOException
        {
            return this.delegate.deserialize(reader);
        }
    }
}
