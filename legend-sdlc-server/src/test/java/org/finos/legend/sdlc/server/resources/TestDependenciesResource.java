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

import org.finos.legend.sdlc.domain.model.TestTools;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.dependency.ProjectRevision;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

public class TestDependenciesResource extends AbstractLegendSDLCServerResourceTest
{
    @Before
    public void setup()
    {
        this.backend.reinitialize();
    }

    @Test
    public void testGetDownstreamDependencies()
    {
        this.backend.project("A").addVersionedClasses("1.0.0", "a1", "a2");
        this.backend.project("B").addVersionedClasses("1.0.0", "b1", "b2");
        this.backend.project("B").addDependency("A:1.0.0");

        Response response = this.clientFor("/api/projects/A/downstreamProjects").request().get();
        Set<ProjectRevision> dependencies = readProjectRevision(response);

        Assert.assertEquals(1, dependencies.size());
        Assert.assertNotNull(findProjectRevision(dependencies, "B"));
    }

    @Test
    public void testGetProjectRevisionUpstreamDependencies()
    {
        this.backend.project("A").addVersionedClasses("1.0.0", "a1", "a2");
        this.backend.project("B").addVersionedClasses("1.0.0", "b1", "b2");
        this.backend.project("C").addVersionedClasses("1.0.0", "c1", "c2");
        this.backend.project("A").addDependency("C:1.0.0");
        this.backend.project("B").addDependency("A:1.0.0");
        String projectBRevisionId = this.backend.getRevisionApi().getProjectRevisionContext("B").getCurrentRevision().getId();

        // B directly depends on A
        String url = String.format("/api/projects/B/revisions/%s/upstreamProjects", projectBRevisionId);
        Set<ProjectDependency> dependencies = readProjectDependencies(this.clientFor(url).request().get());

        Assert.assertEquals(1, dependencies.size());
        Assert.assertEquals("1.0.0", findDependency(dependencies, "A").getVersionId());

        // B transitively depends on A and C
        Set<ProjectDependency> transitiveDependencies = readProjectDependencies(this.clientFor(url).queryParam("transitive", "true").request().get());

        Assert.assertEquals(2, transitiveDependencies.size());
        Assert.assertEquals("1.0.0", findDependency(transitiveDependencies, "A").getVersionId());
        Assert.assertEquals("1.0.0", findDependency(transitiveDependencies, "C").getVersionId());
    }

    @Test
    public void testGetProjectVersionUpstreamDependencies()
    {
        this.backend.project("A").addVersionedClasses("1.0.0", "a1", "a2");

        // project A has no upstream dependencies
        String url = "/api/projects/A/versions/1.0.0/upstreamProjects";
        Set<ProjectDependency> dependencies = readProjectDependencies(this.clientFor(url).request().get());
        Assert.assertEquals(Collections.emptySet(), dependencies);

        Set<ProjectDependency> transitiveDependencies = readProjectDependencies(this.clientFor(url).queryParam("transitive", "true").request().get());
        Assert.assertEquals(Collections.emptySet(), transitiveDependencies);
    }

    @Test
    public void testGetWorkspaceRevisionUpstreamDependencies()
    {
        this.backend.project("A").addVersionedClasses("1.0.0", "a1", "a2");
        this.backend.project("B").addVersionedClasses("1.0.0", "b1", "b2");
        this.backend.project("C").addVersionedClasses("1.0.0", "c1", "c2");
        this.backend.project("A").addDependency("C:1.0.0");
        this.backend.project("B").addDependency("A:1.0.0");

        this.backend.project("B").addEntities("w1", TestTools.newClassEntity("b3", "B"));

        String workspace1CurrentRevision = this.backend.getRevisionApi().getUserWorkspaceRevisionContext("B", "w1").getCurrentRevision().getId();

        // B directly depends on A
        String url = String.format("/api/projects/B/workspaces/w1/revisions/%s/upstreamProjects", workspace1CurrentRevision);
        Set<ProjectDependency> dependencies = readProjectDependencies(this.clientFor(url).request().get());

        Assert.assertEquals(1, dependencies.size());
        Assert.assertEquals("1.0.0", findDependency(dependencies, "A").getVersionId());
    }

