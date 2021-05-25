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
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.plan.generation.transformers.PlanTransformer;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Service;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.ServiceTest;
import org.finos.legend.pure.generated.Root_meta_pure_router_extension_RouterExtension;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.test.junit.LegendSDLCTestCase;
import org.finos.legend.sdlc.test.junit.LegendSDLCTestCaseCollector;

import java.util.function.Consumer;

// Deprecated to account for previous classifier path of service
@Deprecated
public class DeprecatedServiceTestCase extends ServiceTestCase
{

    private DeprecatedServiceTestCase(PureModel pureModel, PureModelContextData pureModelContextData, Service service, ServiceTest serviceTest, MutableList<PlanTransformer> planTransformers, RichIterable<? extends Root_meta_pure_router_extension_RouterExtension> extensions, String pureVersion)
    {
        super(pureModel, pureModelContextData, service, serviceTest, planTransformers, extensions, pureVersion);
    }

    // Keep to handle old classifierPath for service
    @LegendSDLCTestCaseCollector(classifierPath = "meta::alloy::service::metamodel::Service")
    public static void collectTestCases(PureModel pureModel, PureModelContextData pureModelContextData, MutableList<PlanTransformer> planTransformers, RichIterable<? extends Root_meta_pure_router_extension_RouterExtension> extensions, String pureVersion, Entity entity, Consumer<? super LegendSDLCTestCase> testCaseConsumer)
    {
        Service service = findPackageableElement(pureModelContextData.getElementsOfType(Service.class), entity.getPath());
        if (service.test != null)
        {
            testCaseConsumer.accept(new DeprecatedServiceTestCase(pureModel, pureModelContextData, service, service.test, planTransformers, extensions, pureVersion));
        }
    }
}
