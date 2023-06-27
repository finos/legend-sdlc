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

package org.finos.legend.sdlc.server.backend.simple.domain.model.project.workspace;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.server.backend.simple.api.entity.SimpleBackendEntityAccessContext;
import org.finos.legend.sdlc.server.backend.simple.api.entity.SimpleBackendEntityModificationContext;
import org.finos.legend.sdlc.server.backend.simple.api.revision.SimpleBackendRevisionAccessContext;
import org.finos.legend.sdlc.server.backend.simple.domain.model.revision.SimpleBackendRevision;
import org.finos.legend.sdlc.server.domain.api.entity.EntityModificationContext;
import org.finos.legend.sdlc.server.domain.api.revision.RevisionAccessContext;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SimpleBackendProjectWorkspace implements Workspace
{
    private final SimpleBackendRevision revision;
    private String projectId;
    private String workspaceId;
    private String userId;

    @JsonIgnore
    private MutableMap<String, Entity> entities = Maps.mutable.empty();

    @JsonIgnore
    private SimpleBackendEntityModificationContext entityModificationContext;

    @JsonIgnore
    private RevisionAccessContext revisionAccessContext;

    @JsonIgnore
    private SimpleBackendEntityAccessContext entityAccessContext;

    public SimpleBackendProjectWorkspace(String projectId, String workspaceId, String userId, ImmutableMap<String, Entity> entities)
    {
        this.projectId = projectId;
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.entities.putAll(entities.castToMap());
        this.revision  = new SimpleBackendRevision(this.entities);
        this.revisionAccessContext = new SimpleBackendRevisionAccessContext(this.revision);
        this.entityAccessContext = new SimpleBackendEntityAccessContext(this.revision);
        this.entityModificationContext = new SimpleBackendEntityModificationContext(this.revision);
    }

    public SimpleBackendProjectWorkspace(String projectId, String workspaceId, ImmutableMap<String, Entity> entities)
    {
        this(projectId, workspaceId, null, entities);
    }

    @Override
    public String getProjectId()
    {
        return projectId;
    }

    @Override
    public String getWorkspaceId()
    {
        return workspaceId;
    }

    @Override
    public String getUserId()
    {
        return userId;
    }

    public SimpleBackendEntityAccessContext getEntityAccessContext()
    {
        return entityAccessContext;
    }

    public void setEntityAccessContext(SimpleBackendEntityAccessContext entityAccessContext)
    {
        this.entityAccessContext = entityAccessContext;
    }

    public RevisionAccessContext getRevisionAccessContext()
    {
        return this.revisionAccessContext;
    }

    public EntityModificationContext getEntityModificationContext()
    {
        return this.entityModificationContext;
    }
}
