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
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

public class TestWorkspacesResource extends AbstractLegendSDLCServerResourceTest
{
    @Test
    public void testGetMixedTypeWorkspaces() throws HttpResponseException
    {
        this.backend.project("A").addWorkspace("w1", false);
        this.backend.project("A").addWorkspace("w2", false);
        this.backend.project("A").addWorkspace("w3", true);

        Response responseOne = this.clientFor("/api/projects/A/workspaces").request().get();

        if (responseOne.getStatus() != 200)
        {
            throw new HttpResponseException(responseOne.getStatus(), "Error during http call with status: " + responseOne.getStatus() + " , entity: " + responseOne.readEntity(String.class));
        }

        List<Workspace> allUserWorkspaces = responseOne.readEntity(new GenericType<List<Workspace>>()
        {
        });

        Assert.assertEquals(2, allUserWorkspaces.size());
        Assert.assertEquals("w1", findWorkspace(allUserWorkspaces, "w1").getWorkspaceId());
        Assert.assertEquals("A", findWorkspace(allUserWorkspaces, "w1").getProjectId());
        Assert.assertEquals("w2", findWorkspace(allUserWorkspaces, "w2").getWorkspaceId());
        Assert.assertEquals("A", findWorkspace(allUserWorkspaces, "w2").getProjectId());

        Response responseTwo = this.clientFor("/api/projects/A/groupWorkspaces").request().get();

        if (responseTwo.getStatus() != 200)
        {
            throw new HttpResponseException(responseTwo.getStatus(), "Error during http call with status: " + responseTwo.getStatus() + " , entity: " + responseTwo.readEntity(String.class));
        }

        List<Workspace> allGroupWorkspaces = responseTwo.readEntity(new GenericType<List<Workspace>>()
        {
        });

        Assert.assertEquals(1, allGroupWorkspaces.size());
        Assert.assertEquals("w3", findWorkspace(allGroupWorkspaces, "w3").getWorkspaceId());
        Assert.assertEquals("A", findWorkspace(allGroupWorkspaces, "w3").getProjectId());

        Response responseThree = this.clientFor("/api/projects/A/groupWorkspaces").queryParam("includeUserWorkspaces", true).request().get();

        if (responseThree.getStatus() != 200)
        {
            throw new HttpResponseException(responseThree.getStatus(), "Error during http call with status: " + responseThree.getStatus() + " , entity: " + responseThree.readEntity(String.class));
        }

        List<Workspace> allUserAndGroupWorkspaces = responseThree.readEntity(new GenericType<List<Workspace>>()
        {
        });

        Assert.assertEquals(3, allUserAndGroupWorkspaces.size());
        Assert.assertEquals("w1", findWorkspace(allUserAndGroupWorkspaces, "w1").getWorkspaceId());
        Assert.assertEquals("A", findWorkspace(allUserAndGroupWorkspaces, "w1").getProjectId());
        Assert.assertEquals("w2", findWorkspace(allUserAndGroupWorkspaces, "w2").getWorkspaceId());
        Assert.assertEquals("A", findWorkspace(allUserAndGroupWorkspaces, "w2").getProjectId());
        Assert.assertEquals("w3", findWorkspace(allUserAndGroupWorkspaces, "w3").getWorkspaceId());
        Assert.assertEquals("A", findWorkspace(allUserAndGroupWorkspaces, "w3").getProjectId());
    }

    private Workspace findWorkspace(List<Workspace> workspaces, String workspaceId)
    {
        return workspaces.stream().filter(workspace -> workspace.getWorkspaceId().equals(workspaceId)).findFirst().get();
    }
}
