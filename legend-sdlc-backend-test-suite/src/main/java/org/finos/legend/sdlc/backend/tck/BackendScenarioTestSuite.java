// Copyright 2026 Goldman Sachs
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

package org.finos.legend.sdlc.backend.tck;

import org.eclipse.collections.api.factory.Maps;
import org.finos.legend.sdlc.backend.api.entity.EntityApi;
import org.finos.legend.sdlc.backend.api.spi.Backend;
import org.finos.legend.sdlc.backend.api.spi.BackendSession;
import org.finos.legend.sdlc.core.project.ProjectConfigurationUpdater;
import org.finos.legend.sdlc.domain.model.comparison.Comparison;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChange;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.project.source.SourceSpecification;
import org.finos.legend.sdlc.project.workspace.WorkspaceSource;
import org.finos.legend.sdlc.project.workspace.WorkspaceSpecification;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * End-to-end scenarios over a real backend session: project creation, workspace lifecycle, entity round-trips,
 * configuration updates, comparison, and revision access — the parts of the SPI contract the capability suite
 * ({@link BackendContractTestSuite}, which this extends) cannot state. This is what certifies the L4 default
 * implementations against a backend's actual storage provider; a backend runs it by subclassing and supplying
 * its backend, exactly as for the capability suite.
 * <p>
 * The scenarios require only the minimal contract (project and user-workspace lifecycle); each test creates its
 * own uniquely-named project, so {@link #newBackend()} may return a shared instance.
 */
public abstract class BackendScenarioTestSuite extends BackendContractTestSuite
{
    private static final AtomicInteger PROJECT_COUNTER = new AtomicInteger();

    protected static final String GROUP_ID = "org.finos.legend.sdlc.test";

    protected BackendSession session;

    @Before
    public void setUpSession()
    {
        Backend backend = newBackend();
        this.session = backend.newSession(newSessionContext());
    }

    @Test
    public void testCreateProject()
    {
        Project project = createProject();
        String projectId = project.getProjectId();
        Assert.assertNotNull("project id", projectId);

        Assert.assertEquals(projectId, this.session.getProjectApi().getProject(projectId).getProjectId());
        Assert.assertTrue("a created project must report as configured",
                this.session.getProjectConfigurationApi().getProjectConfigurationStatus(projectId).isProjectConfigured());
        ProjectConfiguration configuration = this.session.getProjectConfigurationApi().getProjectConfiguration(projectId, SourceSpecification.projectSourceSpecification());
        Assert.assertEquals(GROUP_ID, configuration.getGroupId());
        Assert.assertEquals(projectId, configuration.getProjectId());
        Assert.assertEquals("a new project has no entities", Collections.emptyList(),
                this.session.getEntityApi().getEntityAccessContext(projectId, SourceSpecification.projectSourceSpecification()).getEntities(null, null, null));
    }

    @Test
    public void testWorkspaceLifecycle()
    {
        String projectId = createProject().getProjectId();
        Workspace workspace = this.session.getWorkspaceApi().newWorkspace(projectId, "scenario-workspace", WorkspaceType.USER, WorkspaceSource.projectWorkspaceSource());
        Assert.assertEquals("scenario-workspace", workspace.getWorkspaceId());
        Assert.assertEquals(projectId, workspace.getProjectId());

        WorkspaceSpecification workspaceSpec = WorkspaceSpecification.newWorkspaceSpecification("scenario-workspace", WorkspaceType.USER);
        Assert.assertEquals("scenario-workspace", this.session.getWorkspaceApi().getWorkspace(projectId, workspaceSpec).getWorkspaceId());
        List<Workspace> workspaces = this.session.getWorkspaceApi().getWorkspaces(projectId, null, null, null);
        Assert.assertEquals(1, workspaces.size());
        Assert.assertFalse("a fresh workspace is not outdated", this.session.getWorkspaceApi().isWorkspaceOutdated(projectId, workspaceSpec));

        this.session.getWorkspaceApi().deleteWorkspace(projectId, workspaceSpec);
        try
        {
            this.session.getWorkspaceApi().getWorkspace(projectId, workspaceSpec);
            Assert.fail("expected 404 for a deleted workspace");
        }
        catch (LegendSDLCException e)
        {
            Assert.assertEquals(404, e.getStatusCode());
        }
    }

    @Test
    public void testEntityRoundTrip()
    {
        String projectId = createProject().getProjectId();
        WorkspaceSpecification workspaceSpec = newWorkspace(projectId, "entity-workspace");
        EntityApi entityApi = this.session.getEntityApi();

        Entity entity = newClassEntity("model::domain::ScenarioClass");
        Revision revision = entityApi.getEntityModificationContext(projectId, workspaceSpec.getSourceSpecification())
                .performChanges(Collections.singletonList(EntityChange.newCreateEntity(entity.getPath(), entity.getClassifierPath(), entity.getContent())), null, "Add scenario entity");
        Assert.assertNotNull("creating an entity must produce a revision", revision);

        Entity read = entityApi.getEntityAccessContext(projectId, workspaceSpec.getSourceSpecification()).getEntity(entity.getPath());
        Assert.assertEquals(entity.getPath(), read.getPath());
        Assert.assertEquals(entity.getClassifierPath(), read.getClassifierPath());
        Assert.assertEquals(Collections.singletonList(entity.getPath()),
                entityApi.getEntityAccessContext(projectId, workspaceSpec.getSourceSpecification()).getEntityPaths(null, null, null));
        Assert.assertEquals("the workspace change must not be visible at the project source", Collections.emptyList(),
                entityApi.getEntityAccessContext(projectId, SourceSpecification.projectSourceSpecification()).getEntities(null, null, null));

        Revision deleteRevision = entityApi.getEntityModificationContext(projectId, workspaceSpec.getSourceSpecification())
                .performChanges(Collections.singletonList(EntityChange.newDeleteEntity(entity.getPath())), null, "Delete scenario entity");
        Assert.assertNotNull(deleteRevision);
        Assert.assertEquals(Collections.emptyList(),
                entityApi.getEntityAccessContext(projectId, workspaceSpec.getSourceSpecification()).getEntities(null, null, null));
    }

    @Test
    public void testConfigurationUpdateInWorkspace()
    {
        String projectId = createProject().getProjectId();
        WorkspaceSpecification workspaceSpec = newWorkspace(projectId, "config-workspace");

        Revision revision = this.session.getProjectConfigurationApi().updateProjectConfiguration(projectId, workspaceSpec.getSourceSpecification(),
                "Update artifact coordinates", ProjectConfigurationUpdater.newUpdater().withGroupId(GROUP_ID + ".updated"));
        Assert.assertNotNull("a configuration change must produce a revision", revision);

        Assert.assertEquals(GROUP_ID + ".updated",
                this.session.getProjectConfigurationApi().getProjectConfiguration(projectId, workspaceSpec.getSourceSpecification()).getGroupId());
        Assert.assertEquals("the workspace change must not be visible at the project source", GROUP_ID,
                this.session.getProjectConfigurationApi().getProjectConfiguration(projectId, SourceSpecification.projectSourceSpecification()).getGroupId());
    }

    @Test
    public void testWorkspaceComparison()
    {
        String projectId = createProject().getProjectId();
        WorkspaceSpecification workspaceSpec = newWorkspace(projectId, "comparison-workspace");

        Entity entity = newClassEntity("model::domain::ComparisonClass");
        this.session.getEntityApi().getEntityModificationContext(projectId, workspaceSpec.getSourceSpecification())
                .performChanges(Collections.singletonList(EntityChange.newCreateEntity(entity.getPath(), entity.getClassifierPath(), entity.getContent())), null, "Add comparison entity");

        Comparison comparison = this.session.getComparisonApi().getWorkspaceSourceComparison(projectId, workspaceSpec);
        Assert.assertNotNull(comparison.getFromRevisionId());
        Assert.assertNotNull(comparison.getToRevisionId());
        Assert.assertEquals(1, comparison.getEntityDiffs().size());
        Assert.assertEquals(entity.getPath(), comparison.getEntityDiffs().get(0).getNewPath());

        Comparison creationComparison = this.session.getComparisonApi().getWorkspaceCreationComparison(projectId, workspaceSpec);
        Assert.assertEquals(1, creationComparison.getEntityDiffs().size());
    }

    @Test
    public void testRevisionContexts()
    {
        String projectId = createProject().getProjectId();
        WorkspaceSpecification workspaceSpec = newWorkspace(projectId, "revision-workspace");

        Revision projectRevision = this.session.getRevisionApi().getRevisionContext(projectId, SourceSpecification.projectSourceSpecification()).getCurrentRevision();
        Assert.assertNotNull("building the project structure must leave a current revision", projectRevision);

        Entity entity = newClassEntity("model::domain::RevisionClass");
        Revision entityRevision = this.session.getEntityApi().getEntityModificationContext(projectId, workspaceSpec.getSourceSpecification())
                .performChanges(Collections.singletonList(EntityChange.newCreateEntity(entity.getPath(), entity.getClassifierPath(), entity.getContent())), null, "Add revision entity");

        List<Revision> entityRevisions = this.session.getRevisionApi()
                .getEntityRevisionContext(projectId, workspaceSpec.getSourceSpecification(), entity.getPath())
                .getRevisions();
        Assert.assertEquals(1, entityRevisions.size());
        Assert.assertEquals(entityRevision.getId(), entityRevisions.get(0).getId());
    }

    protected Project createProject()
    {
        String name = "ScenarioProject" + PROJECT_COUNTER.incrementAndGet();
        return this.session.getProjectApi().createProject(name, "Backend TCK scenario project", ProjectType.MANAGED, GROUP_ID, name.toLowerCase(), Collections.emptyList());
    }

    protected WorkspaceSpecification newWorkspace(String projectId, String workspaceId)
    {
        this.session.getWorkspaceApi().newWorkspace(projectId, workspaceId, WorkspaceType.USER, WorkspaceSource.projectWorkspaceSource());
        return WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER);
    }

    protected Entity newClassEntity(String path)
    {
        String name = path.substring(path.lastIndexOf(':') + 1);
        String pkg = path.substring(0, path.lastIndexOf("::"));
        return Entity.newEntity(path, "meta::pure::metamodel::type::Class",
                Maps.mutable.<String, Object>empty()
                        .withKeyValue("_type", "class")
                        .withKeyValue("name", name)
                        .withKeyValue("package", pkg)
                        .withKeyValue("properties", Collections.emptyList()));
    }
}
