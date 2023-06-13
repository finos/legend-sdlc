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

package org.finos.legend.sdlc.server.gitlab.api.docker;

import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.api.GitLabProjectApi;
import org.finos.legend.sdlc.server.gitlab.api.GitLabProjectApiTestResource;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.project.ProjectStructure;
import org.finos.legend.sdlc.server.project.config.ProjectStructureConfiguration;
import org.finos.legend.sdlc.server.project.extension.DefaultProjectStructureExtension;
import org.finos.legend.sdlc.server.project.extension.DefaultProjectStructureExtensionProvider;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtension;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtensionProvider;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class IntegrationTestGitLabProjectApis extends AbstractGitLabApiTest
{
    private static GitLabProjectApiTestResource gitLabProjectApiTestResource;

    @BeforeClass
    public static void setup() throws LegendSDLCServerException
    {
        setUpProjectApi();
    }

    @Test
    public void testCreateProject() throws LegendSDLCServerException
    {
        gitLabProjectApiTestResource.runCreateProjectTest();
    }

    @Test
    public void testCreateManagedProject() throws LegendSDLCServerException
    {
        gitLabProjectApiTestResource.runCreateManagedProjectTest();
    }

    @Test
    public void testCreateEmbeddedProject() throws LegendSDLCServerException
    {
        gitLabProjectApiTestResource.runCreateEmbeddedProjectTest();
    }

    @Test
    public void testCreateProductionProject() throws LegendSDLCServerException
    {
        gitLabProjectApiTestResource.runCreateProductionProjectTest();
    }

    @Test
    public void testGetProject() throws LegendSDLCServerException
    {
        gitLabProjectApiTestResource.runGetProjectTest();
    }

    @Test
    public void testUpdateProject()
    {
        gitLabProjectApiTestResource.runUpdateProjectTest();
    }

    /**
     * Authenticates with OAuth2 and instantiate the test SDLC GitLabProjectApi.
     *
     * @throws LegendSDLCServerException if cannot authenticate to GitLab.
     */
    private static void setUpProjectApi() throws LegendSDLCServerException
    {
        int projectStructureVersion = ProjectStructure.getLatestProjectStructureVersion();
        int projectStructureExtensionVersion = 1;
        ProjectStructureExtension extension = DefaultProjectStructureExtension.newProjectStructureExtension(projectStructureVersion, projectStructureExtensionVersion, Collections.singletonMap("/PANGRAM.TXT", "THE QUICK BROWN FOX JUMPED OVER THE LAZY DOG"));
        List<ProjectStructureExtension> extensions = Collections.singletonList(extension);
        ProjectStructureExtensionProvider extensionProvider = DefaultProjectStructureExtensionProvider.fromExtensions(extensions);

        GitLabConfiguration gitLabConfig = GitLabConfiguration.newGitLabConfiguration(null, null, null, null, null, null);
        ProjectStructureConfiguration projectStructureConfig = ProjectStructureConfiguration.newConfiguration(null, extensionProvider, null,null, null,null);
        GitLabUserContext gitLabUserContext = prepareGitLabOwnerUserContext();

        GitLabProjectApi gitLabProjectApi = new GitLabProjectApi(gitLabConfig, gitLabUserContext, projectStructureConfig, extensionProvider, backgroundTaskProcessor, null);
        gitLabProjectApiTestResource = new GitLabProjectApiTestResource(gitLabProjectApi);
    }
}
