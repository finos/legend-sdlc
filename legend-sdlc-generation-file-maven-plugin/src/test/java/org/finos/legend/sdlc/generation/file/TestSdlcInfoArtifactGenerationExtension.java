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

package org.finos.legend.sdlc.generation.file;

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.dsl.generation.extension.Artifact;
import org.finos.legend.engine.language.pure.dsl.generation.extension.ArtifactGenerationExtension;
import org.finos.legend.engine.protocol.pure.v1.model.context.AlloySDLC;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.PackageableElement;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Enumeration;

import java.util.List;

public class TestSdlcInfoArtifactGenerationExtension implements ArtifactGenerationExtension
{
    @Override
    public String getKey()
    {
        return "test-sdlcinfo-generation";
    }

    @Override
    public boolean canGenerate(PackageableElement packageableElement)
    {
        return packageableElement instanceof Enumeration;
    }

    @Override
    public List<Artifact> generate(PackageableElement packageableElement, PureModel pureModel, PureModelContextData pureModelContextData, String s)
    {
        if (pureModelContextData.origin != null && pureModelContextData.origin.sdlcInfo != null)
        {
            String groupId = ((AlloySDLC) pureModelContextData.origin.sdlcInfo).groupId;
            String artifactId = ((AlloySDLC) pureModelContextData.origin.sdlcInfo).artifactId;
            String versionId = pureModelContextData.origin.sdlcInfo.version;

            String content = "Some output for enumeration '" + packageableElement._name() + "' - SDLC Info '" + groupId + ":" + artifactId  + ":" + versionId + "'";
            Artifact artifact = new Artifact(content, "SDLCInfo.txt", "txt");
            return Lists.mutable.with(artifact);
        }

        return Lists.mutable.empty();
    }
}
