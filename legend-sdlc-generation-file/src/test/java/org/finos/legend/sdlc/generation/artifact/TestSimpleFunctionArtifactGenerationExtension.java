// Copyright 2022 Goldman Sachs
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

package org.finos.legend.sdlc.generation.artifact;

import java.util.List;
import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.dsl.generation.extension.Artifact;
import org.finos.legend.engine.language.pure.dsl.generation.extension.ArtifactGenerationExtension;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.PackageableElement;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.ConcreteFunctionDefinition;

public class TestSimpleFunctionArtifactGenerationExtension implements ArtifactGenerationExtension
{
    @Override
    public String getKey()
    {
        return "test-function-generation";
    }

    @Override
    public boolean canGenerate(PackageableElement packageableElement)
    {
        return packageableElement instanceof ConcreteFunctionDefinition;
    }

    @Override
    public List<Artifact> generate(PackageableElement packageableElement, PureModel pureModel, PureModelContextData pureModelContextData, String s)
    {
        String content = "Some output for function '" + packageableElement._name() + "'";
        Artifact artifact = new Artifact(content, "MyFunction.txt", "txt");
        return Lists.mutable.with(artifact);
    }
}
