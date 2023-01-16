// Copyright 2022 Goldman Sachs
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
// limitations under the License

package org.finos.legend.sdlc.test.junit.pure.v1;

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.modelManager.ModelManager;
import org.finos.legend.engine.plan.generation.transformers.PlanTransformer;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.connection.Connection;
import org.finos.legend.engine.protocol.pure.v1.model.test.assertion.status.AssertFail;
import org.finos.legend.engine.protocol.pure.v1.model.test.assertion.status.EqualToJsonAssertFail;
import org.finos.legend.engine.protocol.pure.v1.model.test.result.TestError;
import org.finos.legend.engine.protocol.pure.v1.model.test.result.TestExecuted;
import org.finos.legend.engine.protocol.pure.v1.model.test.result.TestExecutionStatus;
import org.finos.legend.engine.protocol.pure.v1.model.test.result.TestResult;
import org.finos.legend.engine.shared.core.deployment.DeploymentMode;
import org.finos.legend.engine.testable.TestableRunner;
import org.finos.legend.engine.testable.model.RunTestsInput;
import org.finos.legend.engine.testable.model.RunTestsResult;
import org.finos.legend.engine.testable.model.RunTestsTestableInput;
import org.finos.legend.pure.generated.Root_meta_pure_extension_Extension;
import org.finos.legend.pure.m3.coreinstance.meta.pure.test.Testable;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.test.junit.LegendSDLCTestCase;
import org.finos.legend.sdlc.test.junit.LegendSDLCTestCaseCollector;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TestableTestCase extends LegendPureV1TestCase<PackageableElement>
{
    private final RunTestsInput runTestsInput;
    private final TestableRunner testableRunner;

    public TestableTestCase(PureModel pureModel, PureModelContextData pureModelContextData, PackageableElement packageableElement, MutableList<PlanTransformer> planTransformers, RichIterable<? extends Root_meta_pure_extension_Extension> extensions, String pureVersion)
    {
        super(pureModel, pureModelContextData, planTransformers, extensions, pureVersion, packageableElement);

        RunTestsTestableInput runTestsTestableInput = new RunTestsTestableInput();
        runTestsTestableInput.testable = packageableElement.getPath();
        runTestsTestableInput.unitTestIds = Collections.emptyList();

        RunTestsInput runTestsInput = new RunTestsInput();
        runTestsInput.model = pureModelContextData;
        runTestsInput.testables = Collections.singletonList(runTestsTestableInput);

        this.runTestsInput = runTestsInput;
        this.testableRunner = new TestableRunner(new ModelManager(DeploymentMode.PROD));
    }

    @Override
    protected void setUpTestData(Consumer<? super Connection> connectionRegistrar)
    {
        // Do nothing.
    }

    @Override
    protected void doRunTest()
    {
        try
        {
            RunTestsResult runTestsResultResult = testableRunner.doTests(this.runTestsInput, null);
            handleDoTestResult(runTestsResultResult);
        }
        catch (Exception e)
        {
            StringBuilder builder = new StringBuilder("Error running test suites");
            String eMessage = e.getMessage();
            if (eMessage != null)
            {
                builder.append(": ").append(eMessage);
            }
            throw new RuntimeException(builder.toString(), e);
        }
    }

    private void handleDoTestResult(RunTestsResult doTestsResult)
    {
        List<TestExecuted> failedTests = doTestsResult.results.stream().filter(res -> res instanceof TestExecuted && ((TestExecuted) res).testExecutionStatus == TestExecutionStatus.FAIL).map(TestExecuted.class::cast).collect(Collectors.toList());
        List<TestError> errorTests = doTestsResult.results.stream().filter(res -> res instanceof TestError).map(TestError.class::cast).collect(Collectors.toList());

        if (!failedTests.isEmpty() || !errorTests.isEmpty())
        {
            StringBuilder str = new StringBuilder();

            String testFailedHead = "Test Failed for " + this.entity.getPath() + ":";
            str.append(testFailedHead).append("\n");
            str.append(String.join("", Collections.nCopies(testFailedHead.length(), "="))).append("\n");

            if (!failedTests.isEmpty())
            {
                str.append("Tests Failures : ").append(failedTests.stream().map(this::buildTestId).sorted().collect(Collectors.joining(", "))).append("\n");
                failedTests.forEach(t -> str.append(buildTestId(t)).append(": \n").append(buildErrorMessageForTestFailure(t)).append("\n"));
            }

            if (!errorTests.isEmpty())
            {
                str.append("Tests Errors : ").append(errorTests.stream().map(this::buildTestId).sorted().collect(Collectors.joining(", "))).append("\n");
                errorTests.forEach(t -> str.append(buildTestId(t)).append(": \n").append(buildErrorMessageForTestError(t)).append("\n"));
            }

            str.append("\n\n");

            fail(str.toString());
        }
    }

    private String buildErrorMessageForTestFailure(TestExecuted testFailed)
    {
        List<AssertFail> failedAsserts = testFailed.assertStatuses.stream().filter(a -> a instanceof AssertFail).map(AssertFail.class::cast).collect(Collectors.toList());

        StringBuilder str = new StringBuilder();
        str.append("Failed Asserts : ").append(failedAsserts.stream().map(a -> a.id).collect(Collectors.joining(", "))).append("\n");
        failedAsserts.forEach(a -> str.append(a.id).append(": ").append(buildErrorMessageForAssertFailure(a)));

        return str.toString();
    }

    private String buildErrorMessageForAssertFailure(AssertFail assertFail)
    {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(assertFail.message).append("\n");

        if (assertFail instanceof EqualToJsonAssertFail)
        {
            stringBuilder.append("  Expected : ").append(((EqualToJsonAssertFail) assertFail).expected).append("\n");
            stringBuilder.append("  Actual : ").append(((EqualToJsonAssertFail) assertFail).actual).append("\n");
        }

        return stringBuilder.toString();
    }

    private String buildErrorMessageForTestError(TestError testError)
    {
        return testError.error;
    }

    private String buildTestId(TestResult testResult)
    {
        StringBuilder str = new StringBuilder();
        if (testResult.testSuiteId != null)
        {
            str.append(testResult.testSuiteId).append(".");
        }
        str.append(testResult.atomicTestId);

        return str.toString();
    }

    @LegendSDLCTestCaseCollector(collectorClass = PackageableElement.class)
    public static void collectTestCases(PureModel pureModel, PureModelContextData pureModelContextData, MutableList<PlanTransformer> planTransformers, RichIterable<? extends Root_meta_pure_extension_Extension> extensions, String pureVersion, Entity entity, Consumer<? super LegendSDLCTestCase> testCaseConsumer)
    {
        org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.PackageableElement packageableElement = pureModel.getPackageableElement(entity.getPath());

        if (!(packageableElement instanceof Testable))
        {
            throw new IllegalArgumentException("Expected '" + entity.getPath() + "' to be instance of Testable");
        }

        if (!((Testable) packageableElement)._tests().isEmpty())
        {
            testCaseConsumer.accept(new TestableTestCase(pureModel, pureModelContextData, findPackageableElement(pureModelContextData.getElements(), entity.getPath()), planTransformers, extensions, pureVersion));
        }
    }
}