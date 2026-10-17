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

package org.finos.legend.sdlc.backend.inmemory;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.impl.utility.Iterate;
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

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Project lifecycle over the in-memory registry. Projects are identified by their name; creating one builds the
 * latest project structure version through the L3 updater, with the deployment's extensions from the
 * environment. Every user holds every permission (there is no access model to enforce).
 */
public class InMemoryProjectApi implements ProjectApi
{
    private final InMemoryBackend backend;

    InMemoryProjectApi(InMemoryBackend backend)
    {
        this.backend = backend;
    }

    @Override
    public Project getProject(String id)
    {
        LegendSDLCException.validateNonNull(id, "id may not be null", 400);
        return toProject(this.backend.getProject(id));
    }

    @Override
    public List<Project> getProjects(boolean user, String search, Iterable<String> tags, Iterable<String> excludeTags, Integer limit)
    {
        List<InMemoryBackend.ProjectState> states = this.backend.getProjects(state ->
        {
            if ((search != null) && !state.getName().toLowerCase().contains(search.toLowerCase()))
            {
                return false;
            }
            List<String> projectTags = state.getTags();
            if ((excludeTags != null) && Iterate.anySatisfy(excludeTags, projectTags::contains))
            {
                return false;
            }
            return (tags == null) || Iterate.isEmpty(tags) || Iterate.anySatisfy(tags, projectTags::contains);
        });
        if ((limit != null) && (states.size() > limit))
        {
            states = states.subList(0, Math.max(0, limit));
        }
        return Iterate.collect(states, this::toProject, Lists.mutable.<Project>empty());
    }

    @Override
    public Project createProject(String name, String description, ProjectType type, String groupId, String artifactId, Iterable<String> tags)
    {
        LegendSDLCException.validateNonNull(name, "name may not be null", 400);
        LegendSDLCException.validateNonNull(type, "type may not be null", 400);
        LegendSDLCException.validateNonNull(groupId, "groupId may not be null", 400);
        LegendSDLCException.validateNonNull(artifactId, "artifactId may not be null", 400);

        InMemoryBackend.ProjectState state = this.backend.createProject(name, name, description, type, tags);
        BackendEnvironment environment = this.backend.environment();
        ProjectStructureUpdater.newUpdateBuilder(this.backend.getFileAccessProvider(), state.getId(),
                        ProjectConfigurationUpdater.newUpdater()
                                .withProjectId(state.getId())
                                .withProjectType(type)
                                .withGroupId(groupId)
                                .withArtifactId(artifactId)
                                .withProjectStructureVersion(ProjectStructure.getLatestProjectStructureVersion()))
                .withProjectStructureExtensionProvider(environment.getProjectStructureExtensionProvider())
                .withProjectStructurePlatformExtensions(environment.getProjectStructurePlatformExtensions())
                .withMessage("Build project " + state.getId())
                .build();
        return toProject(state);
    }

    @Override
    public void deleteProject(String id)
    {
        LegendSDLCException.validateNonNull(id, "id may not be null", 400);
        this.backend.deleteProject(id);
    }

    @Override
    public void changeProjectName(String id, String newName)
    {
        LegendSDLCException.validateNonNull(newName, "newName may not be null", 400);
        this.backend.getProject(id).setName(newName);
    }

    @Override
    public void changeProjectDescription(String id, String newDescription)
    {
        LegendSDLCException.validateNonNull(newDescription, "newDescription may not be null", 400);
        this.backend.getProject(id).setDescription(newDescription);
    }

    @Override
    public void updateProjectTags(String id, Iterable<String> tagsToRemove, Iterable<String> tagsToAdd)
    {
        InMemoryBackend.ProjectState state = this.backend.getProject(id);
        if (tagsToRemove != null)
        {
            tagsToRemove.forEach(state::removeTag);
        }
        if (tagsToAdd != null)
        {
            tagsToAdd.forEach(state::addTag);
        }
    }

    @Override
    public void setProjectTags(String id, Iterable<String> tags)
    {
        LegendSDLCException.validateNonNull(tags, "tags may not be null", 400);
        this.backend.getProject(id).setTags(tags);
    }

    @Override
    public AccessRole getCurrentUserAccessRole(String id)
    {
        this.backend.getProject(id);
        return () -> "OWNER";
    }

    @Override
    public Set<AuthorizableProjectAction> checkUserAuthorizedActions(String id, Set<AuthorizableProjectAction> actions)
    {
        this.backend.getProject(id);
        return ((actions == null) || actions.isEmpty()) ? Collections.emptySet() : EnumSet.copyOf(actions);
    }

    @Override
    public boolean checkUserAuthorizedAction(String id, AuthorizableProjectAction action)
    {
        this.backend.getProject(id);
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
        this.backend.getProject(id);
        return Collections.emptySet();
    }

    private Project toProject(InMemoryBackend.ProjectState state)
    {
        String id = state.getId();
        String name = state.getName();
        String description = state.getDescription();
        List<String> tags = state.getTags();
        ProjectType type = state.getType();
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
