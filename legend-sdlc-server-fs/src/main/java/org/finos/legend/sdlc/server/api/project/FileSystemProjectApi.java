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

package org.finos.legend.sdlc.server.api.project;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.accessRole.AccessRole;
import org.finos.legend.sdlc.domain.model.project.accessRole.AuthorizableProjectAction;
import org.finos.legend.sdlc.server.api.BaseFSApi;
import org.finos.legend.sdlc.server.api.entity.FileSystemEntityApi;
import org.finos.legend.sdlc.server.domain.api.project.ProjectApi;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationUpdater;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.exception.UnavailableFeature;

import org.eclipse.jgit.api.Git;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectStructure;
import org.finos.legend.sdlc.server.project.ProjectStructurePlatformExtensions;
import org.finos.legend.sdlc.server.project.config.ProjectCreationConfiguration;
import org.finos.legend.sdlc.server.project.config.ProjectStructureConfiguration;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtensionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.finos.legend.sdlc.server.api.workspace.FileSystemWorkspaceApi.retrieveRepo;

public class FileSystemProjectApi extends BaseFSApi implements ProjectApi
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemProjectApi.class);
    private final ProjectStructureConfiguration projectStructureConfig;
    private final ProjectStructureExtensionProvider projectStructureExtensionProvider;
    private final ProjectStructurePlatformExtensions projectStructurePlatformExtensions;

    @Inject
    public FileSystemProjectApi(ProjectStructureConfiguration projectStructureConfig, ProjectStructureExtensionProvider projectStructureExtensionProvider, ProjectStructurePlatformExtensions projectStructurePlatformExtensions)
    {
        this.projectStructureConfig = projectStructureConfig;
        this.projectStructureExtensionProvider = projectStructureExtensionProvider;
        this.projectStructurePlatformExtensions = projectStructurePlatformExtensions;
    }

    @Override
    public Project getProject(String id)
    {
        Repository repository = retrieveRepo(id);
        Git git = new Git(repository);
        return gitProjectToProject(git);
    }

    @Override
    public List<Project> getProjects(boolean user, String search, Iterable<String> tags, Integer limit)
    {
        List<Project> gitRepos = new ArrayList<>();
        try (Stream<Path> paths = Files.list(Paths.get(rootDirectory)))
        {
            paths.filter(path -> Files.isDirectory(path)).forEach(repoDir ->
            {
                try
                {
                    FileRepositoryBuilder repoBuilder = new FileRepositoryBuilder();
                    Repository repository = repoBuilder.setGitDir(new File(repoDir.toFile(), ".git")).readEnvironment().findGitDir().build();
                    if (repository != null)
                    {
                        gitRepos.add(gitProjectToProject(new Git(repository)));
                    }
                }
                catch (IOException e)
                {
                    LOGGER.error("Repository {} could not be accessed", repoDir, e);
                }
            });
        }
        catch (IOException e)
        {
            LOGGER.error("Exception occurred when opening the directory", rootDirectory, e);
        }
        return gitRepos;
    }

    @Override
    public Project createProject(String name, String description, ProjectType type, String groupId, String artifactId, Iterable<String> tags)
    {
        LegendSDLCServerException.validate(name, n -> (n != null) && !n.isEmpty(), "name may not be null or empty");
        LegendSDLCServerException.validateNonNull(description, "description may not be null");

        String projectPath = rootDirectory + "/" + name;
        Git gitProject = null;
        String projectId = name;
        try
        {
            Repository repository = FileRepositoryBuilder.create(new File(projectPath, ".git"));
            repository.create();

            gitProject = new Git(repository);
            gitProject.commit().setMessage("Initial Commit").call();

            repository.getConfig().setString("project", null, "id", projectId);
            repository.getConfig().setString("project", null, "name", name);
            repository.getConfig().setString("project", null, "description", description);
            repository.getConfig().save();
        }
        catch (IOException | GitAPIException e)
        {
            LOGGER.error("Failed to create project {}", name, e);
        }
        if (gitProject == null)
        {
            throw new LegendSDLCServerException("Failed to create project: " + name);
        }
        Project project = gitProjectToProject(gitProject);

        // Build project structure
        int projectStructureVersion = getDefaultProjectStructureVersion();
        ProjectConfigurationUpdater configUpdater = ProjectConfigurationUpdater.newUpdater()
                .withProjectId(project.getProjectId())
                .withProjectType(type)
                .withGroupId(groupId)
                .withArtifactId(artifactId)
                .withProjectStructureVersion(projectStructureVersion);
        if (this.projectStructureExtensionProvider != null && type != ProjectType.EMBEDDED)
        {
            configUpdater.setProjectStructureExtensionVersion(this.projectStructureExtensionProvider.getLatestVersionForProjectStructureVersion(projectStructureVersion));
        }
        ProjectStructure.newUpdateBuilder(FileSystemProjectApi.getProjectFileAccessProvider(), project.getProjectId(), configUpdater)
                .withMessage("Build project structure")
                .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                .build();
        return project;
    }

    public static ProjectFileAccessProvider getProjectFileAccessProvider()
    {
        return new ProjectFileAccessProvider()
        {
            @Override
            public FileAccessContext getFileAccessContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
            {
                return new FileSystemEntityApi.AbstractFileSystemFileAccessContext(projectId, sourceSpecification, revisionId);
            }

            @Override
            public RevisionAccessContext getRevisionAccessContext(String projectId, SourceSpecification sourceSpecification, Iterable<? extends String> paths)
            {
                return new FileSystemEntityApi.FileSystemRevisionAccessContext(projectId, sourceSpecification, paths);
            }

            @Override
            public FileModificationContext getFileModificationContext(String projectId, SourceSpecification sourceSpecification, String revisionId)
            {
                return new FileSystemEntityApi.FileSystemProjectFileFileModificationContext(projectId, sourceSpecification, revisionId);
            }
        };
    }

    private int getDefaultProjectStructureVersion()
    {
        ProjectCreationConfiguration projectCreationConfig = getProjectCreationConfiguration();
        if (projectCreationConfig != null)
        {
            Integer defaultProjectStructureVersion = projectCreationConfig.getDefaultProjectStructureVersion();
            if (defaultProjectStructureVersion != null)
            {
                return defaultProjectStructureVersion;
            }
        }
        return ProjectStructure.getLatestProjectStructureVersion();
    }

    private ProjectCreationConfiguration getProjectCreationConfiguration()
    {
        return (this.projectStructureConfig == null) ? null : this.projectStructureConfig.getProjectCreationConfiguration();
    }

    private Project gitProjectToProject(Git project)
    {
        return (project == null) ? null : new FileSystemProjectApi.ProjectWrapper(project);
    }

    private class ProjectWrapper implements Project
    {
        private final Git gitProject;

        public ProjectWrapper(Git gitProject)
        {
            this.gitProject = gitProject;
        }

        @Override
        public String getProjectId()
        {
            return this.gitProject.getRepository().getConfig().getString("project", null, "id");
        }

        @Override
        public String getName()
        {
            return this.gitProject.getRepository().getConfig().getString("project", null, "name");
        }

        @Override
        public String getDescription()
        {
            return this.gitProject.getRepository().getConfig().getString("project", null, "description");
        }

        @Override
        public List<String> getTags()
        {
            return null;
        }

        @Override
        public ProjectType getProjectType()
        {
            return null;
        }

        @Override
        public String getWebUrl()
        {
            return null;
        }
    }

    @Override
    public void deleteProject(String id)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public void changeProjectName(String id, String newName)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public void changeProjectDescription(String id, String newDescription)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public void updateProjectTags(String id, Iterable<String> tagsToRemove, Iterable<String> tagsToAdd)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public void setProjectTags(String id, Iterable<String> tags)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public AccessRole getCurrentUserAccessRole(String id)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public Set<AuthorizableProjectAction> checkUserAuthorizedActions(String id, Set<AuthorizableProjectAction> actions)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public boolean checkUserAuthorizedAction(String id, AuthorizableProjectAction action)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public ImportReport importProject(String id, ProjectType type, String groupId, String artifactId)
    {
        throw UnavailableFeature.exception();
    }

}
