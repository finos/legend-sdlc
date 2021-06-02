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

package org.finos.legend.sdlc.protocol;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.finos.legend.engine.protocol.pure.v1.PureProtocolObjectMapperFactory;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Association;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Class;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Optional;

public class TestProtocolToEntityConverter
{
    private static final String CLASS_CLASSIFIER_PATH = "meta::pure::metamodel::type::Class";

    @Test
    public void testToEntity()
    {
        PermissiveClassToEntityConverter converter = new PermissiveClassToEntityConverter();
        Class cls = new Class();
        cls.superTypes = Collections.singletonList("meta::pure::metamodel::type::Any");
        cls.name = "EmptyClass";
        cls._package = "model::test";
        Entity entity = converter.toEntity(cls);
        assertEntityEqualsClass(cls, entity);

        try
        {
            converter.toEntity(new Association());
            Assert.fail();
        }
        catch (IllegalArgumentException e)
        {
            Assert.assertEquals("Could not convert instance of " + Association.class.getName() + " to Entity: no appropriate classifier found", e.getMessage());
        }
    }

    @Test
    public void testToEntityIfPossible()
    {
        PermissiveClassToEntityConverter converter = new PermissiveClassToEntityConverter();
        Class cls = new Class();
        cls.superTypes = Collections.singletonList("meta::pure::metamodel::type::Any");
        cls.name = "EmptyClass";
        cls._package = "model::test";
        Optional<Entity> entity = converter.toEntityIfPossible(cls);
        Assert.assertTrue(entity.isPresent());
        assertEntityEqualsClass(cls, entity.get());

        Optional<Entity> notAClass = converter.toEntityIfPossible(new Association());
        Assert.assertFalse(notAClass.isPresent());
    }

    private static class PermissiveClassToEntityConverter extends ProtocolToEntityConverter<PackageableElement>
    {

        PermissiveClassToEntityConverter()
        {
            super(PureProtocolObjectMapperFactory.withPureProtocolExtensions(JsonMapper.builder().build()));
        }

        @Override
        protected String getClassifierForClass(java.lang.Class<?> cls)
        {
            return (Class.class == cls) ? CLASS_CLASSIFIER_PATH : null;
        }

        @Override
        protected String getEntityPath(PackageableElement element)
        {
            return element.getPath();
        }
    }

    public static void assertEntityEqualsClass(org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Class cls, Entity entity)
    {
        Assert.assertNotNull(entity);
        Assert.assertEquals(CLASS_CLASSIFIER_PATH, entity.getClassifierPath());
        Assert.assertNotNull(cls);
        Assert.assertEquals(cls.getPath(), entity.getPath());
        Assert.assertEquals(cls.name, entity.getContent().get("name"));
        Assert.assertEquals(cls._package, entity.getContent().get("package"));
        Assert.assertEquals(cls.properties, entity.getContent().get("properties"));
        Assert.assertEquals(cls.superTypes, entity.getContent().get("superTypes"));
    }
}

