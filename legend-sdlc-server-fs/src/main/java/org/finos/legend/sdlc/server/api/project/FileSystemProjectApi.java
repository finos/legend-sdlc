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
import org.finos.legend.sdlc.server.FSConfiguration;
import org.finos.legend.sdlc.server.domain.api.project.ProjectApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.exception.UnavailableFeature;

import org.eclipse.jgit.api.Git;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.finos.legend.sdlc.server.api.workspace.FileSystemWorkspaceApi.retrieveRepo;

public class FileSystemProjectApi implements ProjectApi
{
    @Inject
    public FileSystemProjectApi()
    {
    }

    @Override
    public Project getProject(String id)
    {
        try
        {
            Repository repository = retrieveRepo(id);
            System.out.println("Retrieved project: " + repository.getConfig().getString("project", null, "name"));
            Git git = new Git(repository);
            return gitProjectToProject(git);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<Project> getProjects(boolean user, String search, Iterable<String> tags, Integer limit)
    {
        List<Project> gitRepos = new ArrayList<>();
        File[] repoDirs = new File(FSConfiguration.getRootDirectory()).listFiles(File::isDirectory);
        if (repoDirs != null)
        {
            for (File repoDir : repoDirs)
            {
                try
                {
                    FileRepositoryBuilder repoBuilder = new FileRepositoryBuilder();
                    Repository repository = repoBuilder.setGitDir(new File(repoDir, ".git")).readEnvironment().findGitDir().build();
                    if (repository != null)
                    {
                        gitRepos.add(gitProjectToProject(new Git(repository)));
                    }
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
        return gitRepos;
    }

    @Override
    public Project createProject(String name, String description, ProjectType type, String groupId, String artifactId, Iterable<String> tags)
    {
        LegendSDLCServerException.validate(name, n -> (n != null) && !n.isEmpty(), "name may not be null or empty");
        LegendSDLCServerException.validateNonNull(description, "description may not be null");

        String projectPath = FSConfiguration.getRootDirectory() + "/" + name;
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
            System.out.println("Project created successfully at: " + projectPath);
        }
        catch (IOException | GitAPIException e)
        {
            e.printStackTrace();
        }
        if (gitProject == null)
        {
            throw new LegendSDLCServerException("Failed to create project: " + name);
        }
        Project project = gitProjectToProject(gitProject);
        return project;
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
