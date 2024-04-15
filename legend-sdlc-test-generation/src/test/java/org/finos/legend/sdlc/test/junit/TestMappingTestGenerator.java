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
import org.junit.Test;

public class TestMappingTestGenerator extends AbstractTestClassGeneratorTest<Mapping, MappingTestGenerator>
{
    @Test
    public void testRelationalMapping()
    {
        testGeneration(
                "execution.RelationalMapping",
                "generated/java/execution/RelationalMapping.java",
                null,
                "execution::RelationalMapping");
        testGeneration(
                "org.finos.legend.sdlc.test.junit.junit4.execution.RelationalMapping",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/execution/RelationalMapping.java",
                "org.finos.legend.sdlc.test.junit.junit4",
                "execution::RelationalMapping");
        testGeneration(
                "other.test.pkg.execution.RelationalMapping",
                "generated/java/other/test/pkg/execution/RelationalMapping.java",
                "other.test.pkg",
                "execution::RelationalMapping");
    }

    @Test
    public void testSingleQuoteInResultM2M()
    {
        testGeneration("legend.demo.SingleQuoteInResultM2M",
                "generated/java/legend/demo/SingleQuoteInResultM2M.java",
                null,
                "legend::demo::SingleQuoteInResultM2M");
        testGeneration("org.finos.legend.sdlc.test.junit.junit4.legend.demo.SingleQuoteInResultM2M",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/legend/demo/SingleQuoteInResultM2M.java",
                "org.finos.legend.sdlc.test.junit.junit4",
                "legend::demo::SingleQuoteInResultM2M");
        testGeneration("other.test.pkg.legend.demo.SingleQuoteInResultM2M",
                "generated/java/other/test/pkg/legend/demo/SingleQuoteInResultM2M.java",
                "other.test.pkg",
                "legend::demo::SingleQuoteInResultM2M");
    }

    @Test
    public void testSourceToTargetM2M()
    {
        testGeneration("model.mapping.SourceToTargetM2M",
                "generated/java/model/mapping/SourceToTargetM2M.java",
                null,
                "model::mapping::SourceToTargetM2M");
        testGeneration("org.finos.legend.sdlc.test.junit.junit4.model.mapping.SourceToTargetM2M",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/model/mapping/SourceToTargetM2M.java",
                "org.finos.legend.sdlc.test.junit.junit4",
                "model::mapping::SourceToTargetM2M");
        testGeneration("other.test.pkg.model.mapping.SourceToTargetM2M",
                "generated/java/other/test/pkg/model/mapping/SourceToTargetM2M.java",
                "other.test.pkg",
                "model::mapping::SourceToTargetM2M");
    }

    @Test
    public void testServiceStoreMapping()
    {
        testGeneration(
                "testTestSuites.ServiceStoreMapping",
                "generated/java/testTestSuites/ServiceStoreMapping.java",
                null,
                "testTestSuites::ServiceStoreMapping");
        testGeneration(
                "org.finos.legend.sdlc.test.junit.junit4.testTestSuites.ServiceStoreMapping",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/testTestSuites/ServiceStoreMapping.java",
                "org.finos.legend.sdlc.test.junit.junit4",
                "testTestSuites::ServiceStoreMapping");
        testGeneration(
                "other.test.pkg.testTestSuites.ServiceStoreMapping",
                "generated/java/other/test/pkg/testTestSuites/ServiceStoreMapping.java",
                "other.test.pkg",
                "testTestSuites::ServiceStoreMapping");
    }

    @Override
    protected MappingTestGenerator newGenerator(String packagePrefix)
    {
        return new MappingTestGenerator(packagePrefix);
    }

    @Override
    protected MappingTestGenerator withElement(MappingTestGenerator generator, Mapping mapping)
    {
        return generator.withMapping(mapping);
    }
}
