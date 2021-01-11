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

package org.finos.legend.sdlc.server.gitlab.api;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.accessRole.AccessRole;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.domain.api.project.ProjectApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabMode;
import org.finos.legend.sdlc.server.gitlab.tools.GitLabApiTools;
import org.finos.legend.sdlc.server.gitlab.tools.PagerTools;
import org.finos.legend.sdlc.server.project.ProjectConfigurationUpdateBuilder;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectStructure;
import org.finos.legend.sdlc.server.project.config.ProjectCreationConfiguration;
import org.finos.legend.sdlc.server.project.config.ProjectStructureConfiguration;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtensionProvider;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.Pager;
import org.gitlab4j.api.RepositoryApi;
import org.gitlab4j.api.models.AccessLevel;
import org.gitlab4j.api.models.Branch;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.Permissions;
import org.gitlab4j.api.models.ProjectAccess;
import org.gitlab4j.api.models.Visibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.inject.Inject;
import javax.ws.rs.core.Response.Status;

public class GitLabProjectApi extends GitLabApiWithFileAccess implements ProjectApi
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabProjectApi.class);

    private static final String DEFAULT_LEGEND_SDLC_PROJECT_TAG = "legend";
    private static final Visibility DEFAULT_VISIBILITY = Visibility.INTERNAL;

    private final GitLabConfiguration gitLabConfig;
    private final ProjectStructureConfiguration projectStructureConfig;
    private final ProjectStructureExtensionProvider projectStructureExtensionProvider;
    private final GitLabConfiguration gitLabConfiguration;

    @Inject
    public GitLabProjectApi(GitLabConfiguration gitLabConfig, GitLabUserContext userContext, ProjectStructureConfiguration projectStructureConfig, ProjectStructureExtensionProvider projectStructureExtensionProvider, GitLabConfiguration gitLabConfiguration, BackgroundTaskProcessor backgroundTaskProcessor)
    {
        super(userContext, backgroundTaskProcessor);
        this.gitLabConfig = gitLabConfig;
        this.projectStructureConfig = projectStructureConfig;
        this.projectStructureExtensionProvider = projectStructureExtensionProvider;
        this.gitLabConfiguration = gitLabConfiguration;
    }

    @Override
    public Project getProject(String id)
    {
        LegendSDLCServerException.validateNonNull(id, "id may not be null");
        try
        {
            GitLabProjectId projectId = parseProjectId(id);
            org.gitlab4j.api.models.Project gitLabProject = withRetries(() -> getGitLabApi(projectId.getGitLabMode()).getProjectApi().getProject(projectId.getGitLabId()));
            if (!isLegendSDLCProject(gitLabProject))
            {
                throw new LegendSDLCServerException("Failed to get project " + id);
            }
            return fromGitLabProject(gitLabProject, projectId.getGitLabMode());
        }
        catch (Exception e)
        {
            throw buildException(e, () -> "Failed to get project " + id);
        }
    }

    @Override
    public List<Project> getProjects(boolean user, String search, Iterable<String> tags, Iterable<ProjectType> types)
    {
        try
        {
            Set<ProjectType> typesSet;
            if (types == null)
            {
                typesSet = Collections.emptySet();
            }
            else if (types instanceof Set)
            {
                typesSet = (Set<ProjectType>) types;
            }
            else
            {
                typesSet = EnumSet.noneOf(ProjectType.class);
                types.forEach(typesSet::add);
            }
            Iterable<GitLabMode> modes = typesSet.isEmpty() ? getValidGitLabModes() : typesSet.stream().map(GitLabProjectApi::getGitLabModeFromProjectType).filter(this::isValidGitLabMode).collect(Collectors.toList());

            List<Project> projects = Lists.mutable.empty();
            Set<String> tagSet = (tags == null) ? Collections.emptySet() : toLegendSDLCTagSet(tags);
            for (GitLabMode mode : modes)
            {
                Pager<org.gitlab4j.api.models.Project> pager = withRetries(() -> getGitLabApi(mode).getProjectApi().getProjects(null, null, null, null, search, null, null, user, null, null, ITEMS_PER_PAGE));
                Stream<org.gitlab4j.api.models.Project> stream = PagerTools.stream(pager).filter(this::isLegendSDLCProject);
                if (!tagSet.isEmpty())
                {
                    stream = stream.filter(p -> p.getTagList().stream().anyMatch(tagSet::contains));
                }
                stream.map(p -> fromGitLabProject(p, mode)).forEach(projects::add);
            }
            return projects;
        }
        catch (Exception e)
        {
            throw buildException(e, () ->
            {
                StringBuilder message = new StringBuilder("Failed to find ");
                if (user)
                {
                    message.append("user ");
                }
                message.append("projects");
                List<String> tagList = (tags == null) ? Collections.emptyList() : StreamSupport.stream(tags.spliterator(), false).collect(Collectors.toList());
                if ((search != null) || !tagList.isEmpty())
                {
                    message.append(" (");
                    if (search != null)
                    {
                        message.append("search=\"").append(search).append("\"");
                        if (!tagList.isEmpty())
                        {
                            message.append(", ");
                        }
                    }
                    if (!tagList.isEmpty())
                    {
                        tagList.sort(Comparator.naturalOrder());
                        message.append("tags=[").append(String.join(", ", tagList)).append("]");
                    }
                    message.append(')');
                }
                return message.toString();
            });
        }
    }

    @Override
    public Project createProject(String name, String description, ProjectType type, String groupId, String artifactId, Iterable<String> tags)
    {
        LegendSDLCServerException.validate(name, n -> (n != null) && !n.isEmpty(), "name may not be null or empty");
        LegendSDLCServerException.validateNonNull(description, "description may not be null");
        LegendSDLCServerException.validateNonNull(type, "type may not be null");
        LegendSDLCServerException.validate(groupId, ProjectStructure::isValidGroupId, g -> "Invalid groupId: " + g);
        LegendSDLCServerException.validate(artifactId, ProjectStructure::isValidArtifactId, a -> "Invalid artifactId: " + a);

        validateProjectCreation(name, description, type, groupId, artifactId);

        try
        {
            GitLabMode mode = getGitLabModeFromProjectType(type);
            GitLabApi gitLabApi = getGitLabApi(mode);

            List<String> tagList = Lists.mutable.empty();
            tagList.add(getLegendSDLCProjectTag());
            if (tags != null)
            {
                tagList.addAll(toLegendSDLCTagSet(tags));
            }
            org.gitlab4j.api.models.Project gitLabProjectSpec = new org.gitlab4j.api.models.Project()
                    .withName(name)
                    .withDescription(description)
                    .withTagList(tagList)
                    .withVisibility(getNewProjectVisibility())
                    .withMergeRequestsEnabled(true)
                    .withIssuesEnabled(true)
                    .withWikiEnabled(false)
                    .withSnippetsEnabled(false);
            org.gitlab4j.api.models.Project gitLabProject = gitLabApi.getProjectApi().createProject(gitLabProjectSpec);
            if (gitLabProject == null)
            {
                throw new LegendSDLCServerException("Failed to create project: " + name);
            }

            // protect from commits on master
            gitLabApi.getProtectedBranchesApi().protectBranch(gitLabProject.getId(), MASTER_BRANCH, AccessLevel.NONE, AccessLevel.MAINTAINER);

            // build project structure
            ProjectConfigurationUpdateBuilder.newBuilder(getProjectFileAccessProvider(), type, GitLabProjectId.getProjectIdString(mode, gitLabProject))
                    .withMessage("Build project structure")
                    .withGroupId(groupId)
                    .withArtifactId(artifactId)
                    .withProjectStructureVersion(getDefaultProjectStructureVersion())
                    .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                    .buildProjectStructure();

            return fromGitLabProject(gitLabProject, mode);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to create project " + name,
                    () -> "Failed to create project: " + name,
                    () -> "Failed to create project: " + name);
        }
    }

    @Override
    public ImportReport importProject(String id, ProjectType type, String groupId, String artifactId)
    {
        LegendSDLCServerException.validateNonNull(id, "id may not be null");
        LegendSDLCServerException.validateNonNull(type, "type may not be null");
        LegendSDLCServerException.validate(groupId, ProjectStructure::isValidGroupId, g -> "Invalid groupId: " + g);
        LegendSDLCServerException.validate(artifactId, ProjectStructure::isValidArtifactId, a -> "Invalid artifactId: " + a);

        // Get project id
        GitLabProjectId projectId;
        if (id.chars().allMatch(Character::isDigit))
        {
            projectId = GitLabProjectId.newProjectId(getGitLabModeFromProjectType(type), Integer.parseInt(id));
        }
        else
        {
            projectId = parseProjectId(id);
            if (projectId.getGitLabMode() != getGitLabModeFromProjectType(type))
            {
                throw new LegendSDLCServerException("Invalid project id \"" + id + "\" for project type " + type, Status.BAD_REQUEST);
            }
        }

        // Find project
        GitLabApi gitLabApi = getGitLabApi(projectId.getGitLabMode());
        org.gitlab4j.api.ProjectApi gitLabProjectApi = gitLabApi.getProjectApi();
        org.gitlab4j.api.models.Project currentProject;
        try
        {
            currentProject = withRetries(() -> gitLabProjectApi.getProject(projectId.getGitLabId()));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access project " + id + " of type " + type,
                    () -> "Could not find project " + id + " of type " + type,
                    () -> "Failed to access project " + id + " of type " + type);
        }

        // Create a workspace for project configuration
        RepositoryApi repositoryApi = gitLabApi.getRepositoryApi();
        String workspaceId = "ProjectConfiguration_" + getRandomIdString();
        Branch workspaceBranch;
        try
        {
            workspaceBranch = GitLabApiTools.createBranchFromSourceBranchAndVerify(repositoryApi, projectId.getGitLabId(),
                    getUserWorkspaceBranchName(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE),
                    MASTER_BRANCH, 30, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to create a workspace for initial configuration of project " + id + " of type " + type,
                    () -> "Could not find project " + id + " of type " + type,
                    () -> "Failed to create workspace for initial configuration of project " + id + " of type " + type);
        }
        if (workspaceBranch == null)
        {
            throw new LegendSDLCServerException("Failed to create workspace " + workspaceId + " in project " + projectId);
        }

        // Configure project in workspace
        ProjectFileAccessProvider projectFileAccessProvider = getProjectFileAccessProvider();
        Revision configRevision;
        try
        {
            ProjectConfiguration currentConfig = ProjectStructure.getProjectConfiguration(projectId.toString(), null, null, projectFileAccessProvider, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE);
            ProjectConfigurationUpdateBuilder builder = ProjectConfigurationUpdateBuilder.newBuilder(projectFileAccessProvider, type, projectId.toString())
                    .withWorkspace(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE)
                    .withGroupId(groupId)
                    .withArtifactId(artifactId)
                    .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider);
            int defaultProjectStructureVersion = getDefaultProjectStructureVersion();
            if (currentConfig == null)
            {
                // No current project structure: build a new one
                configRevision = builder.withProjectStructureVersion(defaultProjectStructureVersion)
                        .withMessage("Build project structure")
                        .buildProjectStructure();
            }
            else
            {
                // Existing project structure: update
                if (currentConfig.getProjectType() != type)
                {
                    throw new LegendSDLCServerException("Mismatch between requested project type (" + type + ") and found project type (" + currentConfig.getProjectType() + ")", Status.BAD_REQUEST);
                }
                ProjectStructureVersion currentVersion = currentConfig.getProjectStructureVersion();
                if ((currentVersion == null) || (currentVersion.getVersion() < defaultProjectStructureVersion))
                {
                    builder.withProjectStructureVersion(defaultProjectStructureVersion).withProjectStructureExtensionVersion(null);
                }
                configRevision = builder.withMessage("Update project structure").updateProjectConfiguration();
            }
        }
        catch (Exception e)
        {
            // Try to delete the branch
            try
            {
                boolean deleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, projectId.getGitLabId(),
                        getUserWorkspaceBranchName(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), 30, 1_000);
                if (!deleted)
                {
                    LOGGER.error("Failed to delete workspace {} in project {}", workspaceId, projectId);
                }
            }
            catch (Exception ee)
            {
                // Possibly failed to delete branch - unfortunate, but ignore it
                LOGGER.error("Error deleting workspace {} in project {}", workspaceId, projectId, ee);
            }
            throw e;
        }

        // Submit workspace changes, if any, for review
        String reviewId;
        if (configRevision == null)
        {
            // No changes: nothing to submit
            reviewId = null;

            // Try to delete the branch
            try
            {
                boolean deleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, projectId.getGitLabId(),
                        getUserWorkspaceBranchName(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), 30, 1_000);
                if (!deleted)
                {
                    LOGGER.error("Failed to delete workspace {} in project {}", workspaceId, projectId);
                }
            }
            catch (Exception e)
            {
                // Possibly failed to delete branch - unfortunate, but ignore it
                LOGGER.error("Error deleting workspace {} in project {}", workspaceId, projectId, e);
            }
        }
        else
        {
            MergeRequest mergeRequest;
            try
            {
                mergeRequest = gitLabApi.getMergeRequestApi().createMergeRequest(projectId.getGitLabId(),
                        getUserWorkspaceBranchName(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE),
                        MASTER_BRANCH, "Project structure", "Set up project structure", null, null, null, null, true, false);
            }
            catch (Exception e)
            {
                // Try to delete the branch
                try
                {
                    boolean deleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, projectId.getGitLabId(),
                            getUserWorkspaceBranchName(workspaceId, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE), 30, 1_000);
                    if (!deleted)
                    {
                        LOGGER.error("Failed to delete workspace {} in project {}", workspaceId, projectId);
                    }
                }
                catch (Exception ee)
                {
                    // Possibly failed to delete branch - unfortunate, but ignore it
                    LOGGER.error("Error deleting workspace {} in project {}", workspaceId, projectId, ee);
                }
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to submit project configuration changes create a workspace for initial configuration of project " + id + " of type " + type,
                        () -> "Could not find workspace " + workspaceId + " project " + id + " of type " + type,
                        () -> "Failed to create a review for configuration of project " + id + " of type " + type);
            }
            reviewId = toStringIfNotNull(mergeRequest.getIid());
        }

        // Add tags
        Project finalProject;
        List<String> currentTags = currentProject.getTagList();
        if ((currentTags != null) && currentTags.stream().anyMatch(this::isLegendSDLCProjectTag))
        {
            // already has the necessary tag
            finalProject = fromGitLabProject(currentProject, projectId.getGitLabMode());
        }
        else
        {
            List<String> updatedTags = Lists.mutable.ofInitialCapacity((currentTags == null) ? 1 : (currentTags.size() + 1));
            if (currentTags != null)
            {
                updatedTags.addAll(currentTags);
            }
            updatedTags.add(getLegendSDLCProjectTag());
            org.gitlab4j.api.models.Project updatedProject;
            try
            {
                updatedProject = gitLabProjectApi.updateProject(new org.gitlab4j.api.models.Project()
                        .withId(currentProject.getId())
                        .withTagList(updatedTags));
            }
            catch (Exception e)
            {
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to import project " + id + " of type " + type,
                        () -> "Could not find project " + id + " of type " + type,
                        () -> "Failed to import project " + id + " of type " + type);
            }
            finalProject = fromGitLabProject(updatedProject, projectId.getGitLabMode());
        }
        return new ImportReport()
        {
            @Override
            public Project getProject()
            {
                return finalProject;
            }

            @Override
            public String getReviewId()
            {
                return reviewId;
            }
        };
    }

    @Override
    public void deleteProject(String id)
    {
        LegendSDLCServerException.validateNonNull(id, "id may not be null");
        try
        {
            GitLabProjectId projectId = parseProjectId(id);
            org.gitlab4j.api.models.Project currentProject = getPureGitLabProjectById(projectId);
            withRetries(() -> getGitLabApi(projectId.getGitLabMode()).getProjectApi().deleteProject(currentProject));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to delete project " + id,
                    () -> "Unknown project: " + id,
                    () -> "Failed to delete project " + id);
        }
    }

    @Override
    public void changeProjectName(String id, String newName)
    {
        LegendSDLCServerException.validateNonNull(id, "id may not be null");
        LegendSDLCServerException.validate(newName, n -> (n != null) && !n.isEmpty(), "newName may not be null or empty");
        try
        {
            GitLabProjectId projectId = parseProjectId(id);
            org.gitlab4j.api.models.Project currentProject = getPureGitLabProjectById(projectId);
            if (newName.equals(currentProject.getName()))
            {
                return;
            }
            org.gitlab4j.api.models.Project updatedProject = new org.gitlab4j.api.models.Project()
                    .withId(currentProject.getId())
                    .withName(newName);
            withRetries(() -> getGitLabApi(projectId.getGitLabMode()).getProjectApi().updateProject(updatedProject));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to change name of project " + id + " to \"" + newName + "\"",
                    () -> "Unknown project: " + id,
                    () -> "Failed to change name of project " + id + " to \"" + newName + "\"");
        }
    }

    @Override
    public void changeProjectDescription(String id, String newDescription)
    {
        LegendSDLCServerException.validateNonNull(id, "id may not be null");
        LegendSDLCServerException.validateNonNull(newDescription, "newDescription may not be null");
        try
        {
            GitLabProjectId projectId = parseProjectId(id);
            org.gitlab4j.api.models.Project currentProject = getPureGitLabProjectById(projectId);
            org.gitlab4j.api.models.Project updatedProject = new org.gitlab4j.api.models.Project()
                    .withId(currentProject.getId())
                    .withDescription(newDescription);
            withRetries(() -> getGitLabApi(projectId.getGitLabMode()).getProjectApi().updateProject(updatedProject));
        }
        catch (LegendSDLCServerException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to change the description of project " + id + " to \"" + newDescription + "\"",
                    () -> "Unknown project: " + id,
                    () -> "Failed to change the description of project " + id + " to \"" + newDescription + "\"");
        }
    }

    @Override
    public void updateProjectTags(String id, Iterable<String> tagsToRemove, Iterable<String> tagsToAdd)
    {
        LegendSDLCServerException.validateNonNull(id, "id may not be null");
        try
        {
            GitLabProjectId projectId = parseProjectId(id);

            org.gitlab4j.api.models.Project currentProject = getPureGitLabProjectById(projectId);
            List<String> currentTags = currentProject.getTagList();

            Set<String> toRemoveSet = (tagsToRemove == null) ? Collections.emptySet() : toLegendSDLCTagSet(tagsToRemove);
            Set<String> toAddSet = (tagsToAdd == null) ? Sets.mutable.empty() : toLegendSDLCTagSet(tagsToAdd);

            List<String> updatedTags = Lists.mutable.ofInitialCapacity(currentTags.size() + toAddSet.size() - toRemoveSet.size());
            for (String tag : currentTags)
            {
                if (!isLegendSDLCTag(tag) || !toRemoveSet.contains(tag))
                {
                    updatedTags.add(tag);
                    toAddSet.remove(tag);
                }
            }
            updatedTags.addAll(toAddSet);

            org.gitlab4j.api.models.Project updatedProject = new org.gitlab4j.api.models.Project()
                    .withId(currentProject.getId())
                    .withTagList(updatedTags);
            withRetries(() -> getGitLabApi(projectId.getGitLabMode()).getProjectApi().updateProject(updatedProject));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to update tags for project " + id,
                    () -> "Unknown project: " + id,
                    () -> "Failed to update tags for project " + id);
        }
    }

    @Override
    public void setProjectTags(String id, Iterable<String> tags)
    {
        LegendSDLCServerException.validateNonNull(id, "id may not be null");
        LegendSDLCServerException.validateNonNull(tags, "tags may not be null");

        try
        {
            GitLabProjectId projectId = parseProjectId(id);

            org.gitlab4j.api.models.Project currentProject = getPureGitLabProjectById(projectId);

            List<String> currentTags = currentProject.getTagList();
            Set<String> newTags = toLegendSDLCTagSet(tags);

            List<String> updatedTags = Lists.mutable.ofInitialCapacity(currentTags.size());
            for (String tag : currentTags)
            {
                if (!isLegendSDLCTag(tag) || newTags.remove(tag))
                {
                    updatedTags.add(tag);
                }
            }
            updatedTags.addAll(newTags);

            org.gitlab4j.api.models.Project updatedProject = new org.gitlab4j.api.models.Project()
                    .withId(currentProject.getId())
                    .withTagList(updatedTags);
            withRetries(() -> getGitLabApi(projectId.getGitLabMode()).getProjectApi().updateProject(updatedProject));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to set tags for project " + id,
                    () -> "Unknown project: " + id,
                    () -> "Failed to set tags for project " + id);
        }
    }

    @Override
    public AccessRole getCurrentUserAccessRole(String id)
    {
        LegendSDLCServerException.validateNonNull(id, "id may not be null");
        try
        {
            GitLabProjectId projectId = parseProjectId(id);
            org.gitlab4j.api.models.Project gitLabProject = withRetries(() -> getGitLabApi(projectId.getGitLabMode()).getProjectApi().getProject(projectId.getGitLabId()));
            if (!isLegendSDLCProject(gitLabProject))
            {
                throw new LegendSDLCServerException("Failed to get project " + id);
            }

            Permissions permissions = gitLabProject.getPermissions();
            if (permissions != null)
            {
                ProjectAccess projectAccess = permissions.getProjectAccess();
                AccessLevel projectAccessLevel = (projectAccess == null) ? null : projectAccess.getAccessLevel();
                if (projectAccessLevel != null)
                {
                    return new AccessRoleWrapper(projectAccessLevel);
                }

                ProjectAccess groupAccess = permissions.getGroupAccess();
                AccessLevel groupAccessLevel = (groupAccess == null) ? null : groupAccess.getAccessLevel();
                if (groupAccessLevel != null)
                {
                    return new AccessRoleWrapper(groupAccessLevel);
                }
            }
            return null;
        }
        catch (Exception e)
        {
            throw buildException(e, () -> "Failed to get project " + id);
        }
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

    private void validateProjectCreation(String name, String description, ProjectType type, String groupId, String artifactId)
    {
        ProjectCreationConfiguration projectCreationConfig = getProjectCreationConfiguration();
        if (projectCreationConfig != null)
        {
            if (projectCreationConfig.isDisallowedType(type))
            {
                String message = projectCreationConfig.getDisallowedTypeMessage(type);
                if (message == null)
                {
                    message = "Projects of type " + type + " may not be created through this server.";
                }
                throw new LegendSDLCServerException(message, Status.BAD_REQUEST);
            }

            Pattern groupIdPattern = projectCreationConfig.getGroupIdPattern();
            if ((groupIdPattern != null) && !groupIdPattern.matcher(groupId).matches())
            {
                throw new LegendSDLCServerException("groupId must match \"" + groupIdPattern.pattern() + "\", got: " + groupId, Status.BAD_REQUEST);
            }

            Pattern artifactIdPattern = projectCreationConfig.getArtifactIdPattern();
            if ((artifactIdPattern != null) && !artifactIdPattern.matcher(artifactId).matches())
            {
                throw new LegendSDLCServerException("artifactId must match \"" + artifactIdPattern.pattern() + "\", got: " + artifactId, Status.BAD_REQUEST);
            }
        }
    }

    private ProjectCreationConfiguration getProjectCreationConfiguration()
    {
        return (this.projectStructureConfig == null) ? null : this.projectStructureConfig.getProjectCreationConfiguration();
    }

    private Visibility getNewProjectVisibility()
    {
        Visibility visibility = (this.gitLabConfiguration == null) ? null : this.gitLabConfiguration.getNewProjectVisibility();
        return (visibility == null) ? DEFAULT_VISIBILITY : visibility;
    }

    private org.gitlab4j.api.models.Project getPureGitLabProjectById(GitLabProjectId projectId)
    {
        try
        {
            GitLabApi gitLabApi = getGitLabApi(projectId.getGitLabMode());
            org.gitlab4j.api.models.Project project = withRetries(() -> gitLabApi.getProjectApi().getProject(projectId.getGitLabId()));
            if (!isLegendSDLCProject(project))
            {
                throw new LegendSDLCServerException("Unknown project: " + projectId, Status.NOT_FOUND);
            }
            return project;
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to get project " + projectId,
                    () -> "Unknown project: " + projectId,
                    () -> "Failed to get project " + projectId);
        }
    }

    private boolean isLegendSDLCProject(org.gitlab4j.api.models.Project project)
    {
        if (project == null)
        {
            return false;
        }

        List<String> tags = project.getTagList();
        return (tags != null) && tags.stream().anyMatch(this::isLegendSDLCProjectTag);
    }

    private boolean isLegendSDLCProjectTag(String tag)
    {
        return getLegendSDLCProjectTag().equalsIgnoreCase(tag);
    }

    private String getLegendSDLCProjectTag()
    {
        String tag = this.gitLabConfig.getProjectTag();
        return (tag == null) ? DEFAULT_LEGEND_SDLC_PROJECT_TAG : tag;
    }

    private boolean isLegendSDLCTag(String tag)
    {
        if (tag == null)
        {
            return false;
        }
        String legendSDLCTag = getLegendSDLCProjectTag();
        return (tag.length() > (legendSDLCTag.length() + 1)) &&
                (tag.charAt(legendSDLCTag.length()) == '_') &&
                tag.regionMatches(true, 0, legendSDLCTag, 0, legendSDLCTag.length());
    }

    private String stripLegendSDLCTagPrefix(String tag)
    {
        return (tag == null) ? null : tag.substring(getLegendSDLCProjectTag().length() + 1);
    }

    private String addLegendSDLCTagPrefix(String tag)
    {
        return (tag == null) ? null : (getLegendSDLCProjectTag() + "_" + tag);
    }

    private Set<String> toLegendSDLCTagSet(Iterable<String> tags)
    {
        return Iterate.collect(tags, this::addLegendSDLCTagPrefix, Sets.mutable.empty());
    }

    private List<String> gitLabTagListToLegendSDLCTagList(List<String> gitLabTagList)
    {
        return ((gitLabTagList == null) || gitLabTagList.isEmpty()) ? Collections.emptyList() : ListIterate.collectIf(gitLabTagList, this::isLegendSDLCTag, this::stripLegendSDLCTagPrefix);
    }

    private Project fromGitLabProject(org.gitlab4j.api.models.Project project, GitLabMode mode)
    {
        return (project == null) ? null : new ProjectWrapper(project, mode);
    }

    private class ProjectWrapper implements Project
    {
        private final org.gitlab4j.api.models.Project gitLabProject;
        private final String projectId;
        private final ProjectType type;

        private ProjectWrapper(org.gitlab4j.api.models.Project gitLabProject, GitLabMode mode)
        {
            this.gitLabProject = gitLabProject;
            this.projectId = GitLabProjectId.getProjectIdString(mode, gitLabProject);
            this.type = getProjectTypeFromMode(mode);
        }

        @Override
        public String getProjectId()
        {
            return this.projectId;
        }

        @Override
        public String getName()
        {
            return this.gitLabProject.getName();
        }

        @Override
        public String getDescription()
        {
            return this.gitLabProject.getDescription();
        }

        @Override
        public List<String> getTags()
        {
            return gitLabTagListToLegendSDLCTagList(this.gitLabProject.getTagList());
        }

        @Override
        public ProjectType getProjectType()
        {
            return this.type;
        }
    }

    private static class AccessRoleWrapper implements AccessRole
    {
        private final String accessRole;

        private AccessRoleWrapper(AccessLevel accessLevel)
        {
            this.accessRole = accessLevel.name();
        }

        @Override
        public String getAccessRole()
        {
            return this.accessRole;
        }
    }
}
