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
import org.eclipse.collections.impl.tuple.Tuples;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.plan.generation.transformers.PlanTransformer;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.connection.Connection;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.MultiExecutionTest;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Service;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.ServiceTest;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.SingleExecutionTest;
import org.finos.legend.engine.test.runner.service.RichServiceTestResult;
import org.finos.legend.engine.test.runner.service.ServiceTestRunner;
import org.finos.legend.pure.generated.Root_meta_pure_router_extension_RouterExtension;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.test.junit.LegendSDLCTestCase;
import org.finos.legend.sdlc.test.junit.LegendSDLCTestCaseCollector;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ServiceTestCase extends LegendPureV1TestCase<Service>
{
    private final ServiceTest serviceTest;
    private final ServiceTestRunner serviceTestRunner;

    public ServiceTestCase(PureModel pureModel, PureModelContextData pureModelContextData, Service service, ServiceTest serviceTest, MutableList<PlanTransformer> planTransformers, RichIterable<? extends Root_meta_pure_router_extension_RouterExtension> extensions, String pureVersion)
    {
        super(pureModel, pureModelContextData, planTransformers, extensions, pureVersion, service);
        this.serviceTest = serviceTest;
        this.serviceTestRunner = new ServiceTestRunner(service, Tuples.pair(pureModelContextData, pureModel), this.planExecutor, extensions, planTransformers, pureVersion);
    }

    @Override
    protected void setUpTestData(Consumer<? super Connection> connectionRegistrar)
    {
        // Do nothing.
    }

    @Override
    protected void doRunTest()
    {
        if (this.serviceTest instanceof SingleExecutionTest || this.serviceTest instanceof MultiExecutionTest)
        {
            try
            {
                List<RichServiceTestResult> richServiceTestResults = this.serviceTestRunner.executeTests();
                handleResults(richServiceTestResults);
            }
            catch (Exception e)
            {
                StringBuilder builder = new StringBuilder("Error running tests");
                String eMessage = e.getMessage();
                if (eMessage != null)
                {
                    builder.append(": ").append(eMessage);
                }
                throw new RuntimeException(builder.toString(), e);
            }
        }
        else
        {
            throw new IllegalArgumentException("Service Pure service executions are supported, got: " + this.entity.execution.getClass().getName());
        }
    }

    private void handleResults(List<RichServiceTestResult> testRun)
    {
        Set<String> failedAsserts = getFailedServiceAssertIndices(testRun);
        Map<String, Exception> erroringAssertsWithExceptions = getErroringServiceAssertIndicesWithException(testRun);

        if (!failedAsserts.isEmpty() || !erroringAssertsWithExceptions.isEmpty())
        {
            fail(failedAsserts.stream().sorted().collect(Collectors.joining(", ", "Failures for " + this.entity.getPath() + ": ", ".\n"))
                    .concat(erroringAssertsWithExceptions.entrySet().stream().sorted(Map.Entry.comparingByKey())
                            .map(keyValue ->
                            {
                                StringWriter stringWriter = new StringWriter();
                                PrintWriter printWriter = new PrintWriter(stringWriter);
                                keyValue.getValue().printStackTrace(printWriter);
                                String stackTraceString = stringWriter.toString();
                                return "Errors for " + this.entity.getPath() + ": " + keyValue.getKey() + " with error stack trace: \n" + stackTraceString + "\n";
                            })
                            .collect(Collectors.joining("\n", "", ""))));
        }
    }

    private Set<String> getFailedServiceAssertIndices(List<RichServiceTestResult> testRun)
    {
        return testRun.stream()
                .map(RichServiceTestResult::getResults)
                .filter(Objects::nonNull)
                .map(Map::entrySet)
                .flatMap(Collection::stream)
                .filter(e -> org.finos.legend.engine.test.runner.shared.TestResult.FAILURE == e.getValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private Map<String, Exception> getErroringServiceAssertIndicesWithException(List<RichServiceTestResult> testRun)
    {
        return testRun.stream()
                .map(RichServiceTestResult::getAssertExceptions)
                .filter(Objects::nonNull)
                .filter(map -> !map.isEmpty())
                .map(Map::entrySet)
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @LegendSDLCTestCaseCollector(collectorClass = Service.class)
    public static void collectTestCases(PureModel pureModel, PureModelContextData pureModelContextData, MutableList<PlanTransformer> planTransformers, RichIterable<? extends Root_meta_pure_router_extension_RouterExtension> extensions, String pureVersion, Entity entity, Consumer<? super LegendSDLCTestCase> testCaseConsumer)
    {
        Service service = findPackageableElement(pureModelContextData.getElementsOfType(Service.class), entity.getPath());
        if (service.test != null)
        {
            testCaseConsumer.accept(new ServiceTestCase(pureModel, pureModelContextData, service, service.test, planTransformers, extensions, pureVersion));
        }
    }
}
