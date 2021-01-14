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

package org.finos.legend.sdlc.language.pure.compiler.toPureGraph;

import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.shared.core.deployment.DeploymentMode;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.protocol.pure.v1.PureModelContextDataBuilder;

import java.util.stream.Stream;

public class PureModelBuilder
{
    private final PureModelContextDataBuilder contextDataBuilder = PureModelContextDataBuilder.newBuilder();

    private PureModelBuilder()
    {
    }

    public int getElementCount()
    {
        return this.contextDataBuilder.getElementCount();
    }

    public void addPackageableElement(PackageableElement element)
    {
        this.contextDataBuilder.addPackageableElement(element);
    }

    public PureModelBuilder withPackageableElement(PackageableElement element)
    {
        addPackageableElement(element);
        return this;
    }

    public void addEntity(Entity entity)
    {
        this.contextDataBuilder.addEntity(entity);
    }

    public PureModelBuilder withEntity(Entity entity)
    {
        addEntity(entity);
        return this;
    }

    public void addEntities(Stream<? extends Entity> entities)
    {
        this.contextDataBuilder.addEntities(entities);
    }

    public PureModelBuilder withEntities(Stream<? extends Entity> entities)
    {
        addEntities(entities);
        return this;
    }

    public void addEntities(Iterable<? extends Entity> entities)
    {
        this.contextDataBuilder.addEntities(entities);
    }

    public PureModelBuilder withEntities(Iterable<? extends Entity> entities)
    {
        addEntities(entities);
        return this;
    }

    public void addEntities(Entity... entities)
    {
        this.contextDataBuilder.addEntities(entities);
    }

    public PureModelBuilder withEntities(Entity... entities)
    {
        addEntities(entities);
        return this;
    }

    public PureModelWithContextData build()
    {
        return build(null);
    }

    public PureModelWithContextData build(ClassLoader classLoader)
    {
        PureModelContextData pureModelContextData = this.contextDataBuilder.build();
        PureModel pureModel = buildPureModel(pureModelContextData, classLoader);
        return new PureModelWithContextData(pureModel, pureModelContextData);
    }

    public PureModel buildPureModel()
    {
        return buildPureModel(null);
    }

    public PureModel buildPureModel(ClassLoader classLoader)
    {
        return buildPureModel(this.contextDataBuilder.build(), classLoader);
    }

    public static PureModelBuilder newBuilder()
    {
        return new PureModelBuilder();
    }

    private static PureModel buildPureModel(PureModelContextData pureModelContextData, ClassLoader classLoader)
    {
        return new PureModel(pureModelContextData, null, classLoader, DeploymentMode.PROD);
    }

    public static class PureModelWithContextData
    {
        private final PureModel pureModel;
        private final PureModelContextData pureModelContextData;

        private PureModelWithContextData(PureModel pureModel, PureModelContextData pureModelContextData)
        {
            this.pureModel = pureModel;
            this.pureModelContextData = pureModelContextData;
        }

        public PureModel getPureModel()
        {
            return this.pureModel;
        }

        public PureModelContextData getPureModelContextData()
        {
            return this.pureModelContextData;
        }
    }
}
