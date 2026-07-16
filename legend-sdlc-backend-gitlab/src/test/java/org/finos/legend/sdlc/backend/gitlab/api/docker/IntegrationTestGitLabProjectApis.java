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

package org.finos.legend.sdlc.backend.gitlab.api.docker;

import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.backend.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabProjectApi;
import org.finos.legend.sdlc.backend.gitlab.api.GitLabProjectApiTestResource;
import org.finos.legend.sdlc.backend.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.backend.gitlab.api.TestProjectStructureExtensions;
import org.finos.legend.sdlc.project.structure.ProjectStructure;
import org.finos.legend.sdlc.project.structure.extension.ProjectStructureExtension;
import org.finos.legend.sdlc.project.structure.extension.ProjectStructureExtensionProvider;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class IntegrationTestGitLabProjectApis extends AbstractGitLabApiTest
{
    private static GitLabProjectApiTestResource gitLabProjectApiTestResource;

    @BeforeClass
    public static void setup() throws LegendSDLCException
    {
        setUpProjectApi();
    }

    @Test
    public void testCreateProject() throws LegendSDLCException
    {
        gitLabProjectApiTestResource.runCreateProjectTest();
    }

    @Test
    public void testCreateManagedProject() throws LegendSDLCException
    {
        gitLabProjectApiTestResource.runCreateManagedProjectTest();
    }

    @Test
    public void testCreateEmbeddedProject() throws LegendSDLCException
    {
        gitLabProjectApiTestResource.runCreateEmbeddedProjectTest();
    }

    @Test
    public void testCreateProductionProject() throws LegendSDLCException
    {
        gitLabProjectApiTestResource.runCreateProductionProjectTest();
    }

    @Test
    public void testGetProject() throws LegendSDLCException
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
     * @throws LegendSDLCException if cannot authenticate to GitLab.
     */
    private static void setUpProjectApi() throws LegendSDLCException
    {
        int projectStructureVersion = ProjectStructure.getLatestProjectStructureVersion();
        int projectStructureExtensionVersion = 1;
        ProjectStructureExtension extension = TestProjectStructureExtensions.newFileExtension(projectStructureVersion, projectStructureExtensionVersion, Collections.singletonMap("/PANGRAM.TXT", "THE QUICK BROWN FOX JUMPED OVER THE LAZY DOG"));
        ProjectStructureExtensionProvider extensionProvider = TestProjectStructureExtensions.providerFor(extension);

        GitLabConfiguration gitLabConfig = GitLabConfiguration.newGitLabConfiguration(null, null, null, null, null, null);
        GitLabUserContext gitLabUserContext = prepareGitLabOwnerUserContext();

        GitLabProjectApi gitLabProjectApi = new GitLabProjectApi(gitLabConfig, gitLabUserContext, null, extensionProvider, backgroundTaskProcessor, null);
        gitLabProjectApiTestResource = new GitLabProjectApiTestResource(gitLabProjectApi);
    }
}
