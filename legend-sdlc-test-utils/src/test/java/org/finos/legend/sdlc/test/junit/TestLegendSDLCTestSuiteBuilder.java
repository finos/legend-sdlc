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

import junit.framework.TestFailure;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.finos.legend.sdlc.test.PathTools;
import org.junit.Assert;
import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class TestLegendSDLCTestSuiteBuilder
{
    @Test
    public void testBuildM2MMappingWithTestsTestSuite() throws Exception
    {
        testTestSuiteBuilder("legend-sdlc-test-m2m-mapping-model-with-tests", 2, 2, 0, 0);
    }

    protected void testTestSuiteBuilder(String entitiesResourceName, int expectedTestCount, int expectedTestCaseCount, int expectedErrors, int expectedFailures) throws Exception
    {
        TestSuite suite;
        try (EntityLoader entityLoader = EntityLoader.newEntityLoader(PathTools.resourceToPath(entitiesResourceName)))
        {
            suite = new LegendSDLCTestSuiteBuilder("vX_X_X")
                    .buildSuite(entitiesResourceName, entityLoader);
        }
        assertTestSuite(suite, entitiesResourceName, expectedTestCount, expectedTestCaseCount, expectedErrors, expectedFailures);
    }

    protected void assertTestSuite(TestSuite suite, String entitiesResourceName, int expectedTestCount, int expectedTestCaseCount, int expectedErrors, int expectedFailures)
    {
        Assert.assertEquals(entitiesResourceName, suite.getName());
        Assert.assertEquals(expectedTestCount, suite.testCount());
        Assert.assertEquals(expectedTestCaseCount, suite.countTestCases());

        TestResult testResult = new TestResult();
        suite.run(testResult);
        Assert.assertEquals(suite.countTestCases(), testResult.runCount());
        Assert.assertEquals(buildFailureMessage("erroring", testResult.errors()), expectedErrors, testResult.errorCount());
        Assert.assertEquals(buildFailureMessage("failing", testResult.failures()), expectedFailures, testResult.failureCount());
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
                builder.append('\n').append(writer.toString());
            }
        });
        return builder.toString();
    }
}

