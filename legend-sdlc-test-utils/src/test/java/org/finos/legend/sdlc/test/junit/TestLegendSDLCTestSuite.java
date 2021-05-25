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

import junit.framework.TestSuite;
import org.finos.legend.sdlc.test.PathTools;

public class TestLegendSDLCTestSuite extends TestSuite
{
    public static TestSuite suite()
    {
        return new LegendSDLCTestSuiteBuilder("vX_X_X").buildSuite("Test TestSuite", PathTools.resourceToPath("legend-sdlc-test-m2m-mapping-model-with-tests"));
    }
}
