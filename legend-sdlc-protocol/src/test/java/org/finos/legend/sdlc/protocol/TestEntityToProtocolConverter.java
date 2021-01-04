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

package org.finos.legend.sdlc.protocol;

import org.finos.legend.engine.protocol.pure.v1.PureProtocolObjectMapperFactory;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Class;
import org.finos.legend.sdlc.domain.model.TestTools;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class TestEntityToProtocolConverter
{
    private static final String CLASS_CLASSIFIER_PATH = "meta::pure::metamodel::type::Class";

    @Test
    public void testFromEntity()
    {
        ClassEntityConverter converter = new ClassEntityConverter();
        Entity entity = TestTools.newClassEntity("EmptyClass", "model::test");
        Class result = converter.fromEntity(entity);
        assertClassEqualsEntity(entity, result);

        IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, () -> converter.fromEntity(TestTools.newEnumerationEntity("SomeEnum", "model::test", "one", "two")));
        Assert.assertEquals("Could not convert entity model::test::SomeEnum with classifier meta::pure::metamodel::type::Enumeration: no appropriate target class found", e.getMessage());
    }

    @Test
    public void testFromEntityIfPossible()
    {
        ClassEntityConverter converter = new ClassEntityConverter();
        Entity entity = TestTools.newClassEntity("EmptyClass", "model::test");
        Class result = converter.fromEntity(entity);
        assertClassEqualsEntity(entity, result);

        Optional<Class> notAClass = converter.fromEntityIfPossible(TestTools.newEnumerationEntity("SomeEnum", "model::test", "one", "two"));
        Assert.assertFalse(notAClass.isPresent());
    }

    private static class ClassEntityConverter extends EntityToProtocolConverter<Class>
    {
        private ClassEntityConverter()
        {
            super(PureProtocolObjectMapperFactory.getNewObjectMapper());
        }

        @Override
        protected java.lang.Class<? extends Class> getTargetClass(Entity entity)
        {
            return CLASS_CLASSIFIER_PATH.equals(entity.getClassifierPath()) ? Class.class : null;
        }
    }

    private static void assertClassEqualsEntity(Entity entity, Class cls)
    {
        Assert.assertNotNull(entity);
        Assert.assertNotNull(cls);
        Assert.assertEquals(entity.getPath(), cls.getPath());
        Assert.assertEquals(CLASS_CLASSIFIER_PATH, entity.getClassifierPath());
        Assert.assertEquals(entity.getContent().get("name"), cls.name);
        Assert.assertEquals(entity.getContent().get("package"), cls._package);
        Assert.assertEquals(entity.getContent().get("superTypes"), cls.superTypes);
        Assert.assertEquals(((Collection<?>) entity.getContent().get("properties")).stream().map(m -> ((Map<?, ?>) m).get("name")).collect(Collectors.toList()),
                cls.properties.stream().map(p -> p.name).collect(Collectors.toList()));
    }
}