    @Test
    public void testGetProjectRevisionUpstreamDependenciesForPatchReleaseVersion()
    {
        this.backend.project("A").addVersionedClasses("1.0.0", "a1", "a2");
        this.backend.project("B").addVersionedClasses("1.0.0", "b1", "b2");
        this.backend.project("C").addVersionedClasses("1.0.0", "c1", "c2");
        this.backend.project("A").addDependency("C:1.0.0");
        this.backend.project("B").addDependency("A:1.0.0");
        this.backend.project("B").addPatch(VersionId.parseVersionId("1.0.1"));
        String patchRevisionId = this.backend.getRevisionApi().getProjectRevisionContext("B", VersionId.parseVersionId("1.0.1")).getCurrentRevision().getId();

        // B directly depends on A
        String url = String.format("/api/projects/B/patches/1.0.1/revisions/%s/upstreamProjects", patchRevisionId);
        Set<ProjectDependency> dependencies = readProjectDependencies(this.clientFor(url).request().get());

        Assert.assertEquals(1, dependencies.size());
        Assert.assertEquals("1.0.0", findDependency(dependencies, "A").getVersionId());

        // B transitively depends on A and C
        Set<ProjectDependency> transitiveDependencies = readProjectDependencies(this.clientFor(url).queryParam("transitive", "true").request().get());

        Assert.assertEquals(2, transitiveDependencies.size());
        Assert.assertEquals("1.0.0", findDependency(transitiveDependencies, "A").getVersionId());
        Assert.assertEquals("1.0.0", findDependency(transitiveDependencies, "C").getVersionId());
    }

    @Test
    public void testGetWorkspaceRevisionUpstreamDependenciesForPatchReleaseVersion()
    {
        this.backend.project("A").addVersionedClasses("1.0.0", "a1", "a2");
        this.backend.project("B").addVersionedClasses("1.0.0", "b1", "b2");
        this.backend.project("C").addVersionedClasses("1.0.0", "c1", "c2");
        this.backend.project("A").addDependency("C:1.0.0");
        this.backend.project("B").addDependency("A:1.0.0");

        this.backend.project("B").addEntities("w1", Arrays.asList(TestTools.newClassEntity("b3", "B")), VersionId.parseVersionId("1.0.1"));

        String workspace1CurrentRevision = this.backend.getRevisionApi().getWorkspaceRevisionContext("B", SourceSpecification.newUserWorkspaceSourceSpecification("w1", VersionId.parseVersionId("1.0.1"))).getCurrentRevision().getId();

        // B directly depends on A
        String url = String.format("/api/projects/B/patches/1.0.1/workspaces/w1/revisions/%s/upstreamProjects", workspace1CurrentRevision);
        Set<ProjectDependency> dependencies = readProjectDependencies(this.clientFor(url).request().get());

        Assert.assertEquals(1, dependencies.size());
        Assert.assertEquals("1.0.0", findDependency(dependencies, "A").getVersionId());
    }

    private ProjectDependency findDependency(Set<ProjectDependency> dependencies, String projectId)
    {
        return dependencies.stream().filter(d -> d.getProjectId().equals(projectId)).findFirst().get();
    }

    private Set<ProjectDependency> readProjectDependencies(Response response)
    {
        Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        return response.readEntity(new GenericType<Set<ProjectDependency>>()
        {
        });
    }

    private ProjectRevision findProjectRevision(Set<ProjectRevision> projectRevisions, String projectId)
    {
        return projectRevisions.stream().filter(pr -> pr.getProjectId().equals(projectId)).findFirst().get();
    }

    private Set<ProjectRevision> readProjectRevision(Response response)
    {
        Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        return response.readEntity(new GenericType<Set<ProjectRevision>>()
        {
        });
    }
}
