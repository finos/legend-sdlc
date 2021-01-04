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

package org.finos.legend.sdlc.protocol.pure.v1;

import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Class;
import org.finos.legend.sdlc.domain.model.TestTools;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Optional;

public class TestEntityToPureConverter
{
    private final EntityToPureConverter converter = new EntityToPureConverter();

    @Test
    public void testFromEntity()
    {
        Entity entity = TestTools.newClassEntity("EmptyClass", "model::test");
        PackageableElement result = this.converter.fromEntity(entity);
        Assert.assertTrue(result instanceof Class);
        Class resultClass = (Class) result;
        Assert.assertEquals("EmptyClass", resultClass.name);
        Assert.assertEquals("model::test", resultClass._package);
        Assert.assertEquals(Collections.singletonList("meta::pure::metamodel::type::Any"), resultClass.superTypes);
        Assert.assertEquals(Collections.emptyList(), resultClass.properties);

        IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, () -> this.converter.fromEntity(Entity.newEntity("not::a::real::PureEntity", "meta::unknown::NotAClassifier", Collections.emptyMap())));
        String message = e.getMessage();
        Assert.assertNotNull(message);
        String expectedPrefix = "Could not convert entity not::a::real::PureEntity with classifier meta::unknown::NotAClassifier to class PackageableElement";
        if (!message.startsWith(expectedPrefix))
        {
            Assert.assertEquals(expectedPrefix, message);
        }
    }

    @Test
    public void testFromEntityIfPossible()
    {
        Entity entity = TestTools.newClassEntity("EmptyClass", "model::test");
        Optional<PackageableElement> result = this.converter.fromEntityIfPossible(entity);
        Assert.assertTrue(result.isPresent());
        Assert.assertTrue(result.get() instanceof Class);
        Class resultClass = (Class) result.get();
        Assert.assertEquals("EmptyClass", resultClass.name);
        Assert.assertEquals("model::test", resultClass._package);
        Assert.assertEquals(Collections.singletonList("meta::pure::metamodel::type::Any"), resultClass.superTypes);
        Assert.assertEquals(Collections.emptyList(), resultClass.properties);

        Optional<PackageableElement> nothing = this.converter.fromEntityIfPossible(Entity.newEntity("note::a::real::PureEntity", "meta::unknown::NotAClassifier", Collections.emptyMap()));
        Assert.assertFalse(nothing.isPresent());
    }
}
