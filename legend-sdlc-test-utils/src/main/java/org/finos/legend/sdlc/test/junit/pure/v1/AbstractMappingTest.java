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

package org.finos.legend.sdlc.test.junit.pure.v1;

import org.finos.legend.engine.plan.execution.PlanExecutor;
import org.finos.legend.engine.protocol.pure.v1.model.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.Mapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.mappingTest.MappingTest_Legacy;
import org.finos.legend.engine.test.runner.mapping.MappingTestRunner;

public abstract class AbstractMappingTest extends AbstractTestableTest
{
    protected void runTest(int testNum) throws Exception
    {
        String mappingPath = getEntityPath();
        MappingTest_Legacy mappingTest = getMappingTest(mappingPath, testNum);
        LegacyMappingTestHelper helper = new LegacyMappingTestHelper(4, mappingPath, new MappingTestRunner(getPureModel(), mappingPath, mappingTest, PlanExecutor.newPlanExecutorBuilder().withAvailableStoreExecutors().build(), getRouterExtensions(), getPlanTransformers(), getPureVersion()));
        helper.setUp();
        helper.runTest();
    }

    private MappingTest_Legacy getMappingTest(String mappingPath, int testNum)
    {
        Mapping mapping = getMapping(mappingPath);
        try
        {
            return mapping.tests.get(testNum - 1);
        }
        catch (IndexOutOfBoundsException e)
        {
            throw new RuntimeException("Could not get test #" + testNum + " for Mapping " + mappingPath + " with " + mapping.tests.size() + " tests", e);
        }
    }

    private Mapping getMapping(String mappingPath)
    {
        PackageableElement element = getProtocolElement(mappingPath);
        if (element == null)
        {
            throw new RuntimeException("Could not find mapping: " + mappingPath);
        }
        if (!(element instanceof Mapping))
        {
            throw new RuntimeException("Element " + mappingPath + " is not an instance of Mapping (found " + element.getClass().getName() + ")");
        }
        return (Mapping) element;
    }

    protected String getPureVersion()
    {
        return "vX_X_X";
    }
}
