// Copyright 2023 Goldman Sachs
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

package org.finos.legend.sdlc.test.junit;

import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.Mapping;
import org.finos.legend.sdlc.generation.GeneratorTemplate;

class MappingTestGenerator extends AbstractTestClassGenerator
{
    private static final String LEGACY_TEST_COUNT_PARAM = "legacyTestCount";

    MappingTestGenerator(String packagePrefix)
    {
        super(GeneratorTemplate.fromResource("org/finos/sdlc/generation/MappingTestClassTemplate.ftl"), packagePrefix);
    }

    MappingTestGenerator withMapping(Mapping mapping)
    {
        setElement(mapping);
        setParameter(LEGACY_TEST_COUNT_PARAM, (mapping.tests == null) ? 0 : mapping.tests.size());
        return this;
    }
}
