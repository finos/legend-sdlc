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

package org.finos.legend.sdlc.test.junit.pure.v1;

import org.eclipse.collections.api.RichIterable;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.plan.execution.PlanExecutor;
import org.finos.legend.engine.plan.generation.transformers.PlanTransformer;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.mappingTest.MappingTest_Legacy;
import org.finos.legend.engine.test.runner.mapping.MappingTestRunner;
import org.finos.legend.pure.generated.Root_meta_pure_extension_Extension;
import org.finos.legend.sdlc.test.junit.LegendSDLCTestCase;

public class LegacyMappingTestCase extends LegendSDLCTestCase
{
    private final LegacyMappingTestHelper helper;

    public LegacyMappingTestCase(String mappingPath, PureModel pureModel, MappingTest_Legacy mappingTest, Iterable<? extends PlanTransformer> planTransformers, RichIterable<? extends Root_meta_pure_extension_Extension> extensions, String pureVersion)
    {
        super(mappingPath);
        this.helper = new LegacyMappingTestHelper(3, mappingPath, new MappingTestRunner(pureModel, mappingPath, mappingTest, PlanExecutor.newPlanExecutorBuilder().withAvailableStoreExecutors().build(), extensions, planTransformers, pureVersion));
    }

    @Override
    protected void doSetUp()
    {
        this.helper.setUp();
    }

    @Override
    protected void doRunTest() throws Exception
    {
        this.helper.runTest();
    }
}
