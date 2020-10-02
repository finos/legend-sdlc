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

package org.finos.legend.sdlc.domain.model.entity.change;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class TestEntityChange
{
    @Test
    public void testCreateEntity()
    {
        String entityPath = "model::domain::test::TestClass";
        String classifierPath = "meta::pure::metamodel::type::Class";
        Map<String, String> content = new HashMap<>();
        content.put("package", "model::domain::test");
        content.put("name", "TestClass");
        EntityChange change = EntityChange.newCreateEntity(entityPath, classifierPath, content);
        Assert.assertSame(EntityChangeType.CREATE, change.getType());
        Assert.assertEquals(entityPath, change.getEntityPath());
        Assert.assertEquals(classifierPath, change.getClassifierPath());
        Assert.assertEquals(content, change.getContent());
        Assert.assertNull(change.getNewEntityPath());
    }

    @Test
    public void testDeleteEntity()
    {
        String entityPath = "model::domain::test::TestClass";
        EntityChange change = EntityChange.newDeleteEntity(entityPath);
        Assert.assertSame(EntityChangeType.DELETE, change.getType());
        Assert.assertEquals(entityPath, change.getEntityPath());
        Assert.assertNull(change.getClassifierPath());
        Assert.assertNull(change.getContent());
        Assert.assertNull(change.getNewEntityPath());
    }

    @Test
    public void testModifyEntity()
    {
        String entityPath = "model::domain::test::TestClass";
        String classifierPath = "meta::pure::metamodel::type::Class";
        Map<String, String> content = new HashMap<>();
        content.put("package", "model::domain::test");
        content.put("name", "TestClass");
        EntityChange change = EntityChange.newModifyEntity(entityPath, classifierPath, content);
        Assert.assertSame(EntityChangeType.MODIFY, change.getType());
        Assert.assertEquals(entityPath, change.getEntityPath());
        Assert.assertEquals(classifierPath, change.getClassifierPath());
        Assert.assertEquals(content, change.getContent());
        Assert.assertNull(change.getNewEntityPath());
    }

    @Test
    public void testRenameEntity()
    {
        String oldEntityPath = "model::domain::test::TestClass";
        String newEntityPath = "model::domain::test::AnotherTestClass";
        EntityChange change = EntityChange.newRenameEntity(oldEntityPath, newEntityPath);
        Assert.assertSame(EntityChangeType.RENAME, change.getType());
        Assert.assertEquals(oldEntityPath, change.getEntityPath());
        Assert.assertNull(change.getClassifierPath());
        Assert.assertNull(change.getContent());
        Assert.assertEquals(newEntityPath, change.getNewEntityPath());
    }
}
