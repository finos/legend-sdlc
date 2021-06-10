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

package org.finos.legend.sdlc.extensions.collection.entity.serializer;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.finos.legend.sdlc.protocol.pure.v1.PureEntitySerializer;
import org.finos.legend.sdlc.serialization.DefaultJsonEntitySerializer;
import org.finos.legend.sdlc.serialization.EntitySerializer;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

public class TestExtensions
{

    @Test
    public void testPlanGeneratorExtensionArePresent()
    {
        assertHasExtensions(Lists.mutable.<Class<? extends EntitySerializer>>empty().with(PureEntitySerializer.class).with(DefaultJsonEntitySerializer.class), EntitySerializer.class, true);
    }

    private <T> void assertHasExtensions(Iterable<? extends Class<? extends T>> expectedExtensionClasses, Class<T> extensionClass, boolean failOnAdditional)
    {

        assertHasExtensions(Lists.mutable.withAll(ServiceLoader.load(extensionClass)), expectedExtensionClasses, extensionClass, failOnAdditional);
    }

    private <T> void assertHasExtensions(List<T> availableClasses, Iterable<? extends Class<? extends T>> expectedExtensionClasses, Class<T> extensionClass, boolean failOnAdditional)
    {
        MutableSet<Class<? extends T>> missingClasses = Sets.mutable.withAll(expectedExtensionClasses);
        MutableList<Class<?>> unexpectedClasses = Lists.mutable.empty();
        availableClasses.forEach(e ->
        {
            if (!missingClasses.remove(e.getClass()))
            {
                unexpectedClasses.add(e.getClass());
            }
        });
        Assert.assertEquals("Missing extensions for " + extensionClass.getName(), Collections.emptySet(), missingClasses);
        if (failOnAdditional)
        {
            Assert.assertEquals("Unexpected extensions for " + extensionClass.getName(), Collections.emptyList(), unexpectedClasses);
        }
    }

}
