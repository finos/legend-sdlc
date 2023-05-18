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

package org.finos.legend.sdlc.server.gitlab.api;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.finos.legend.sdlc.domain.model.patch.Patch;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.version.Version;
import org.finos.legend.sdlc.server.domain.api.version.NewVersionType;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.api.server.AbstractGitLabServerApiTest;
import org.junit.Assert;

import java.util.List;

/**
 * Substantial test resource class for patch API tests shared by the docker-based and server-based GitLab tests.
 */
public class GitLabPatchApiTestResource
{
    private final GitLabPatchApi gitLabPatchApi;
    private final GitLabProjectApi gitLabProjectApi;
    private final GitLabRevisionApi gitLabRevisionApi;
    private final GitLabVersionApi gitLabVersionApi;

    public GitLabPatchApiTestResource(GitLabPatchApi gitLabPatchApi, GitLabProjectApi gitLabProjectApi, GitLabRevisionApi gitLabRevisionApi, GitLabVersionApi gitLabVersionApi)
    {
        this.gitLabPatchApi = gitLabPatchApi;
        this.gitLabProjectApi = gitLabProjectApi;
        this.gitLabRevisionApi = gitLabRevisionApi;
        this.gitLabVersionApi = gitLabVersionApi;
    }

    public void runCreatePatchTest() throws LegendSDLCServerException
    {
        String projectName = "PatchTestProjectOne";
        String description = "A test project.";
        String groupId = "org.finos.sdlc.test";
        String artifactId = "testpatchprojone";
        List<String> tags = Lists.mutable.with("doe", "moffitt", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);

        Project createdProject = gitLabProjectApi.createProject(projectName, description, ProjectType.MANAGED, groupId, artifactId, tags);

        Assert.assertNotNull(createdProject);
        Assert.assertEquals(projectName, createdProject.getName());
        Assert.assertEquals(description, createdProject.getDescription());
        Assert.assertNull(createdProject.getProjectType());
        Assert.assertEquals(Sets.mutable.withAll(tags), Sets.mutable.withAll(createdProject.getTags()));

        String projectId = createdProject.getProjectId();
        Version version = gitLabVersionApi.newVersion(projectId, NewVersionType.PATCH, gitLabRevisionApi.getProjectRevisionContext(projectId).getCurrentRevision().getId(), "");
        Patch patch = gitLabPatchApi.newPatch(projectId, version.getId());

        Assert.assertNotNull(patch);
        Assert.assertEquals(projectId, patch.getProjectId());
        Assert.assertEquals("0.0.2", patch.getPatchReleaseVersionId().toVersionIdString());
    }

    public void runGetPatchesTest() throws LegendSDLCServerException
    {
        String projectName = "PatchTestProjectTwo";
        String description = "A test project.";
        String groupId = "org.finos.sdlc.test";
        String artifactId = "testpatchprojtwo";
        List<String> tags = Lists.mutable.with("doe", "moffitt", AbstractGitLabServerApiTest.INTEGRATION_TEST_PROJECT_TAG);

        Project createdProject = gitLabProjectApi.createProject(projectName, description, ProjectType.MANAGED, groupId, artifactId, tags);

        Assert.assertNotNull(createdProject);
        Assert.assertEquals(projectName, createdProject.getName());
        Assert.assertEquals(description, createdProject.getDescription());
        Assert.assertNull(createdProject.getProjectType());
        Assert.assertEquals(Sets.mutable.withAll(tags), Sets.mutable.withAll(createdProject.getTags()));

        String projectId = createdProject.getProjectId();
        Version versionOne = gitLabVersionApi.newVersion(projectId, NewVersionType.PATCH, gitLabRevisionApi.getProjectRevisionContext(projectId).getCurrentRevision().getId(), "");
        Patch patchOne = gitLabPatchApi.newPatch(projectId, versionOne.getId());

        Assert.assertNotNull(patchOne);
        Assert.assertEquals(projectId, patchOne.getProjectId());
        Assert.assertEquals("0.0.2", patchOne.getPatchReleaseVersionId().toVersionIdString());

        Version versionTwo = gitLabVersionApi.newVersion(projectId, NewVersionType.PATCH, gitLabRevisionApi.getProjectRevisionContext(projectId).getCurrentRevision().getId(), "");
        Patch patchTwo = gitLabPatchApi.newPatch(projectId, versionTwo.getId());

        Assert.assertNotNull(patchTwo);
        Assert.assertEquals(projectId, patchTwo.getProjectId());
        Assert.assertEquals("0.0.3", patchTwo.getPatchReleaseVersionId().toVersionIdString());

        List<Patch> patches = gitLabPatchApi.getPatches(projectId, null, null, null, null, null, null);

        Assert.assertEquals(2, patches.size());
    }

    public GitLabProjectApi getGitLabProjectApi()
    {
        return gitLabProjectApi;
    }
}
