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

import org.finos.legend.engine.test.runner.mapping.MappingTestRunner;
import org.finos.legend.engine.test.runner.mapping.RichMappingTestResult;
import org.finos.legend.engine.test.runner.shared.TestResult;

import java.util.Optional;

class LegacyMappingTestHelper extends TestHelper
{
    private final MappingTestRunner testRunner;

    LegacyMappingTestHelper(int junitVersion, String entityPath, MappingTestRunner testRunner)
    {
        super(junitVersion, entityPath);
        this.testRunner = testRunner;
    }

    void setUp() throws Exception
    {
        this.testRunner.setupTestData();
    }

    @Override
    void runTest() throws Exception
    {
        RichMappingTestResult result = this.testRunner.doRunTest();
        if (result.getResult() == TestResult.ERROR)
        {
            Exception e = result.getException();
            if (e != null)
            {
                throw e;
            }
            throw new RuntimeException("Unknown error running mapping test '" + this.testRunner.mappingTestLegacy.name + "' for Mapping '" + this.entityPath + "'");
        }
        if (result.getResult() == TestResult.FAILURE)
        {
            String message = "Test failure for mapping test '" + this.testRunner.mappingTestLegacy.name + "' for Mapping '" + this.entityPath + "'";
            Optional<String> expected = result.getExpected();
            Optional<String> actual = result.getActual();
            if (expected.isPresent() || actual.isPresent())
            {
                assertEquals(message, expected.orElse(null), actual.orElse(null));
            }
            else
            {
                fail(message);
            }
        }
    }
}
