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
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

public class TestWorkspacesResource extends AbstractLegendSDLCServerResourceTest
{
    @Test
    public void testGetMixedTypeWorkspaces() throws HttpResponseException
    {
        String projectId = "A";
        String workspaceOneId = "w1";
        String workspaceTwoId = "w2";
        String workspaceThreeId = "w3";

        this.backend.project(projectId).addWorkspace(workspaceOneId, WorkspaceType.USER);
        this.backend.project(projectId).addWorkspace(workspaceTwoId, WorkspaceType.USER);
        this.backend.project(projectId).addWorkspace(workspaceThreeId, WorkspaceType.GROUP);

        List<Workspace> allUserWorkspaces = this.requestHelperFor("/api/projects/A/workspaces")
                .withAcceptableResponseStatuses(Collections.singleton(Response.Status.Family.SUCCESSFUL))
                .getTyped();

        Assert.assertNotNull(allUserWorkspaces);
        Assert.assertEquals(2, allUserWorkspaces.size());
        Assert.assertEquals(workspaceOneId, findWorkspace(allUserWorkspaces, workspaceOneId).getWorkspaceId());
        Assert.assertEquals(projectId, findWorkspace(allUserWorkspaces, workspaceOneId).getProjectId());
        Assert.assertEquals(workspaceTwoId, findWorkspace(allUserWorkspaces, workspaceTwoId).getWorkspaceId());
        Assert.assertEquals(projectId, findWorkspace(allUserWorkspaces, workspaceTwoId).getProjectId());

        List<Workspace> allGroupWorkspaces = this.requestHelperFor("/api/projects/A/groupWorkspaces")
                .withAcceptableResponseStatuses(Collections.singleton(Response.Status.Family.SUCCESSFUL))
                .getTyped();


        Assert.assertNotNull(allGroupWorkspaces);
        Assert.assertEquals(1, allGroupWorkspaces.size());
        Assert.assertEquals(workspaceThreeId, findWorkspace(allGroupWorkspaces, workspaceThreeId).getWorkspaceId());
        Assert.assertEquals(projectId, findWorkspace(allGroupWorkspaces, workspaceThreeId).getProjectId());

        Response responseThree = this.clientFor("/api/projects/A/groupWorkspaces").queryParam("includeUserWorkspaces", true).request().get();

        if (responseThree.getStatus() != 200)
        {
            throw new HttpResponseException(responseThree.getStatus(), "Error during getting all workspaces with status: " + responseThree.getStatus() + " , entity: " + responseThree.readEntity(String.class));
        }

        List<Workspace> allUserAndGroupWorkspaces = responseThree.readEntity(new GenericType<List<Workspace>>()
        {
        });

        Assert.assertNotNull(allUserAndGroupWorkspaces);
        Assert.assertEquals(3, allUserAndGroupWorkspaces.size());
        Assert.assertEquals(workspaceOneId, findWorkspace(allUserAndGroupWorkspaces, workspaceOneId).getWorkspaceId());
        Assert.assertEquals(projectId, findWorkspace(allUserAndGroupWorkspaces, workspaceOneId).getProjectId());
        Assert.assertEquals(workspaceTwoId, findWorkspace(allUserAndGroupWorkspaces, workspaceTwoId).getWorkspaceId());
        Assert.assertEquals(projectId, findWorkspace(allUserAndGroupWorkspaces, workspaceTwoId).getProjectId());
        Assert.assertEquals(workspaceThreeId, findWorkspace(allUserAndGroupWorkspaces, workspaceThreeId).getWorkspaceId());
        Assert.assertEquals(projectId, findWorkspace(allUserAndGroupWorkspaces, workspaceThreeId).getProjectId());
    }

    @Test
    public void testGetAndDeleteUserWorkspace() throws HttpResponseException
    {
        String projectId = "A";
        String workspaceId = "userw1";

        this.backend.project(projectId).addWorkspace(workspaceId, WorkspaceType.USER);

        Workspace workspace = this.requestHelperFor("/api/projects/A/workspaces/userw1")
                .withAcceptableResponseStatuses(Collections.singleton(Response.Status.Family.SUCCESSFUL))
                .getTyped();

        Assert.assertNotNull(workspace);
        Assert.assertEquals(workspaceId, workspace.getWorkspaceId());
        Assert.assertEquals(projectId, workspace.getProjectId());

        Response deletionResponse = this.clientFor("/api/projects/A/workspaces/userw1").request().delete();

        Assert.assertEquals("Error during deleting user workspace with status: " + deletionResponse.getStatus() + ", entity: " + deletionResponse.readEntity(String.class), 204, deletionResponse.getStatus());

        Response finalGetResponse = this.clientFor("/api/projects/A/workspaces/userw1").request().get();

        Assert.assertEquals("Error during getting deleted user workspace with status: " + finalGetResponse.getStatus() + ", entity: " + finalGetResponse.readEntity(String.class), 204, finalGetResponse.getStatus());
    }

    @Test
    public void testGetAndDeleteGroupWorkspace() throws HttpResponseException
    {
        String projectId = "A";
        String workspaceId = "groupw1";

        this.backend.project(projectId).addWorkspace(workspaceId, WorkspaceType.GROUP);

        Workspace workspace = this.requestHelperFor("/api/projects/A/groupWorkspaces/groupw1")
                .withAcceptableResponseStatuses(Collections.singleton(Response.Status.Family.SUCCESSFUL))
                .getTyped();

        Assert.assertNotNull(workspace);
        Assert.assertEquals(workspaceId, workspace.getWorkspaceId());
        Assert.assertEquals(projectId, workspace.getProjectId());

        Response deletionResponse = this.clientFor("/api/projects/A/groupWorkspaces/groupw1").request().delete();

        Assert.assertEquals("Error during deleting group workspace with status: " + deletionResponse.getStatus() + ", entity: " + deletionResponse.readEntity(String.class), 204, deletionResponse.getStatus());

        Response finalGetResponse = this.clientFor("/api/projects/A/groupWorkspaces/groupw1").request().get();

        Assert.assertEquals("Error during getting deleted group workspace with status: " + finalGetResponse.getStatus() + ", entity: " + finalGetResponse.readEntity(String.class), 204, finalGetResponse.getStatus());
    }

    private Workspace findWorkspace(List<Workspace> workspaces, String workspaceId)
    {
        return workspaces.stream().filter(workspace -> workspace.getWorkspaceId().equals(workspaceId)).findFirst().get();
    }
}
