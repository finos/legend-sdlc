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

package org.finos.legend.sdlc.backend.fs;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.finos.legend.sdlc.backend.api.project.ProjectApi;
import org.finos.legend.sdlc.backend.api.spi.BackendEnvironment;
import org.finos.legend.sdlc.core.project.ProjectConfigurationUpdater;
import org.finos.legend.sdlc.core.project.ProjectStructureUpdater;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.accessRole.AccessRole;
import org.finos.legend.sdlc.domain.model.project.accessRole.AuthorizableProjectAction;
import org.finos.legend.sdlc.domain.model.project.accessRole.UserPermission;
import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.project.structure.ProjectStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Project lifecycle over the file-system root: every subdirectory holding a git repository is a project, its
 * metadata (name, description, type, tags) in the repository configuration. Creating a project initializes the
 * repository and builds the latest project structure version through the L3 updater, with the deployment's
 * extensions from the environment. Every user holds every permission (there is no access model to enforce).
 */
public class FileSystemProjectApi implements ProjectApi
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemProjectApi.class);

    private final FileSystemBackend backend;
    private final FileSystemProjectFileAccessProvider fileAccessProvider;

    FileSystemProjectApi(FileSystemBackend backend, FileSystemProjectFileAccessProvider fileAccessProvider)
    {
        this.backend = backend;
        this.fileAccessProvider = fileAccessProvider;
    }

    @Override
    public Project getProject(String id)
    {
        LegendSDLCException.validateNonNull(id, "id may not be null", 400);
        return toProject(this.fileAccessProvider.getRepository(id));
    }

    @Override
    public List<Project> getProjects(boolean user, String search, Iterable<String> tags, Iterable<String> excludeTags, Integer limit)
    {
        MutableList<Project> projects = Lists.mutable.empty();
        try (Stream<Path> paths = Files.list(Paths.get(this.fileAccessProvider.getRootDirectory())))
        {
            paths.filter(Files::isDirectory)
                    .filter(dir -> new File(dir.toFile(), Constants.DOT_GIT).isDirectory())
                    .forEach(dir ->
                    {
                        Repository repository = this.fileAccessProvider.findRepository(dir.getFileName().toString());
                        if (repository != null)
                        {
                            Project project = toProject(repository);
                            if (matches(project, search, tags, excludeTags))
                            {
                                projects.add(project);
                            }
                        }
                    });
        }
        catch (IOException e)
        {
            LOGGER.error("Exception occurred when opening the directory {}", this.fileAccessProvider.getRootDirectory(), e);
            throw FileSystemProjectFileAccessProvider.wrapException("Failed to fetch projects", e);
        }
        return ((limit != null) && (projects.size() > limit)) ? projects.subList(0, Math.max(0, limit)) : projects;
    }

    private static boolean matches(Project project, String search, Iterable<String> tags, Iterable<String> excludeTags)
    {
        if ((search != null) && ((project.getName() == null) || !project.getName().toLowerCase().contains(search.toLowerCase())))
        {
            return false;
        }
        List<String> projectTags = project.getTags();
        if ((excludeTags != null) && Iterate.anySatisfy(excludeTags, projectTags::contains))
        {
            return false;
        }
        return (tags == null) || Iterate.isEmpty(tags) || Iterate.anySatisfy(tags, projectTags::contains);
    }

    @Override
    public Project createProject(String name, String description, ProjectType type, String groupId, String artifactId, Iterable<String> tags)
    {
        LegendSDLCException.validate(name, n -> (n != null) && !n.isEmpty(), "name may not be null or empty", 400);
        LegendSDLCException.validateNonNull(type, "type may not be null", 400);
        LegendSDLCException.validateNonNull(groupId, "groupId may not be null", 400);
        LegendSDLCException.validateNonNull(artifactId, "artifactId may not be null", 400);
        if (this.fileAccessProvider.findRepository(name) != null)
        {
            throw new LegendSDLCException("Project " + name + " already exists", 409);
        }

        Repository repository = this.fileAccessProvider.initRepository(name, name, description);
        try
        {
            repository.getConfig().setString("project", null, "type", type.name());
            if ((tags != null) && !Iterate.isEmpty(tags))
            {
                repository.getConfig().setStringList("project", null, "tag", Lists.mutable.withAll(tags));
            }
            repository.getConfig().save();
        }
        catch (IOException e)
        {
            throw FileSystemProjectFileAccessProvider.wrapException("Failed to create project: " + name, e);
        }

        BackendEnvironment environment = this.backend.environment();
        ProjectStructureUpdater.newUpdateBuilder(this.fileAccessProvider, name,
                        ProjectConfigurationUpdater.newUpdater()
                                .withProjectId(name)
                                .withProjectType(type)
                                .withGroupId(groupId)
                                .withArtifactId(artifactId)
                                .withProjectStructureVersion(ProjectStructure.getLatestProjectStructureVersion()))
                .withProjectStructureExtensionProvider(environment.getProjectStructureExtensionProvider())
                .withProjectStructurePlatformExtensions(environment.getProjectStructurePlatformExtensions())
                .withMessage("Build project structure")
                .build();
        return toProject(repository);
    }

    @Override
    public void deleteProject(String id)
    {
        throw new LegendSDLCException("Deleting projects is not supported by the file-system backend", 501);
    }

    @Override
    public void changeProjectName(String id, String newName)
    {
        LegendSDLCException.validateNonNull(newName, "newName may not be null", 400);
        setProjectConfigValues(id, "name", Collections.singletonList(newName));
    }

    @Override
    public void changeProjectDescription(String id, String newDescription)
    {
        LegendSDLCException.validateNonNull(newDescription, "newDescription may not be null", 400);
        setProjectConfigValues(id, "description", Collections.singletonList(newDescription));
    }

    @Override
    public void updateProjectTags(String id, Iterable<String> tagsToRemove, Iterable<String> tagsToAdd)
    {
        Repository repository = this.fileAccessProvider.getRepository(id);
        MutableList<String> tags = Lists.mutable.of(repository.getConfig().getStringList("project", null, "tag"));
        if (tagsToRemove != null)
        {
            tagsToRemove.forEach(tags::remove);
        }
        if (tagsToAdd != null)
        {
            tagsToAdd.forEach(tag ->
            {
                if (!tags.contains(tag))
                {
                    tags.add(tag);
                }
            });
        }
        setProjectConfigValues(id, "tag", tags);
    }

    @Override
    public void setProjectTags(String id, Iterable<String> tags)
    {
        LegendSDLCException.validateNonNull(tags, "tags may not be null", 400);
        setProjectConfigValues(id, "tag", Lists.mutable.withAll(tags));
    }

    private void setProjectConfigValues(String id, String key, List<String> values)
    {
        Repository repository = this.fileAccessProvider.getRepository(id);
        try
        {
            repository.getConfig().setStringList("project", null, key, values);
            repository.getConfig().save();
        }
        catch (IOException e)
        {
            throw FileSystemProjectFileAccessProvider.wrapException("Failed to update project " + id, e);
        }
    }

    @Override
    public AccessRole getCurrentUserAccessRole(String id)
    {
        this.fileAccessProvider.getRepository(id);
        return () -> "OWNER";
    }

    @Override
    public Set<AuthorizableProjectAction> checkUserAuthorizedActions(String id, Set<AuthorizableProjectAction> actions)
    {
        this.fileAccessProvider.getRepository(id);
        return ((actions == null) || actions.isEmpty()) ? Collections.emptySet() : EnumSet.copyOf(actions);
    }

    @Override
    public boolean checkUserAuthorizedAction(String id, AuthorizableProjectAction action)
    {
        this.fileAccessProvider.getRepository(id);
        return true;
    }

    @Override
    public ImportReport importProject(String id, ProjectType type, String groupId, String artifactId)
    {
        LegendSDLCException.validateNonNull(id, "id may not be null", 400);
        Project project = getProject(id);
        return new ImportReport()
        {
            @Override
            public Project getProject()
            {
                return project;
            }

            @Override
            public String getReviewId()
            {
                return null;
            }
        };
    }

    @Override
    public Set<UserPermission> getAllUsersAuthorizedActions(String id, Set<AuthorizableProjectAction> actions)
    {
        this.fileAccessProvider.getRepository(id);
        return Collections.emptySet();
    }

    private static Project toProject(Repository repository)
    {
        String id = repository.getConfig().getString("project", null, "id");
        String name = repository.getConfig().getString("project", null, "name");
        String description = repository.getConfig().getString("project", null, "description");
        String typeName = repository.getConfig().getString("project", null, "type");
        ProjectType type = (typeName == null) ? null : ProjectType.valueOf(typeName);
        List<String> tags = Arrays.asList(repository.getConfig().getStringList("project", null, "tag"));
        return new Project()
        {
            @Override
            public String getProjectId()
            {
                return id;
            }

            @Override
            public String getName()
            {
                return name;
            }

            @Override
            public String getDescription()
            {
                return description;
            }

            @Override
            public List<String> getTags()
            {
                return tags;
            }

            @Override
            public ProjectType getProjectType()
            {
                return type;
            }

            @Override
            public String getWebUrl()
            {
                return null;
            }
        };
    }
}
