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
import org.eclipse.collections.api.factory.Sets;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

public class TestProjectsResource extends AbstractLegendSDLCServerResourceTest
{
    @Before
    public void resetBackend()
    {
        this.backend.reinitialize();
    }

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

    @Test
    public void testGetProjectsFilteredByTag() throws HttpResponseException
    {
        this.backend.project("A").addTags("finance");
        this.backend.project("B").addTags("finance", "production");
        this.backend.project("C").addTags("sandbox");

        Set<String> ids = getProjectIds(this.clientFor("/api/projects").queryParam("tag", "finance").request());
        Assert.assertEquals(Sets.mutable.with("A", "B"), ids);
    }

    @Test
    public void testGetProjectsFilteredByMultipleTagsIsOrSemantics() throws HttpResponseException
    {
        this.backend.project("A").addTags("finance");
        this.backend.project("B").addTags("production");
        this.backend.project("C").addTags("sandbox");

        // tag filter is OR — projects matching any of the tags should be returned
        Set<String> ids = getProjectIds(this.clientFor("/api/projects")
                .queryParam("tag", "finance")
                .queryParam("tag", "sandbox")
                .request());
        Assert.assertEquals(Sets.mutable.with("A", "C"), ids);
    }

    @Test
    public void testGetProjectsFilteredByExcludeTag() throws HttpResponseException
    {
        this.backend.project("A").addTags("finance");
        this.backend.project("B").addTags("finance", "archived");
        this.backend.project("C").addTags("sandbox");

        Set<String> ids = getProjectIds(this.clientFor("/api/projects").queryParam("excludeTag", "archived").request());
        Assert.assertEquals(Sets.mutable.with("A", "C"), ids);
    }

    @Test
    public void testGetProjectsFilteredByMultipleExcludeTagsIsOrSemantics() throws HttpResponseException
    {
        this.backend.project("A").addTags("finance");
        this.backend.project("B").addTags("archived");
        this.backend.project("C").addTags("deprecated");
        this.backend.project("D").addTags("trading");

        // excludeTag filter is OR — projects with any of the excluded tags should be dropped
        Set<String> ids = getProjectIds(this.clientFor("/api/projects")
                .queryParam("excludeTag", "archived")
                .queryParam("excludeTag", "deprecated")
                .request());
        Assert.assertEquals(Sets.mutable.with("A", "D"), ids);
    }

    @Test
    public void testGetProjectsExcludeTagWinsOverIncludeTag() throws HttpResponseException
    {
        this.backend.project("A").addTags("finance");
        this.backend.project("B").addTags("finance", "archived");
        this.backend.project("C").addTags("finance", "deprecated");

        // include finance AND exclude archived: B is dropped despite matching include
        Set<String> ids = getProjectIds(this.clientFor("/api/projects")
                .queryParam("tag", "finance")
                .queryParam("excludeTag", "archived")
                .request());
        Assert.assertEquals(Sets.mutable.with("A", "C"), ids);
    }

    @Test
    public void testGetProjectsExcludeTagWithUntaggedProjects() throws HttpResponseException
    {
        this.backend.project("A").addTags("archived");
        this.backend.project("B"); // no tags
        this.backend.project("C").addTags("finance");

        // projects with no tags should not be excluded
        Set<String> ids = getProjectIds(this.clientFor("/api/projects").queryParam("excludeTag", "archived").request());
        Assert.assertEquals(Sets.mutable.with("B", "C"), ids);
    }

    private static Set<String> getProjectIds(Invocation.Builder request) throws HttpResponseException
    {
        Response response = request.get();
        if (response.getStatus() != 200)
        {
            throw new HttpResponseException(response.getStatus(), "Error during http call with status: " + response.getStatus() + " , entity: " + response.readEntity(String.class));
        }
        List<Project> projects = response.readEntity(new GenericType<List<Project>>()
        {
        });
        return projects.stream().map(Project::getProjectId).collect(Collectors.toSet());
    }

    private Project findProject(List<Project> projects, String projectId)
    {
        return projects.stream().filter(project -> project.getProjectId().equals(projectId)).findFirst().get();
    }
}
