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

package org.finos.legend.sdlc.test.junit;

import junit.framework.TestCase;
import junit.framework.TestFailure;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.impl.list.fixed.ArrayAdapter;
import org.junit.Assert;
import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

public class TestLegendSDLCTestSuiteBuilder
{
    @Test
    public void testBuildM2MMappingWithTestsTestSuite() throws Exception
    {
        Map<String, Set<String>> expectedTestCasesByTestSuite = Maps.mutable.with(
                "legend::demo::SingleQuoteInResultM2M { Specific }", Sets.immutable.with("legend::demo::SingleQuoteInResultM2M Test #1").castToSet(),
                "model::mapping::SourceToTargetM2M { Specific }", Sets.immutable.with("model::mapping::SourceToTargetM2M Test #1", "model::mapping::SourceToTargetM2M Test #2").castToSet());
        ExpectedTestState expectedTestState = new ExpectedTestState("legend-sdlc-test-m2m-mapping-model-with-tests", 2, 3, 0, 0, expectedTestCasesByTestSuite);

        testTestSuiteBuilder(expectedTestState, "legend-sdlc-test-m2m-mapping-model-with-tests", "legend", "model");
    }

    @Test
    public void testBuildServicesWithTestSuite() throws Exception
    {
        Map<String, Set<String>> expectedTestCasesByTestSuite = Maps.mutable.with(
                "testTestSuites::TestService { Generic }", Sets.immutable.with("testTestSuites::TestService Test #1").castToSet(),
                "testTestSuites::TestService2 { Generic }", Sets.immutable.with("testTestSuites::TestService2 Test #1").castToSet(),
                "testTestSuites::TestService3 { Generic }", Sets.immutable.with("testTestSuites::TestService3 Test #1").castToSet(),
                "testTestSuites::TestService4 { Generic }", Sets.immutable.with("testTestSuites::TestService4 Test #1").castToSet());
        ExpectedTestState expectedTestState = new ExpectedTestState("legend-sdlc-test-service-with-testSuites", 4, 4, 0, 2, expectedTestCasesByTestSuite);

        testTestSuiteBuilder(expectedTestState, "legend-sdlc-test-service-with-testSuites", "testTestSuites");
    }

    @Test
    public void testBuildMappingWithTestSuite() throws Exception
    {
        Map<String, Set<String>> expectedTestCasesByTestSuite = Maps.mutable.with(
                "execution::RelationalMapping { Generic }", Sets.immutable.with("execution::RelationalMapping Test #1").castToSet());
        ExpectedTestState expectedTestState = new ExpectedTestState("legend-sdlc-test-mapping-with-testTestSuites", 1, 1, 0, 0, expectedTestCasesByTestSuite);

        testTestSuiteBuilder(expectedTestState, "legend-sdlc-test-mapping-with-testTestSuites", Lists.immutable.with("data", "execution", "model", "store"), Lists.immutable.with("model::mapping", "model::domain"));
    }

    protected void testTestSuiteBuilder(ExpectedTestState expectedTestState, String suiteName, String... testingPackages)
    {
        testTestSuiteBuilder(expectedTestState, suiteName, ArrayAdapter.adapt(testingPackages), null);
    }

    protected void testTestSuiteBuilder(ExpectedTestState expectedTestState, String suiteName, Iterable<String> includePackagesForTesting, Iterable<String> excludePackagesForTesting)
    {
        TestSuite suite = new LegendSDLCTestSuiteBuilder(suiteName, "vX_X_X").buildSuiteFromPackages(includePackagesForTesting, excludePackagesForTesting);
        expectedTestState.assertTestSuite(suite);
    }

    private String buildFailureMessage(String description, Enumeration<TestFailure> failureEnumeration)
    {
        if (!failureEnumeration.hasMoreElements())
        {
            return "0 " + description + " tests";
        }

        List<TestFailure> failureList = Collections.list(failureEnumeration);
        StringBuilder builder = new StringBuilder().append(failureList.size()).append(' ').append(description).append((failureList.size() == 1) ? " test:" : " tests:");
        failureList.forEach(failure ->
        {
            builder.append('\n').append(failure);
            Throwable t = failure.thrownException();
            if (t != null)
            {
                StringWriter writer = new StringWriter();
                try (PrintWriter pw = new PrintWriter(writer))
                {
                    t.printStackTrace(pw);
                }
                builder.append('\n').append(writer.getBuffer());
            }
        });
        return builder.toString();
    }

    protected class ExpectedTestState
    {
        private final String suiteName;
        private final int expectedTestCount;
        private final int expectedTestCaseCount;
        private final int expectedErrors;
        private final int expectedFailures;
        private final Map<String, Set<String>> expectedTestCasesByTestSuite;

        public ExpectedTestState(String suiteName, int expectedTestCount, int expectedTestCaseCount, int expectedErrors, int expectedFailures, Map<String, Set<String>> expectedTestCasesByTestSuite)
        {
            this.suiteName = suiteName;
            this.expectedTestCount = expectedTestCount;
            this.expectedTestCaseCount = expectedTestCaseCount;
            this.expectedErrors = expectedErrors;
            this.expectedFailures = expectedFailures;
            this.expectedTestCasesByTestSuite = expectedTestCasesByTestSuite;
        }

        public void assertTestSuite(TestSuite suite)
        {
            Map<String, Set<String>> actualTestCasesByTestSuite = Maps.mutable.empty();

            IntStream.range(0, suite.testCount()).forEach(i ->
            {
                TestSuite ts = (TestSuite) suite.testAt(i);
                actualTestCasesByTestSuite.put(ts.getName(), Sets.immutable.fromStream(IntStream.range(0, ts.countTestCases()).mapToObj(j -> ((TestCase) ts.testAt(j)).getName())).castToSet());
            });

            TestResult testResult = new TestResult();
            suite.run(testResult);

            Assert.assertEquals(this.suiteName, suite.getName());
            Assert.assertEquals(this.expectedTestCount, suite.testCount());
            Assert.assertEquals(this.expectedTestCaseCount, suite.countTestCases());

            Assert.assertEquals(suite.countTestCases(), testResult.runCount());
            Assert.assertEquals(buildFailureMessage("erroring", testResult.errors()), this.expectedErrors, testResult.errorCount());
            Assert.assertEquals(buildFailureMessage("failing", testResult.failures()), this.expectedFailures, testResult.failureCount());
            Assert.assertEquals(this.expectedTestCasesByTestSuite, actualTestCasesByTestSuite);
        }
    }
}
