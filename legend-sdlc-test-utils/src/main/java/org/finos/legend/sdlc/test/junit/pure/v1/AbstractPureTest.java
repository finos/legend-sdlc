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

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.plan.generation.transformers.PlanTransformer;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.pure.generated.Root_meta_pure_extension_Extension;
import org.junit.BeforeClass;

public abstract class AbstractPureTest
{
    @BeforeClass
    public static void initialize()
    {
        PureTestHelper.initialize();
    }

    protected abstract String getEntityPath();

    protected static PureModel getPureModel()
    {
        return PureTestHelper.getPureModel();
    }

    protected static PureModelContextData getPureModelContextData()
    {
        return PureTestHelper.getPureModelContextData();
    }

    protected static PackageableElement getProtocolElement(String path)
    {
        return PureTestHelper.getProtocolElement(path);
    }

    protected static MutableList<PlanTransformer> getPlanTransformers()
    {
        return PureTestHelper.getPlanTransformers();
    }

    protected static RichIterable<? extends Root_meta_pure_extension_Extension> getRouterExtensions()
    {
        return PureTestHelper.getRouterExtensions();
    }
}
