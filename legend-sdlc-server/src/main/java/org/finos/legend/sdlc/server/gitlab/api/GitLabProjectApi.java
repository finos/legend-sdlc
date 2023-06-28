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

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.accessRole.AccessRole;
import org.finos.legend.sdlc.domain.model.project.accessRole.AuthorizableProjectAction;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectConfiguration;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectStructureVersion;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.server.domain.api.project.ProjectApi;
import org.finos.legend.sdlc.server.domain.api.project.ProjectConfigurationUpdater;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.tools.GitLabApiTools;
import org.finos.legend.sdlc.server.gitlab.tools.PagerTools;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.WorkspaceAccessType;
import org.finos.legend.sdlc.server.project.ProjectStructure;
import org.finos.legend.sdlc.server.project.ProjectStructurePlatformExtensions;
import org.finos.legend.sdlc.server.project.config.ProjectCreationConfiguration;
import org.finos.legend.sdlc.server.project.config.ProjectStructureConfiguration;
import org.finos.legend.sdlc.server.project.extension.ProjectStructureExtensionProvider;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.finos.legend.sdlc.server.tools.CallUntil;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.Pager;
import org.gitlab4j.api.ProtectedBranchesApi;
import org.gitlab4j.api.RepositoryApi;
import org.gitlab4j.api.UserApi;
import org.gitlab4j.api.models.AccessLevel;
import org.gitlab4j.api.models.Branch;
import org.gitlab4j.api.models.Member;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.Permissions;
import org.gitlab4j.api.models.ProjectAccess;
import org.gitlab4j.api.models.ProtectedTag;
import org.gitlab4j.api.models.User;
import org.gitlab4j.api.models.Visibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.ws.rs.core.Response.Status;

