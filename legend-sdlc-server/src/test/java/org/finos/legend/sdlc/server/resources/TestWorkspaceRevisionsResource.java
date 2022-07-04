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

package org.finos.legend.sdlc.server.resources;

import org.apache.http.client.HttpResponseException;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryEntity;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

public class TestWorkspaceRevisionsResource extends AbstractLegendSDLCServerResourceTest
{
    @Test
    public void testGetUserWorkspaceRevisions() throws HttpResponseException
    {
        String projectId = "A";
        String workspaceOneId = "revisionw1";
        String entityOneName = "testentityone";
        String entityTwoName = "testentitytwo";
        String entityPackageName = "testpkg";

        this.backend.project(projectId).addEntities(workspaceOneId, InMemoryEntity.newEntity(entityOneName, entityPackageName), InMemoryEntity.newEntity(entityTwoName, entityPackageName));

        Workspace workspace = this.requestHelperFor("/api/projects/A/workspaces/revisionw1")
                .withAcceptableResponseStatuses(Collections.singleton(Response.Status.Family.SUCCESSFUL))
                .getTyped();

        Assert.assertNotNull(workspace);
        Assert.assertEquals(workspaceOneId, workspace.getWorkspaceId());
        Assert.assertEquals(projectId, workspace.getProjectId());

        List<Revision> revisions = this.requestHelperFor("/api/projects/A/workspaces/revisionw1/revisions")
                .withAcceptableResponseStatuses(Collections.singleton(Response.Status.Family.SUCCESSFUL))
                .getTyped();

        Assert.assertNotNull(revisions);
        Assert.assertEquals(1, revisions.size());
        Assert.assertNotNull(revisions.get(0).getId());
    }

    @Test
    public void testGetGroupWorkspaceRevisions() throws HttpResponseException
    {
        String projectId = "A";
        String workspaceOneId = "revisionw2";
        String entityOneName = "testentityone";
        String entityTwoName = "testentitytwo";
        String entityPackageName = "testpkg";

        this.backend.project(projectId).addEntities(workspaceOneId, WorkspaceType.GROUP, InMemoryEntity.newEntity(entityOneName, entityPackageName), InMemoryEntity.newEntity(entityTwoName, entityPackageName));

        Workspace workspace = this.requestHelperFor("/api/projects/A/groupWorkspaces/revisionw2")
                .withAcceptableResponseStatuses(Collections.singleton(Response.Status.Family.SUCCESSFUL))
                .getTyped();

        Assert.assertNotNull(workspace);
        Assert.assertEquals(workspaceOneId, workspace.getWorkspaceId());
        Assert.assertEquals(projectId, workspace.getProjectId());

        List<Revision> revisions = this.requestHelperFor("/api/projects/A/groupWorkspaces/revisionw2/revisions")
                .withAcceptableResponseStatuses(Collections.singleton(Response.Status.Family.SUCCESSFUL))
                .getTyped();

        Assert.assertNotNull(revisions);
        Assert.assertEquals(1, revisions.size());
        Assert.assertNotNull(revisions.get(0).getId());
    }

}
