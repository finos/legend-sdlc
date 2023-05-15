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
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.engine.test.runner.service.RichServiceTestResult;
import org.finos.legend.engine.test.runner.service.ServiceTestRunner;
import org.finos.legend.engine.test.runner.shared.TestResult;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

class LegacyServiceTestHelper extends TestHelper
{
    private final ServiceTestRunner testRunner;

    LegacyServiceTestHelper(int junitVersion, String entityPath, ServiceTestRunner testRunner)
    {
        super(junitVersion, entityPath);
        this.testRunner = testRunner;
    }

    @Override
    void runTest() throws Exception
    {
        List<RichServiceTestResult> richServiceTestResults = this.testRunner.executeTests();

        MutableList<String> failures = Lists.mutable.empty();
        MutableMap<String, Exception> errors = Maps.mutable.empty();
        richServiceTestResults.forEach(run ->
        {
            Map<String, TestResult> runResults = run.getResults();
            if (runResults != null)
            {
                runResults.forEach((key, result) ->
                {
                    if (TestResult.FAILURE == result)
                    {
                        failures.add(key);
                    }
                });
            }
            Map<String, Exception> runErrors = run.getAssertExceptions();
            if (runErrors != null)
            {
                errors.putAll(runErrors);
            }
        });

        if (failures.notEmpty() || errors.notEmpty())
        {
            StringBuilder builder = new StringBuilder();
            if (failures.notEmpty())
            {
                builder.append("Failures for ").append(this.entityPath).append(" (").append(failures.size()).append("): ");
                failures.sortThis().appendString(builder, ", ");
            }
            if (errors.notEmpty())
            {
                if (failures.notEmpty())
                {
                    builder.append('\n');
                }
                builder.append("Errors for ").append(this.entityPath).append(" (").append(errors.size()).append(":");
                Lists.mutable.withAll(errors.keySet()).sortThis().forEach(key ->
                {
                    Exception e = errors.get(key);
                    StringWriter stringWriter = new StringWriter();
                    try (PrintWriter printWriter = new PrintWriter(stringWriter))
                    {
                        e.printStackTrace(printWriter);
                    }
                    builder.append("\n").append(key).append(": ").append(stringWriter.getBuffer());
                });
            }
            fail(builder.toString());
        }
    }
}
