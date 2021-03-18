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

package org.finos.legend.sdlc.generation.model;

import org.eclipse.collections.api.block.function.Function3;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.CompileContext;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.dsl.generation.extension.ModelGenerationExtension;
import org.finos.legend.engine.protocol.pure.PureClientVersions;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.PackageableElement;

import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

public class ModelGenerator
{
    private PackageableElement generator;
    private MutableList<ModelGenerationExtension> extensions;
    private PureModel pureModel;

    public ModelGenerator()
    {

    }

    private ModelGenerator(PackageableElement generator, PureModel pureModel)
    {
        this.generator = generator;
        this.extensions = Iterate.addAllTo(ServiceLoader.load(ModelGenerationExtension.class), Lists.mutable.empty());
        this.pureModel = pureModel;
    }

    public static ModelGenerator newGenerator(PackageableElement packageableElement, PureModel pureModel)
    {
        return new ModelGenerator(packageableElement, pureModel);
    }

    public String getName()
    {
        return this.generator.getName();
    }

    public PureModelContextData generateModel()
    {
        List<Function3<PackageableElement, CompileContext, String, PureModelContextData>> generators = ListIterate.flatCollect(extensions, ModelGenerationExtension::getPureModelContextDataGenerators);
        return generators.stream()
                .map(generator -> generator.value(this.generator, this.pureModel.getContext(), PureClientVersions.latest))
                .filter(Objects::nonNull).findFirst()
                .orElseThrow(() -> new UnsupportedOperationException("No model generator found for element '" + this.generator.getClass().getSimpleName() + "'"));
    }

}
