// Copyright 2025 Goldman Sachs
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

package org.finos.legend.sdlc.generation.deployment;

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.engine.deployment.model.DeploymentExtension;
import org.finos.legend.engine.deployment.model.DeploymentResponse;
import org.finos.legend.engine.deployment.model.DeploymentStatus;
import org.finos.legend.engine.protocol.pure.m3.PackageableElement;
import org.finos.legend.engine.protocol.pure.m3.type.Class;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;

import java.util.List;

public class TestBasicClassDeployExtension implements DeploymentExtension
{
    @Override
    public String getKey()
    {
        return "classDeployTest";
    }

    @Override
    public List<String> getSupportedClassifierPaths()
    {
        return Lists.mutable.with("meta::pure::metamodel::type::Class");
    }

    @Override
    public boolean isElementDeployable(PackageableElement packageableElement)
    {
        return packageableElement instanceof Class;
    }

    @Override
    public DeploymentResponse deployElement(PureModelContextData pureModelContextData, PackageableElement packageableElement)
    {
        return new DeploymentResponse(this.getKey(), packageableElement.getPath(), DeploymentStatus.SUCCESS, "deploy success");
    }

    @Override
    public boolean requiresValidation()
    {
        return true;
    }

    @Override
    public DeploymentResponse validateElement(PureModelContextData pureModelContextData, PackageableElement packageableElement)
    {
        return new DeploymentResponse(this.getKey(), packageableElement.getPath(), DeploymentStatus.SUCCESS, "validate success");

    }
}

