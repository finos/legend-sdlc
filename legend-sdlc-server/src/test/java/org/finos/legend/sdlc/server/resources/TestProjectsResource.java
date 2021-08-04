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

package org.finos.legend.sdlc.server.resources;

import org.apache.http.client.HttpResponseException;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

public class TestProjectsResource extends AbstractLegendSDLCServerResourceTest
{
    @Test
    public void testGetAllProjects() throws HttpResponseException
    {
        this.backend.project("A").addVersionedClasses("1.0.0", "a1", "a2");
        this.backend.project("B").addVersionedClasses("1.0.0", "b1", "b2");
        this.backend.project("C").addVersionedClasses("1.0.0", "c1", "c2");
        this.backend.project("B").addDependency("C:1.0.0");

        Response response = this.clientFor("/api/projects").request().get();

        if (response.getStatus() != 200)
        {
            throw new HttpResponseException(response.getStatus(), "Error during http call with status: " + response.getStatus() + " , entity: " + response.readEntity(String.class));
        }

        List<Project> projects = response.readEntity(new GenericType<List<Project>>()
        {
        });

        Assert.assertEquals(3, projects.size());
        Assert.assertEquals("project-A", findProject(projects, "A").getName());
        Assert.assertEquals("project-B", findProject(projects, "B").getName());
        Assert.assertEquals("project-C", findProject(projects, "C").getName());
    }

    private Project findProject(List<Project> projects, String projectId)
    {
        return projects.stream().filter(project -> project.getProjectId().equals(projectId)).findFirst().get();
    }
}
