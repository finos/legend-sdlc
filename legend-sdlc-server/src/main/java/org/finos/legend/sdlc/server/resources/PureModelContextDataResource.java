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

package org.finos.legend.sdlc.server.resources;

import org.finos.legend.engine.protocol.pure.PureClientVersions;
import org.finos.legend.engine.protocol.pure.v1.model.context.AlloySDLC;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.protocol.pure.v1.PureModelContextDataBuilder;
import org.finos.legend.sdlc.server.domain.api.entity.EntityAccessContext;

public abstract class PureModelContextDataResource extends BaseResource
{
    protected PureModelContextData getPureModelContextData(String projectId, String revisionOrVersionId, EntityAccessContext entityAccessContext)
    {
        return getPureModelContextData(projectId, revisionOrVersionId, entityAccessContext.getEntities(null, null, null));
    }

    protected PureModelContextData getPureModelContextData(String projectId, String revisionOrVersionId, Iterable<? extends Entity> entities)
    {
        AlloySDLC sdlc = new AlloySDLC();
        sdlc.project = projectId;
        sdlc.baseVersion = revisionOrVersionId;
        return PureModelContextDataBuilder.newBuilder()
                .withProtocol("pure", PureClientVersions.production)
                .withSDLC(sdlc)
                .withEntitiesIfPossible(entities)
                .build();
    }
}
