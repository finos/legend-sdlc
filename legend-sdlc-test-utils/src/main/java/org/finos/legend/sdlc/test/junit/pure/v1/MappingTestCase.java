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
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.plan.generation.transformers.PlanTransformer;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.connection.Connection;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.Mapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.mappingTest.MappingTest;
import org.finos.legend.engine.test.runner.mapping.MappingTestRunner;
import org.finos.legend.engine.test.runner.mapping.RichMappingTestResult;
import org.finos.legend.engine.test.runner.shared.TestResult;
import org.finos.legend.pure.generated.Root_meta_pure_router_extension_RouterExtension;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.test.junit.LegendSDLCTestCase;
import org.finos.legend.sdlc.test.junit.LegendSDLCTestCaseCollector;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;

public class MappingTestCase extends LegendPureV1TestCase<Mapping>
{
    private final MappingTestRunner mappingTestRunner;

    private MappingTestCase(PureModel pureModel, PureModelContextData pureModelContextData, Mapping mapping, MappingTest mappingTest, MutableList<PlanTransformer> planTransformers, RichIterable<? extends Root_meta_pure_router_extension_RouterExtension> extensions, String pureVersion)
    {
        super(pureModel, pureModelContextData, planTransformers, extensions, pureVersion, mapping);
        this.mappingTestRunner = new MappingTestRunner(pureModel, mapping.getPath(), mappingTest, this.planExecutor, extensions, planTransformers, pureVersion);
    }

    @Override
    protected void setUpTestData(Consumer<? super Connection> connectionRegistrar)
    {
        mappingTestRunner.setupTestData();
    }

    @Override
    protected void doRunTest()
    {
        RichMappingTestResult richMappingTestResult = this.mappingTestRunner.doRunTest();
        MappingTest mappingTest = this.mappingTestRunner.mappingTest;
        if (richMappingTestResult.getResult() == TestResult.FAILURE)
        {
            String message = "Test failure for mapping test '" + mappingTest.name + "' for Mapping '" + this.entity.getPath() + "'";
            Optional<String> expected = richMappingTestResult.getExpected();
            Optional<String> actual = richMappingTestResult.getActual();
            if (expected.isPresent() || actual.isPresent())
            {
                assertEquals(message, expected.orElse(null), actual.orElse(null));
            }
            fail(message);
        }
        else if (richMappingTestResult.getResult() == TestResult.ERROR)
        {
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            richMappingTestResult.getException().printStackTrace(printWriter);
            String stackTraceString = stringWriter.toString();
            fail("Error running mapping test '" + mappingTest.name + "' for Mapping '" + this.entity.getPath() + "' with error stack trace: \n" + stackTraceString + "\n");
        }
    }

    @LegendSDLCTestCaseCollector(collectorClass = Mapping.class)
    public static void collectTestCases(PureModel pureModel, PureModelContextData pureModelContextData, MutableList<PlanTransformer> planTransformers, RichIterable<? extends Root_meta_pure_router_extension_RouterExtension> extensions,String pureVersion, Entity entity, Consumer<? super LegendSDLCTestCase> testCaseConsumer)
    {
        Mapping mapping = findPackageableElement(pureModelContextData.getElementsOfType(Mapping.class), entity.getPath());
        Optional.ofNullable(mapping.tests)
                .orElse(Collections.emptyList())
                .stream()
                .map(test -> new MappingTestCase(pureModel, pureModelContextData, mapping, test, planTransformers, extensions, pureVersion))
                .forEach(testCaseConsumer);
    }
}