public class GitLabProjectApi extends GitLabApiWithFileAccess implements ProjectApi
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabProjectApi.class);

    private static final String DEFAULT_LEGEND_SDLC_PROJECT_TAG = "legend";
    private static final Visibility DEFAULT_VISIBILITY = Visibility.INTERNAL;

    private final ProjectStructureConfiguration projectStructureConfig;
    private final ProjectStructureExtensionProvider projectStructureExtensionProvider;
    private final ProjectStructurePlatformExtensions projectStructurePlatformExtensions;

    static final String PROJECT_CONFIGURATION_WORKSPACE_ID_PREFIX = "ProjectConfiguration_";

    @Inject
    public GitLabProjectApi(GitLabConfiguration gitLabConfiguration, GitLabUserContext userContext, ProjectStructureConfiguration projectStructureConfig, ProjectStructureExtensionProvider projectStructureExtensionProvider, BackgroundTaskProcessor backgroundTaskProcessor, ProjectStructurePlatformExtensions projectStructurePlatformExtensions)
    {
        super(gitLabConfiguration, userContext, backgroundTaskProcessor);
        this.projectStructureConfig = projectStructureConfig;
        this.projectStructureExtensionProvider = projectStructureExtensionProvider;
        this.projectStructurePlatformExtensions = projectStructurePlatformExtensions;
    }

    @Override
    public Project getProject(String id)
    {
        LegendSDLCServerException.validateNonNull(id, "id may not be null");
        GitLabProjectId projectId = parseProjectId(id);
        org.gitlab4j.api.models.Project gitLabProject = getLegendSDLCGitLabProject(projectId);
        return fromGitLabProject(gitLabProject);
    }

    @Override
    public List<Project> getProjects(boolean user, String search, Iterable<String> tags, Integer limit)
    {
        boolean limited = false;
        if (limit != null)
        {
            if (limit == 0)
            {
                return Collections.emptyList();
            }
            if (limit < 0)
            {
                throw new LegendSDLCServerException("Invalid limit: " + limit, Status.BAD_REQUEST);
            }
            limited = true;
        }
        try
        {
            Set<String> tagSet = (tags == null) ? Collections.emptySet() : toLegendSDLCTagSet(tags);
            Pager<org.gitlab4j.api.models.Project> pager = withRetries(() -> getGitLabApi().getProjectApi().getProjects(null, null, null, null, search, true, null, user, null, null, ITEMS_PER_PAGE));
            Stream<org.gitlab4j.api.models.Project> stream = PagerTools.stream(pager).filter(this::isLegendSDLCProject);
            if (!tagSet.isEmpty())
            {
                stream = stream.filter(p ->
                {
                    List<String> tagList = p.getTagList();
                    return (tagList != null) && Iterate.anySatisfy(p.getTagList(), tagSet::contains);
                });
            }
            if (limited)
            {
                stream = stream.limit(limit);
            }
            return stream.map(this::fromGitLabProject).collect(PagerTools.listCollector(pager, limit));
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
                List<String> tagList = (tags == null) ? Collections.emptyList() : Lists.mutable.withAll(tags);
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
        LegendSDLCServerException.validate(groupId, ProjectStructure::isValidGroupId, g -> "Invalid groupId: " + g);
        LegendSDLCServerException.validate(artifactId, ProjectStructure::isValidArtifactId, a -> "Invalid artifactId: " + a);
        if (type != null)
        {
            LegendSDLCServerException.validate(type, ProjectStructure::isValidProjectType, t -> "Invalid type: " + t);
        }

        validateProjectCreation(groupId, artifactId);

        GitLabApi gitLabApi = getGitLabApi();

        // Create project
        org.gitlab4j.api.models.Project gitLabProject;
        try
        {
            org.gitlab4j.api.ProjectApi projectApi = gitLabApi.getProjectApi();
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
            gitLabProject = projectApi.createProject(gitLabProjectSpec);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to create project " + name,
                    () -> "Failed to create project: " + name,
                    () -> "Failed to create project: " + name);
        }
        if (gitLabProject == null)
        {
            throw new LegendSDLCServerException("Failed to create project: " + name);
        }
        Project project = fromGitLabProject(gitLabProject);

        // try to ensure current user is a maintainer (or higher), but proceed even if we fail
        AccessLevel minUserAccessLevel = AccessLevel.MAINTAINER;
        if (!tryEnsureMinAccessLevel(gitLabApi, gitLabProject, minUserAccessLevel))
        {
            LOGGER.warn("Created project {} but could not set access level for {} to {} - trying to proceed anyway", project.getProjectId(), getCurrentUser(), minUserAccessLevel);
        }

        // protect default branch
        AccessLevel pushAccessLevel = AccessLevel.NONE;
        AccessLevel mergeAccessLevel = AccessLevel.MAINTAINER;
        String defaultBranchName = getDefaultBranch(gitLabProject);
        try
        {
            ProtectedBranchesApi protectedBranchesApi = gitLabApi.getProtectedBranchesApi();
            withRetries(() -> protectedBranchesApi.protectBranch(gitLabProject.getId(), defaultBranchName, pushAccessLevel, mergeAccessLevel));
        }
        catch (Exception e)
        {
            LOGGER.warn("Error trying to protect branch {} in project {} (push: {}, merge: {}) - trying to proceed anyway", defaultBranchName, project.getProjectId(), pushAccessLevel, mergeAccessLevel, e);
        }

        // build project structure
        try
        {
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
            ProjectStructure.newUpdateBuilder(getProjectFileAccessProvider(), project.getProjectId(), configUpdater)
                    .withMessage("Build project structure")
                    .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                    .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions)
                    .build();
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to set up project structure for newly created project " + project.getProjectId(),
                    () -> "Error setting up project structure for newly created project " + project.getProjectId(),
                    () -> "Error setting up project structure for newly created project " + project.getProjectId());
        }

        return project;
    }

    private boolean tryEnsureMinAccessLevel(GitLabApi gitLabApi, org.gitlab4j.api.models.Project project, AccessLevel accessLevel)
    {
        int projectId = project.getId();

        User currentUser;
        try
        {
            UserApi userApi = gitLabApi.getUserApi();
            currentUser = withRetries(userApi::getCurrentUser);
        }
        catch (Exception e)
        {
            LOGGER.warn("Error trying to set access level for {} in project {} to {}: could not get current user", getCurrentUser(), projectId, accessLevel.name(), e);
            return false;
        }

        org.gitlab4j.api.ProjectApi projectApi = gitLabApi.getProjectApi();
        int userId = currentUser.getId();
        CallUntil<AccessLevel, ?> callUntil = CallUntil.callUntil(() ->
        {
            try
            {
                Member member;
                try
                {
                    member = withRetries(() -> projectApi.getMember(projectId, userId));
                }
                catch (GitLabApiException e)
                {
                    if (!GitLabApiTools.isNotFoundGitLabApiException(e))
                    {
                        throw e;
                    }
                    return projectApi.addMember(projectId, userId, accessLevel).getAccessLevel();
                }
                return accessLevelAtLeast(member.getAccessLevel(), accessLevel) ?
                        member.getAccessLevel() :
                        projectApi.updateMember(projectId, userId, accessLevel).getAccessLevel();
            }
            catch (Exception e)
            {
                LOGGER.warn("Error trying to set access level for {} in project {} to {}", getCurrentUser(), projectId, accessLevel.name(), e);
                return null;
            }
        }, al -> accessLevelAtLeast(al, accessLevel), 10, 500);
        if (!callUntil.succeeded())
        {
            AccessLevel result = callUntil.getResult();
            LOGGER.warn("Failed to set access level for {} in project {} to {} after {} attempts: access level {}", getCurrentUser(), projectId, accessLevel.name(), callUntil.getTryCount(), (result == null) ? null : result.name());
            return false;
        }
        LOGGER.debug("Set access level for {} in project {} to {} after {} attempts", getCurrentUser(), projectId, accessLevel.name(), callUntil.getTryCount());
        return true;
    }

    private boolean accessLevelAtLeast(AccessLevel accessLevel, AccessLevel minAccessLevel)
    {
        return (accessLevel != null) && (accessLevel.toValue() >= minAccessLevel.toValue());
    }

    @Override
    public ImportReport importProject(String id, ProjectType type, String groupId, String artifactId)
    {
        LegendSDLCServerException.validateNonNull(id, "id may not be null");
        LegendSDLCServerException.validate(groupId, ProjectStructure::isValidGroupId, g -> "Invalid groupId: " + g);
        LegendSDLCServerException.validate(artifactId, ProjectStructure::isValidArtifactId, a -> "Invalid artifactId: " + a);
        if (type != null)
        {
            LegendSDLCServerException.validate(type, ProjectStructure::isValidProjectType, t -> "Invalid type: " + t);
        }

        // Get project ID
        GitLabProjectId projectId = id.chars().allMatch(Character::isDigit) ?
                GitLabProjectId.newProjectId(getGitLabConfiguration().getProjectIdPrefix(), Integer.parseInt(id)) :
                parseProjectId(id);

        // Find project
        GitLabApi gitLabApi = getGitLabApi();
        org.gitlab4j.api.ProjectApi gitLabProjectApi = gitLabApi.getProjectApi();
        org.gitlab4j.api.models.Project currentProject;
        try
        {
            currentProject = withRetries(() -> gitLabProjectApi.getProject(projectId.getGitLabId()));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access project " + id,
                    () -> "Could not find project " + id,
                    () -> "Failed to access project " + id);
        }

        // Create a workspace for project configuration
        RepositoryApi repositoryApi = gitLabApi.getRepositoryApi();
        String workspaceId = PROJECT_CONFIGURATION_WORKSPACE_ID_PREFIX + getRandomIdString();
        WorkspaceSpecification workspaceSpec = WorkspaceSpecification.newWorkspaceSpecification(workspaceId, WorkspaceType.USER, WorkspaceAccessType.WORKSPACE);
        Branch workspaceBranch;
        String defaultBranch = getDefaultBranch(projectId);
        try
        {
            workspaceBranch = GitLabApiTools.createBranchFromSourceBranchAndVerify(repositoryApi, projectId.getGitLabId(), getWorkspaceBranchName(workspaceSpec), defaultBranch, 30, 1_000);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to create a workspace for initial configuration of project " + id,
                    () -> "Could not find project " + id,
                    () -> "Failed to create workspace for initial configuration of project " + id);
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
            ProjectConfiguration currentConfig = ProjectStructure.getProjectConfiguration(projectId.toString(), SourceSpecification.projectSourceSpecification(), null, projectFileAccessProvider);
            ProjectConfigurationUpdater configUpdater = ProjectConfigurationUpdater.newUpdater()
                    .withProjectType(type)
                    .withGroupId(groupId)
                    .withArtifactId(artifactId);
            ProjectStructure.UpdateBuilder builder = ProjectStructure.newUpdateBuilder(projectFileAccessProvider, projectId.toString(), configUpdater)
                    .withWorkspace(workspaceSpec)
                    .withProjectStructureExtensionProvider(this.projectStructureExtensionProvider)
                    .withProjectStructurePlatformExtensions(this.projectStructurePlatformExtensions);
            int defaultProjectStructureVersion = getDefaultProjectStructureVersion();
            if (currentConfig == null)
            {
                // No current project structure: build a new one
                configUpdater.setProjectStructureVersion(defaultProjectStructureVersion);
                if (this.projectStructureExtensionProvider != null && type != ProjectType.EMBEDDED)
                {
                    configUpdater.setProjectStructureExtensionVersion(this.projectStructureExtensionProvider.getLatestVersionForProjectStructureVersion(defaultProjectStructureVersion));
                }
                configRevision = builder.withMessage("Build project structure").build();
            }
            else
            {
                ProjectStructureVersion currentVersion = currentConfig.getProjectStructureVersion();
                if ((currentVersion == null) || (currentVersion.getVersion() < defaultProjectStructureVersion))
                {
                    configUpdater.setProjectStructureVersion(defaultProjectStructureVersion);
                    if (this.projectStructureExtensionProvider != null && (type != null ? type : currentConfig.getProjectType()) != ProjectType.EMBEDDED)
                    {
                        configUpdater.setProjectStructureExtensionVersion(this.projectStructureExtensionProvider.getLatestVersionForProjectStructureVersion(defaultProjectStructureVersion));
                    }
                }
                configRevision = builder.withMessage("Update project structure").update();
            }
        }
        catch (Exception e)
        {
            // Try to delete the branch in case of exception
            deleteWorkspace(projectId, repositoryApi, workspaceSpec);
            throw e;
        }

        // Submit workspace changes, if any, for review
        String reviewId;
        if (configRevision == null)
        {
            // No changes: nothing to submit
            reviewId = null;

            // Try to delete the branch
            deleteWorkspace(projectId, repositoryApi, workspaceSpec);
        }
        else
        {
            MergeRequest mergeRequest;
            try
            {
                mergeRequest = gitLabApi.getMergeRequestApi().createMergeRequest(projectId.getGitLabId(), getWorkspaceBranchName(workspaceSpec), defaultBranch, "Project structure", "Set up project structure", null, null, null, null, true, false);
            }
            catch (Exception e)
            {
                // Try to delete the branch in case of exception
                deleteWorkspace(projectId, repositoryApi, workspaceSpec);
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to submit project configuration changes create a workspace for initial configuration of project " + id,
                        () -> "Could not find " + getReferenceInfo(id, workspaceSpec),
                        () -> "Failed to create a review for configuration of project " + id);
            }
            reviewId = toStringIfNotNull(mergeRequest.getIid());
        }

        // Add tags
        Project finalProject;
        if (hasLegendSDLCProjectTag(currentProject))
        {
            // already has the necessary tag
            finalProject = fromGitLabProject(currentProject);
        }
        else
        {
            List<String> currentTags = currentProject.getTagList();
            List<String> updatedTags = Lists.mutable.ofInitialCapacity((currentTags == null) ? 1 : (currentTags.size() + 1));
            if (currentTags != null)
            {
                updatedTags.addAll(currentTags);
            }
            updatedTags.add(getLegendSDLCProjectTag());
            org.gitlab4j.api.models.Project updatedProject;
            try
            {
                updatedProject = gitLabProjectApi.updateProject(new org.gitlab4j.api.models.Project().withId(currentProject.getId()).withTagList(updatedTags));
            }
            catch (Exception e)
            {
                // Try to delete the branch in case of exception
                deleteWorkspace(projectId, repositoryApi, workspaceSpec);
                throw buildException(e,
                        () -> "User " + getCurrentUser() + " is not allowed to import project " + id,
                        () -> "Could not find project " + id,
                        () -> "Failed to import project " + id);
            }
            finalProject = fromGitLabProject(updatedProject);
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

    private void deleteWorkspace(GitLabProjectId projectId, RepositoryApi repositoryApi, WorkspaceSpecification workspaceSpec)
    {
        try
        {
            boolean deleted = GitLabApiTools.deleteBranchAndVerify(repositoryApi, projectId.getGitLabId(), getWorkspaceBranchName(workspaceSpec), 30, 1_000);
            if (!deleted)
            {
                LOGGER.error("Failed to delete {} {} {} in project {}", workspaceSpec.getType().getLabel(), workspaceSpec.getAccessType().getLabel(), workspaceSpec.getId(), projectId);
            }
        }
        catch (Exception e)
        {
            // Possibly failed to delete branch - unfortunate, but ignore it
            LOGGER.error("Error deleting {} {} {} in project {}", workspaceSpec.getType().getLabel(), workspaceSpec.getAccessType().getLabel(), workspaceSpec.getId(), projectId, e);
        }
    }

    @Override
    public void deleteProject(String id)
    {
        LegendSDLCServerException.validateNonNull(id, "id may not be null");
        try
        {
            GitLabProjectId projectId = parseProjectId(id);
            org.gitlab4j.api.models.Project currentProject = getLegendSDLCGitLabProject(projectId);
            withRetries(() -> getGitLabApi().getProjectApi().deleteProject(currentProject));
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
            org.gitlab4j.api.models.Project currentProject = getLegendSDLCGitLabProject(projectId);
            if (newName.equals(currentProject.getName()))
            {
                return;
            }
            org.gitlab4j.api.models.Project updatedProject = new org.gitlab4j.api.models.Project().withId(currentProject.getId()).withName(newName);
            withRetries(() -> getGitLabApi().getProjectApi().updateProject(updatedProject));
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
            org.gitlab4j.api.models.Project currentProject = getLegendSDLCGitLabProject(projectId);
            org.gitlab4j.api.models.Project updatedProject = new org.gitlab4j.api.models.Project().withId(currentProject.getId()).withDescription(newDescription);
            withRetries(() -> getGitLabApi().getProjectApi().updateProject(updatedProject));
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

            org.gitlab4j.api.models.Project currentProject = getLegendSDLCGitLabProject(projectId);
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

            org.gitlab4j.api.models.Project updatedProject = new org.gitlab4j.api.models.Project().withId(currentProject.getId()).withTagList(updatedTags);
            withRetries(() -> getGitLabApi().getProjectApi().updateProject(updatedProject));
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

            org.gitlab4j.api.models.Project currentProject = getLegendSDLCGitLabProject(projectId);

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

            org.gitlab4j.api.models.Project updatedProject = new org.gitlab4j.api.models.Project().withId(currentProject.getId()).withTagList(updatedTags);
            withRetries(() -> getGitLabApi().getProjectApi().updateProject(updatedProject));
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
            org.gitlab4j.api.models.Project gitLabProject = withRetries(() -> getGitLabApi().getProjectApi().getProject(projectId.getGitLabId()));
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

    @Override
    public Set<AuthorizableProjectAction> checkUserAuthorizedActions(String id, Set<AuthorizableProjectAction> actions)
    {
        GitLabProjectId projectId = parseProjectId(id);
        org.gitlab4j.api.models.Project gitLabProject = getLegendSDLCGitLabProject(projectId);
        AccessLevel userLevel = getUserAccess(gitLabProject);
        return (userLevel == null) ? Collections.emptySet() : Iterate.select(actions, a -> (a != null) && checkUserAction(projectId, a, userLevel), EnumSet.noneOf(AuthorizableProjectAction.class));
    }

    @Override
    public boolean checkUserAuthorizedAction(String id, AuthorizableProjectAction action)
    {
        GitLabProjectId projectId = parseProjectId(id);
        org.gitlab4j.api.models.Project gitLabProject = getLegendSDLCGitLabProject(projectId);
        AccessLevel userLevel = getUserAccess(gitLabProject);
        return (userLevel != null) && checkUserAction(projectId, action, userLevel);
    }

    private AccessLevel getUserAccess(org.gitlab4j.api.models.Project gitLabProject)
    {
        Permissions permissions = gitLabProject.getPermissions();
        if (permissions != null)
        {
            ProjectAccess projectAccess = permissions.getProjectAccess();
            AccessLevel projectAccessLevel = (projectAccess == null) ? null : projectAccess.getAccessLevel();
            if (projectAccessLevel != null)
            {
                return projectAccessLevel;
            }

            ProjectAccess groupAccess = permissions.getGroupAccess();
            return (groupAccess == null) ? null : groupAccess.getAccessLevel();
        }
        return null;
    }

    private boolean checkUserAction(GitLabProjectId projectId, AuthorizableProjectAction action, AccessLevel accessLevel)
    {
        switch (action)
        {
            case CREATE_VERSION:
            {
                return checkUserReleasePermission(projectId, accessLevel);
            }
            case COMMIT_REVIEW:
            {
                return defaultMergeReviewAccess(accessLevel);
            }
            case SUBMIT_REVIEW:
            {
                return defaultSubmitReviewAccess(accessLevel);
            }
            case CREATE_WORKSPACE:
            {
                return defaultCreateWorkspaceAccess(accessLevel);
            }
            default:
            {
                return false;
            }
        }
    }

    private boolean checkUserReleasePermission(GitLabProjectId projectId, AccessLevel accessLevel)
    {
        try
        {
            List<ProtectedTag> protectedTags = withRetries(() -> getGitLabApi().getTagsApi().getProtectedTags(projectId.getGitLabId()));
            if (protectedTags == null || protectedTags.isEmpty())
            {
                // By default, user can perform a release if the user has developer access or above
                // See https://docs.gitlab.com/ee/user/permissions.html#release-permissions-with-protected-tags
                return defaultReleaseAction(accessLevel);
            }
            for (ProtectedTag tag : LazyIterate.select(protectedTags, a -> a.getName().startsWith("release") || a.getName().startsWith("version")))
            {
                if (tag.getCreateAccessLevels().isEmpty())
                {
                    return defaultReleaseAction(accessLevel);
                }
                // with the release protected tag the user must have the min access_level
                List<ProtectedTag.CreateAccessLevel> matchedTags = ListIterate.select(tag.getCreateAccessLevels(), a -> a.getAccess_level().value >= accessLevel.value);
                // if the matchedTags are empty or null user access does not match any of the protected tags
                if (matchedTags.isEmpty())
                {
                    return defaultReleaseAction(accessLevel);
                }

                // User does not meet all criteria not authorized for the action
                if (matchedTags.size() != tag.getCreateAccessLevels().size())
                {
                    return false;
                }
            }
        }
        catch (Exception e)
        {
            throw buildException(e, () -> "Failed to get protected tags for " + projectId.getGitLabId());
        }
        return false;
    }

    private boolean defaultReleaseAction(AccessLevel accessLevel)
    {
        return (accessLevel.value >= GitlabProjectAccess.CREATE_VERSION);
    }

    /**
     * Default access level needed to create a workspace or a branch https://docs.gitlab.com/ee/user/permissions.html
     *
     * @param userAccessLevel the access level of the user
     * @return action and whether the user is authorized for that
     */
    private boolean defaultCreateWorkspaceAccess(AccessLevel userAccessLevel)
    {
        return (userAccessLevel.value >= GitlabProjectAccess.CREATE_WORKSPACE);
    }

    /**
     * Default access level needed to submit a review https://docs.gitlab.com/ee/user/permissions.html
     *
     * @param userAccessLevel access level  of the user
     * @return projectAuthorizationState
     */
    private boolean defaultSubmitReviewAccess(AccessLevel userAccessLevel)
    {
        return (userAccessLevel.value >= GitlabProjectAccess.SUBMIT_REVIEW);
    }

    /**
     * Default access level needed to merge an approved review https://docs.gitlab.com/ee/user/permissions.html
     *
     * @param userAccessLevel access level  of the user
     * @return projectAuthorizationState
     */
    private boolean defaultMergeReviewAccess(AccessLevel userAccessLevel)
    {
        return (userAccessLevel.value >= GitlabProjectAccess.MERGE_REVIEW);
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

    private void validateProjectCreation(String groupId, String artifactId)
    {
        ProjectCreationConfiguration projectCreationConfig = getProjectCreationConfiguration();
        if (projectCreationConfig != null)
        {
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
        Visibility visibility = (this.getGitLabConfiguration() == null) ? null : this.getGitLabConfiguration().getNewProjectVisibility();
        return (visibility == null) ? DEFAULT_VISIBILITY : visibility;
    }

    private org.gitlab4j.api.models.Project getLegendSDLCGitLabProject(GitLabProjectId projectId)
    {
        org.gitlab4j.api.models.Project gitLabProject;
        try
        {
            org.gitlab4j.api.ProjectApi projectApi = getGitLabApi().getProjectApi();
            gitLabProject = withRetries(() -> projectApi.getProject(projectId.getGitLabId()));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to get project " + projectId,
                    () -> "Unknown project: " + projectId,
                    () -> "Failed to get project " + projectId);
        }
        if (!isLegendSDLCProject(gitLabProject))
        {
            throw new LegendSDLCServerException("Unknown project: " + projectId, Status.NOT_FOUND, new RuntimeException("GitLab project " + projectId.getGitLabId() + " exists but is not a Legend SDLC project"));
        }
        return gitLabProject;
    }

    private boolean isLegendSDLCProject(org.gitlab4j.api.models.Project project)
    {
        return (project != null) && hasLegendSDLCProjectTag(project);
    }

    private boolean hasLegendSDLCProjectTag(org.gitlab4j.api.models.Project project)
    {
        List<String> tags = project.getTagList();
        return (tags != null) && Iterate.anySatisfy(tags, getLegendSDLCProjectTag()::equalsIgnoreCase);
    }

    private String getLegendSDLCProjectTag()
    {
        String tag = this.getGitLabConfiguration().getProjectTag();
        return (tag == null) ? DEFAULT_LEGEND_SDLC_PROJECT_TAG : tag;
    }

    private boolean isLegendSDLCTag(String tag)
    {
        if (tag == null)
        {
            return false;
        }
        String legendSDLCTag = getLegendSDLCProjectTag();
        return (tag.length() > (legendSDLCTag.length() + 1)) && (tag.charAt(legendSDLCTag.length()) == '_') && tag.regionMatches(true, 0, legendSDLCTag, 0, legendSDLCTag.length());
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

    private Project fromGitLabProject(org.gitlab4j.api.models.Project project)
    {
        return (project == null) ? null : new ProjectWrapper(project, GitLabProjectId.getProjectIdString(this.getGitLabConfiguration().getProjectIdPrefix(), project));
    }

    private class ProjectWrapper implements Project
    {
        private final org.gitlab4j.api.models.Project gitLabProject;
        private final String projectId;

        private ProjectWrapper(org.gitlab4j.api.models.Project gitLabProject, String projectId)
        {
            this.gitLabProject = gitLabProject;
            this.projectId = projectId;
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
        @JsonIgnore
        public ProjectType getProjectType()
        {
            return null;
        }

        @Override
        public String getWebUrl()
        {
            return this.gitLabProject.getWebUrl();
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

    private static class GitlabProjectAccess
    {
        public static int MERGE_REVIEW = AccessLevel.MAINTAINER.value;
        public static int CREATE_WORKSPACE = AccessLevel.DEVELOPER.value;
        public static int SUBMIT_REVIEW = AccessLevel.DEVELOPER.value;
        public static int CREATE_VERSION = AccessLevel.DEVELOPER.value;
    }
}
