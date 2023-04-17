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
// limitations under the License

package org.finos.legend.sdlc.test.junit.pure.v1;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.test.assertion.status.AssertFail;
import org.finos.legend.engine.protocol.pure.v1.model.test.assertion.status.EqualToJsonAssertFail;
import org.finos.legend.engine.protocol.pure.v1.model.test.result.TestError;
import org.finos.legend.engine.protocol.pure.v1.model.test.result.TestExecuted;
import org.finos.legend.engine.protocol.pure.v1.model.test.result.TestExecutionStatus;
import org.finos.legend.engine.protocol.pure.v1.model.test.result.TestResult;
import org.finos.legend.engine.testable.TestableRunner;
import org.finos.legend.engine.testable.model.RunTestsResult;
import org.finos.legend.engine.testable.model.RunTestsTestableInput;

import java.util.List;

class TestableHelper extends TestHelper
{
    private final TestableRunner testableRunner;
    private final PureModel pureModel;
    private final PureModelContextData pureModelContextData;

    TestableHelper(int junitVersion, String entityPath, TestableRunner testableRunner, PureModel pureModel, PureModelContextData pureModelContextData)
    {
        super(junitVersion, entityPath);
        this.testableRunner = testableRunner;
        this.pureModel = pureModel;
        this.pureModelContextData = pureModelContextData;
    }

    @Override
    void runTest() throws Exception
    {
        RunTestsResult result = this.testableRunner.doTests(buildRunTestsTestableInput(this.entityPath), this.pureModel, this.pureModelContextData);

        MutableList<TestExecuted> failures = Lists.mutable.empty();
        MutableList<TestError> errors = Lists.mutable.empty();
        result.results.forEach(res ->
        {
            if (res instanceof TestError)
            {
                errors.add((TestError) res);
            }
            else if (res instanceof TestExecuted)
            {
                TestExecuted executedResult = (TestExecuted) res;
                if (executedResult.testExecutionStatus == TestExecutionStatus.FAIL)
                {
                    failures.add(executedResult);
                }
            }
        });
        if (failures.notEmpty() || errors.notEmpty())
        {
            StringBuilder builder = new StringBuilder("Test Failed for ").append(this.entityPath).append(":\n");
            for (int i = 0, len = builder.length() - 1; i < len; i++)
            {
                builder.append('=');
            }
            builder.append("\n");

            if (failures.notEmpty())
            {
                failures.collect(TestableHelper::buildTestId, Lists.mutable.ofInitialCapacity(failures.size())).sortThis().appendString(builder, "Test Failures : ", ", ", "\n");
                failures.forEach(t -> appendMessageForTestFailure(builder, t).append("\n"));
            }
            if (errors.notEmpty())
            {
                errors.collect(TestableHelper::buildTestId, Lists.mutable.ofInitialCapacity(errors.size())).sortThis().appendString(builder, "Test Errors : ", ", ", "\n");
                errors.forEach(t -> appendMessageForTestError(builder, t).append("\n"));
            }
            fail(builder.toString());
        }
    }

    private static StringBuilder appendMessageForTestFailure(StringBuilder builder, TestExecuted testFailed)
    {
        appendTestId(builder, testFailed).append(":\n");
        MutableList<AssertFail> assertFails = ListIterate.selectInstancesOf(testFailed.assertStatuses, AssertFail.class);
        assertFails.collect(a -> a.id).sortThis().appendString(builder, "Failed Asserts : ", ", ", "\n");
        assertFails.forEach(a ->
        {
            builder.append(a.id).append(": ").append(a.message).append("\n");
            if (a instanceof EqualToJsonAssertFail)
            {
                builder.append("  Expected : ").append(((EqualToJsonAssertFail) a).expected).append("\n");
                builder.append("  Actual : ").append(((EqualToJsonAssertFail) a).actual).append("\n");
            }
        });
        return builder;
    }

    private static StringBuilder appendMessageForTestError(StringBuilder builder, TestError testError)
    {
        return appendTestId(builder, testError).append(":\n").append(testError.error);
    }

    private static String buildTestId(TestResult testResult)
    {
        return (testResult.testSuiteId == null) ? testResult.atomicTestId : (testResult.testSuiteId + "." + testResult.atomicTestId);
    }

    private static StringBuilder appendTestId(StringBuilder builder, TestResult testResult)
    {
        if (testResult.testSuiteId != null)
        {
            builder.append(testResult.testSuiteId).append(".");
        }
        return builder.append(testResult.atomicTestId);
    }

    private static List<RunTestsTestableInput> buildRunTestsTestableInput(String elementPath)
    {
        RunTestsTestableInput runTestsTestableInput = new RunTestsTestableInput();
        runTestsTestableInput.testable = elementPath;
        runTestsTestableInput.unitTestIds = Lists.fixedSize.empty();
        return Lists.immutable.with(runTestsTestableInput).castToList();
    }
}
