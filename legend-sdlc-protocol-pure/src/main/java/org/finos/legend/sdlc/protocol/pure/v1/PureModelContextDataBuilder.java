// Copyright 2020 Goldman Sachs
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

package org.finos.legend.sdlc.protocol.pure.v1;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.utility.ArrayIterate;
import org.finos.legend.engine.protocol.Protocol;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextPointer;
import org.finos.legend.engine.protocol.pure.v1.model.context.SDLC;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.sdlc.domain.model.entity.Entity;

import java.util.Optional;
import java.util.stream.Stream;

public class PureModelContextDataBuilder
{
    private final EntityToPureConverter converter;
    private final MutableList<PackageableElement> elements = Lists.mutable.empty();
    private Protocol protocol;
    private SDLC sdlc;

    private PureModelContextDataBuilder(EntityToPureConverter converter)
    {
        this.converter = converter;
    }

    public int getElementCount()
    {
        return this.elements.size();
    }

    public void addPackageableElement(PackageableElement element)
    {
        this.elements.add(element);
    }

    public PureModelContextDataBuilder withPackageableElement(PackageableElement element)
    {
        addPackageableElement(element);
        return this;
    }

    public void addEntity(Entity entity)
    {
        addPackageableElement(this.converter.fromEntity(entity));
    }

    public PureModelContextDataBuilder withEntity(Entity entity)
    {
        addEntity(entity);
        return this;
    }

    public void addEntities(Stream<? extends Entity> entities)
    {
        entities.forEach(this::addEntity);
    }

    public PureModelContextDataBuilder withEntities(Stream<? extends Entity> entities)
    {
        addEntities(entities);
        return this;
    }

    public void addEntities(Iterable<? extends Entity> entities)
    {
        entities.forEach(this::addEntity);
    }

    public PureModelContextDataBuilder withEntities(Iterable<? extends Entity> entities)
    {
        addEntities(entities);
        return this;
    }

    public void addEntities(Entity... entities)
    {
        ArrayIterate.forEach(entities, this::addEntity);
    }

    public PureModelContextDataBuilder withEntities(Entity... entities)
    {
        addEntities(entities);
        return this;
    }

    public boolean addEntityIfPossible(Entity entity)
    {
        Optional<PackageableElement> element = this.converter.fromEntityIfPossible(entity);
        element.ifPresent(this::addPackageableElement);
        return element.isPresent();
    }

    public PureModelContextDataBuilder withEntityIfPossible(Entity entity)
    {
        addEntityIfPossible(entity);
        return this;
    }

    public void addEntitiesIfPossible(Stream<? extends Entity> entities)
    {
        entities.forEach(this::addEntityIfPossible);
    }

    public PureModelContextDataBuilder withEntitiesIfPossible(Stream<? extends Entity> entities)
    {
        addEntitiesIfPossible(entities);
        return this;
    }

    public void addEntitiesIfPossible(Iterable<? extends Entity> entities)
    {
        entities.forEach(this::addEntityIfPossible);
    }

    public PureModelContextDataBuilder withEntitiesIfPossible(Iterable<? extends Entity> entities)
    {
        addEntitiesIfPossible(entities);
        return this;
    }

    public void addEntitiesIfPossible(Entity... entities)
    {
        ArrayIterate.forEach(entities, this::addEntityIfPossible);
    }

    public PureModelContextDataBuilder withEntitiesIfPossible(Entity... entities)
    {
        addEntitiesIfPossible(entities);
        return this;
    }

    public void setProtocol(Protocol protocol)
    {
        this.protocol = protocol;
    }

    public PureModelContextDataBuilder withProtocol(Protocol protocol)
    {
        setProtocol(protocol);
        return this;
    }

    public PureModelContextDataBuilder withProtocol(String name, String version)
    {
        return withProtocol(new Protocol(name, version));
    }

    public void setSDLC(SDLC sdlc)
    {
        this.sdlc = sdlc;
    }

    public PureModelContextDataBuilder withSDLC(SDLC sdlc)
    {
        setSDLC(sdlc);
        return this;
    }

    public PureModelContextData build()
    {
        PureModelContextData.Builder builder = PureModelContextData.newBuilder();
        builder.addElements(this.elements);
        if (this.protocol != null)
        {
            builder.setSerializer(this.protocol);
        }
        if ((this.protocol != null) || (this.sdlc != null))
        {
            PureModelContextPointer origin = new PureModelContextPointer();
            origin.serializer = this.protocol;
            origin.sdlcInfo = this.sdlc;
            builder.setOrigin(origin);
        }
        return builder.build();
    }

    public static PureModelContextDataBuilder newBuilder()
    {
        return newBuilder(new EntityToPureConverter());
    }

    public static PureModelContextDataBuilder newBuilder(EntityToPureConverter converter)
    {
        return new PureModelContextDataBuilder(converter);
    }
}
