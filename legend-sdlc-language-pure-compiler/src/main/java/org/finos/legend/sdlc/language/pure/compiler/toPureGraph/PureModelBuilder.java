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
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModelProcessParameter;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.extension.CompilerExtension;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.extension.CompilerExtensions;
import org.finos.legend.engine.protocol.Protocol;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.context.SDLC;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.shared.core.deployment.DeploymentMode;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.protocol.pure.v1.EntityToPureConverter;
import org.finos.legend.sdlc.protocol.pure.v1.PureModelContextDataBuilder;

import java.util.ServiceLoader;
import java.util.stream.Stream;

public class PureModelBuilder
{
    private final PureModelContextDataBuilder contextDataBuilder;
    private ClassLoader classLoader;
    private CompilerExtensions extensions;
    private String packagePrefix;

    private PureModelBuilder(EntityToPureConverter converter)
    {
        this.contextDataBuilder = PureModelContextDataBuilder.newBuilder(converter);
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

    public boolean addEntityIfPossible(Entity entity)
    {
        return this.contextDataBuilder.addEntityIfPossible(entity);
    }

    public PureModelBuilder withEntityIfPossible(Entity entity)
    {
        addEntityIfPossible(entity);
        return this;
    }

    public void addEntitiesIfPossible(Stream<? extends Entity> entities)
    {
        this.contextDataBuilder.addEntitiesIfPossible(entities);
    }

    public PureModelBuilder withEntitiesIfPossible(Stream<? extends Entity> entities)
    {
        addEntitiesIfPossible(entities);
        return this;
    }

    public void addEntitiesIfPossible(Iterable<? extends Entity> entities)
    {
        this.contextDataBuilder.addEntitiesIfPossible(entities);
    }

    public PureModelBuilder withEntitiesIfPossible(Iterable<? extends Entity> entities)
    {
        addEntitiesIfPossible(entities);
        return this;
    }

    public void addEntitiesIfPossible(Entity... entities)
    {
        this.contextDataBuilder.addEntitiesIfPossible(entities);
    }

    public PureModelBuilder withEntitiesIfPossible(Entity... entities)
    {
        addEntitiesIfPossible(entities);
        return this;
    }

    public PureModelBuilder withSDLC(SDLC sdlc)
    {
        this.contextDataBuilder.withSDLC(sdlc);
        return this;
    }

    public PureModelBuilder withProtocol(Protocol protocol)
    {
        this.contextDataBuilder.withProtocol(protocol);
        return this;
    }

    public void setClassLoader(ClassLoader classLoader)
    {
        this.classLoader = classLoader;
    }

    public PureModelBuilder withClassLoader(ClassLoader classLoader)
    {
        setClassLoader(classLoader);
        return this;
    }

    public void setCompilerExtensions(CompilerExtensions extensions)
    {
        this.extensions = extensions;
    }

    public PureModelBuilder withCompilerExtensions(CompilerExtensions extensions)
    {
        setCompilerExtensions(extensions);
        return this;
    }

    public void setPackagePrefix(String packagePrefix)
    {
        this.packagePrefix = packagePrefix;
    }

    public PureModelBuilder withPackagePrefix(String packagePrefix)
    {
        setPackagePrefix(packagePrefix);
        return this;
    }

    public PureModelWithContextData build()
    {
        PureModelContextData pureModelContextData = this.contextDataBuilder.build();
        PureModel pureModel = buildPureModel(pureModelContextData);
        return new PureModelWithContextData(pureModel, pureModelContextData);
    }

    @Deprecated
    public PureModelWithContextData build(ClassLoader classLoader)
    {
        return withClassLoader(classLoader).build();
    }

    public PureModel buildPureModel()
    {
        return buildPureModel(this.contextDataBuilder.build());
    }

    @Deprecated
    public PureModel buildPureModel(ClassLoader classLoader)
    {
        return withClassLoader(classLoader).buildPureModel();
    }

    private PureModel buildPureModel(PureModelContextData pureModelContextData)
    {
        return new PureModel(pureModelContextData, getExtensions(), null, this.classLoader, DeploymentMode.PROD, new PureModelProcessParameter(this.packagePrefix), null);
    }

    private CompilerExtensions getExtensions()
    {
        if (this.extensions != null)
        {
            return this.extensions;
        }
        Iterable<? extends CompilerExtension> exts = (this.classLoader == null) ? ServiceLoader.load(CompilerExtension.class) : ServiceLoader.load(CompilerExtension.class, this.classLoader);
        return CompilerExtensions.fromExtensions(exts);
    }

    public static PureModelBuilder newBuilder()
    {
        return newBuilder(null);
    }

    public static PureModelBuilder newBuilder(EntityToPureConverter converter)
    {
        return new PureModelBuilder(converter);
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
