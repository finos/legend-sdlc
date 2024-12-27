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

import org.finos.legend.engine.plan.execution.PlanExecutor;
import org.finos.legend.engine.protocol.pure.v1.model.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Service;
import org.finos.legend.engine.test.runner.service.ServiceTestRunner;
import org.junit.Test;

public abstract class AbstractServiceTest extends AbstractTestableTest
{
    @Test
    public void testService() throws Exception
    {
        String servicePath = getEntityPath();
        Service service = getService(servicePath);
        if (service.test != null)
        {
            LegacyServiceTestHelper helper = new LegacyServiceTestHelper(4, servicePath, new ServiceTestRunner(service, null, getPureModelContextData(), getPureModel(), null, PlanExecutor.newPlanExecutorBuilder().withAvailableStoreExecutors().build(), getRouterExtensions(), getPlanTransformers(), getPureVersion()));
            helper.runTest();
        }
    }

    private Service getService(String servicePath)
    {
        PackageableElement element = getProtocolElement(servicePath);
        if (element == null)
        {
            throw new RuntimeException("Could not find service: " + servicePath);
        }
        if (!(element instanceof Service))
        {
            throw new RuntimeException("Element " + servicePath + " is not an instance of Service (found " + element.getClass().getName() + ")");
        }
        return (Service) element;
    }

    protected String getPureVersion()
    {
        return "vX_X_X";
    }
}
