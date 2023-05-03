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
// limitations under the License.

package org.finos.legend.sdlc.test.junit.pure.v1;

import org.finos.legend.engine.language.pure.modelManager.ModelManager;
import org.finos.legend.engine.shared.core.deployment.DeploymentMode;
import org.finos.legend.engine.testable.TestableRunner;
import org.junit.Test;

public abstract class AbstractTestableTest extends AbstractPureTest
{
    @Test
    public void testTestable() throws Exception
    {
        TestableHelper helper = new TestableHelper(4, getEntityPath(), new TestableRunner(new ModelManager(DeploymentMode.PROD)), getPureModel(), getPureModelContextData());
        helper.runTest();
    }
}
