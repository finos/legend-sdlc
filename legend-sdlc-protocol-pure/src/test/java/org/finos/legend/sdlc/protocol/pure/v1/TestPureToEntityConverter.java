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

import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.junit.Assert;
import org.junit.Test;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Class;
import org.finos.legend.sdlc.protocol.TestProtocolToEntityConverter;

import java.util.Collections;
import java.util.Optional;

public class TestPureToEntityConverter
{
    private final PureToEntityConverter converter = new PureToEntityConverter();

    @Test
    public void testToEntity()
    {
        Class cls = new Class();
        cls.superTypes = Collections.singletonList("meta::pure::metamodel::type::Any");
        cls.name = "EmptyClass";
        cls._package = "model::test";
        Entity entity = this.converter.toEntity(cls);
        TestProtocolToEntityConverter.assertEntityEqualsClass(cls, entity);
    }

    @Test
    public void testToEntityIfPossible()
    {
        Class cls = new Class();
        cls.superTypes = Collections.singletonList("meta::pure::metamodel::type::Any");
        cls.name = "EmptyClass";
        cls._package = "model::test";
        Optional<Entity> entity = this.converter.toEntityIfPossible(cls);
        Assert.assertTrue(entity.isPresent());
        TestProtocolToEntityConverter.assertEntityEqualsClass(cls, entity.get());
    }
}

