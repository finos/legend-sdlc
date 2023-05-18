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
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryEntity;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
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

        Response responseOne = this.clientFor("/api/projects/A/workspaces/revisionw1").request().get();

        if (responseOne.getStatus() != 200)
        {
            throw new HttpResponseException(responseOne.getStatus(), "Error during getting user workspace with status: " + responseOne.getStatus() + ", entity: " + responseOne.readEntity(String.class));
        }

        Workspace workspace = responseOne.readEntity(new GenericType<Workspace>()
        {
        });

        Assert.assertNotNull(workspace);
        Assert.assertEquals(workspaceOneId, workspace.getWorkspaceId());
        Assert.assertEquals(projectId, workspace.getProjectId());

        Response responseThree = this.clientFor("/api/projects/A/workspaces/revisionw1/revisions").request().get();

        if (responseThree.getStatus() != 200)
        {
            throw new HttpResponseException(responseThree.getStatus(), "Error during getting revisions in user workspace with status: " + responseThree.getStatus() + ", entity: " + responseThree.readEntity(String.class));
        }

        List<Revision> revisions = responseThree.readEntity(new GenericType<List<Revision>>()
        {
        });

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

        Response responseOne = this.clientFor("/api/projects/A/groupWorkspaces/revisionw2").request().get();

        if (responseOne.getStatus() != 200)
        {
            throw new HttpResponseException(responseOne.getStatus(), "Error during getting group workspace with status: " + responseOne.getStatus() + ", entity: " + responseOne.readEntity(String.class));
        }

        Workspace workspace = responseOne.readEntity(new GenericType<Workspace>()
        {
        });

        Assert.assertNotNull(workspace);
        Assert.assertEquals(workspaceOneId, workspace.getWorkspaceId());
        Assert.assertEquals(projectId, workspace.getProjectId());

        Response responseThree = this.clientFor("/api/projects/A/groupWorkspaces/revisionw2/revisions").request().get();

        if (responseThree.getStatus() != 200)
        {
            throw new HttpResponseException(responseThree.getStatus(), "Error during getting revisions in group workspace with status: " + responseThree.getStatus() + ", entity: " + responseThree.readEntity(String.class));
        }

        List<Revision> revisions = responseThree.readEntity(new GenericType<List<Revision>>()
        {
        });

        Assert.assertNotNull(revisions);
        Assert.assertEquals(1, revisions.size());
        Assert.assertNotNull(revisions.get(0).getId());
    }

    @Test
    public void testGetUserWorkspaceRevisionsForPatchReleaseVersion() throws HttpResponseException
    {
        String projectId = "A";
        String workspaceOneId = "revisionw1";
        String entityOneName = "testentityone";
        String entityTwoName = "testentitytwo";
        String entityPackageName = "testpkg";
        VersionId patchReleaseVersionId = VersionId.parseVersionId("1.0.1");

        this.backend.project(projectId).addVersionedEntities("1.0.0", InMemoryEntity.newEntity(entityOneName, entityPackageName), InMemoryEntity.newEntity(entityTwoName, entityPackageName));
        this.backend.project(projectId).addEntities(workspaceOneId, Arrays.asList(InMemoryEntity.newEntity(entityOneName, entityPackageName), InMemoryEntity.newEntity(entityTwoName, entityPackageName)), patchReleaseVersionId);

        Response responseOne = this.clientFor("/api/projects/A/patches/1.0.1/workspaces/revisionw1").request().get();

        if (responseOne.getStatus() != 200)
        {
            throw new HttpResponseException(responseOne.getStatus(), "Error during getting user workspace with status: " + responseOne.getStatus() + ", entity: " + responseOne.readEntity(String.class));
        }

        Workspace workspace = responseOne.readEntity(new GenericType<Workspace>()
        {
        });

        Assert.assertNotNull(workspace);
        Assert.assertEquals(workspaceOneId, workspace.getWorkspaceId());
        Assert.assertEquals(projectId, workspace.getProjectId());

        Response responseThree = this.clientFor("/api/projects/A/patches/1.0.1/workspaces/revisionw1/revisions").request().get();

        if (responseThree.getStatus() != 200)
        {
            throw new HttpResponseException(responseThree.getStatus(), "Error during getting revisions in user workspace with status: " + responseThree.getStatus() + ", entity: " + responseThree.readEntity(String.class));
        }

        List<Revision> revisions = responseThree.readEntity(new GenericType<List<Revision>>()
        {
        });

        Assert.assertNotNull(revisions);
        Assert.assertEquals(1, revisions.size());
        Assert.assertNotNull(revisions.get(0).getId());
    }

    @Test
    public void testGetGroupWorkspaceRevisionsForPatchReleaseVersion() throws HttpResponseException
    {
        String projectId = "A";
        String workspaceOneId = "revisionw2";
        String entityOneName = "testentityone";
        String entityTwoName = "testentitytwo";
        String entityPackageName = "testpkg";
        VersionId patchReleaseVersionId = VersionId.parseVersionId("1.0.1");

        this.backend.project(projectId).addVersionedEntities("1.0.0", InMemoryEntity.newEntity(entityOneName, entityPackageName), InMemoryEntity.newEntity(entityTwoName, entityPackageName));
        this.backend.project(projectId).addEntities(workspaceOneId, WorkspaceType.GROUP, Arrays.asList(InMemoryEntity.newEntity(entityOneName, entityPackageName), InMemoryEntity.newEntity(entityTwoName, entityPackageName)), patchReleaseVersionId);

        Response responseOne = this.clientFor("/api/projects/A/patches/1.0.1/groupWorkspaces/revisionw2").request().get();

        if (responseOne.getStatus() != 200)
        {
            throw new HttpResponseException(responseOne.getStatus(), "Error during getting group workspace with status: " + responseOne.getStatus() + ", entity: " + responseOne.readEntity(String.class));
        }

        Workspace workspace = responseOne.readEntity(new GenericType<Workspace>()
        {
        });

        Assert.assertNotNull(workspace);
        Assert.assertEquals(workspaceOneId, workspace.getWorkspaceId());
        Assert.assertEquals(projectId, workspace.getProjectId());

        Response responseThree = this.clientFor("/api/projects/A/patches/1.0.1/groupWorkspaces/revisionw2/revisions").request().get();

        if (responseThree.getStatus() != 200)
        {
            throw new HttpResponseException(responseThree.getStatus(), "Error during getting revisions in group workspace with status: " + responseThree.getStatus() + ", entity: " + responseThree.readEntity(String.class));
        }

        List<Revision> revisions = responseThree.readEntity(new GenericType<List<Revision>>()
        {
        });

        Assert.assertNotNull(revisions);
        Assert.assertEquals(1, revisions.size());
        Assert.assertNotNull(revisions.get(0).getId());
    }

    private Entity findEntity(List<Entity> entities, String entityName, String entityPackageName)
    {
        return entities.stream().filter(entity -> entity.getContent().get("name").equals(entityName) && entity.getContent().get("package").equals(entityPackageName)).findFirst().get();
    }
}
