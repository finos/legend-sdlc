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

import org.finos.legend.sdlc.domain.model.patch.Patch;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.review.ReviewState;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.revision.RevisionAlias;
import org.finos.legend.sdlc.domain.model.user.User;
import org.finos.legend.sdlc.domain.model.version.Version;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.project.source.PatchSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.ProjectSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecificationConsumer;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecificationVisitor;
import org.finos.legend.sdlc.server.domain.api.project.source.VersionSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.server.domain.api.workspace.PatchWorkspaceSource;
import org.finos.legend.sdlc.server.domain.api.workspace.ProjectWorkspaceSource;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSource;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSourceConsumer;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSourceVisitor;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabAuthException;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.tools.GitLabApiTools;
import org.finos.legend.sdlc.server.gitlab.tools.PagerTools;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.WorkspaceAccessType;
import org.finos.legend.sdlc.server.tools.StringTools;
import org.finos.legend.sdlc.server.tools.ThrowingRunnable;
import org.finos.legend.sdlc.server.tools.ThrowingSupplier;
import org.finos.legend.sdlc.tools.entity.EntityPaths;
import org.gitlab4j.api.CommitsApi;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.MergeRequestApi;
import org.gitlab4j.api.ProjectApi;
import org.gitlab4j.api.models.AbstractUser;
import org.gitlab4j.api.models.Commit;
import org.gitlab4j.api.models.CommitRef;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.Release;
import org.gitlab4j.api.models.ReleaseParams;
import org.gitlab4j.api.models.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

abstract class BaseGitLabApi
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BaseGitLabApi.class);

    private static final Random RANDOM = new Random();
    private static final Encoder RANDOM_ID_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_RETRY_WAIT_INTERVAL_MILLIS = 1000L;
    private static final LongUnaryOperator RETRY_WAIT_INTERVAL_UPDATER = w -> w + 1000L;

    private static final String WORKSPACE_BRANCH_PREFIX = "workspace";
    private static final String CONFLICT_RESOLUTION_BRANCH_PREFIX = "resolution";
    private static final String BACKUP_BRANCH_PREFIX = "backup";
    private static final String GROUP_WORKSPACE_BRANCH_PREFIX = "group";
    private static final String GROUP_CONFLICT_RESOLUTION_BRANCH_PREFIX = "group-resolution";
    private static final String GROUP_BACKUP_BRANCH_PREFIX = "group-backup";
    private static final String PATCH_WORKSPACE_BRANCH_PREFIX = "patch";

    private static final String TEMPORARY_BRANCH_PREFIX = "tmp";
    private static final String PATCH_BRANCH_PREFIX = "patch/main";

    private static final Pattern WORKSPACE_BRANCH_PATTERN = Pattern.compile("(" + PATCH_WORKSPACE_BRANCH_PREFIX + "/(?<patchVersion>\\d++\\.\\d++\\.\\d++)/)?(?<type>" + WORKSPACE_BRANCH_PREFIX + "|" + GROUP_WORKSPACE_BRANCH_PREFIX + "|" + CONFLICT_RESOLUTION_BRANCH_PREFIX + "|" + GROUP_CONFLICT_RESOLUTION_BRANCH_PREFIX + "|" + BACKUP_BRANCH_PREFIX + "|" + GROUP_BACKUP_BRANCH_PREFIX + ")/((?<user>[^/]++)/)?(?<id>[^/]++)");

    private static final String MR_STATE_OPENED = "opened";
    private static final String MR_STATE_CLOSED = "closed";
    private static final String MR_STATE_LOCKED = "locked";
    private static final String MR_STATE_MERGED = "merged";

    private static final String MASTER_BRANCH = "master";

    protected static final int ITEMS_PER_PAGE = 100;

    protected static final char BRANCH_DELIMITER = '/';

    protected static final String RELEASE_TAG_PREFIX = "release-";

    private final GitLabConfiguration gitLabConfiguration;
    private final GitLabUserContext userContext;

    protected BaseGitLabApi(GitLabConfiguration gitLabConfiguration, GitLabUserContext userContext)
    {
        this.gitLabConfiguration = gitLabConfiguration;
        this.userContext = userContext;
    }

    protected GitLabConfiguration getGitLabConfiguration()
    {
        return this.gitLabConfiguration;
    }

    protected String getCurrentUser()
    {
        return this.userContext.getCurrentUser();
    }

    protected GitLabProjectId parseProjectId(String projectId)
    {
        GitLabProjectId gitLabProjectId;
        try
        {
            gitLabProjectId = GitLabProjectId.parseProjectId(projectId);
        }
        catch (Exception e)
        {
            throw new LegendSDLCServerException("Invalid project id: \"" + projectId + "\"", Status.BAD_REQUEST, e);
        }
        if (!Objects.equals(this.gitLabConfiguration.getProjectIdPrefix(), gitLabProjectId.getPrefix()))
        {
            throw new LegendSDLCServerException("Invalid project id: \"" + projectId + "\"", Status.BAD_REQUEST);
        }
        return gitLabProjectId;
    }

    protected GitLabApi getGitLabApi()
    {
        try
        {
            return this.userContext.getGitLabAPI();
        }
        catch (LegendSDLCServerException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            StringBuilder message = new StringBuilder("Error getting GitLabApi for user ").append(getCurrentUser());
            String detail = (e instanceof GitLabAuthException) ? ((GitLabAuthException) e).getDetail() : e.getMessage();
            if (detail != null)
            {
                message.append(": ").append(detail);
            }
            throw new LegendSDLCServerException(message.toString(), e);
        }
    }

    @Deprecated
    protected static boolean isValidEntityName(String string)
    {
        return EntityPaths.isValidEntityName(string);
    }

    @Deprecated
    protected static boolean isValidEntityPath(String string)
    {
        return EntityPaths.isValidEntityPath(string);
    }

    @Deprecated
    protected static boolean isValidPackagePath(String string)
    {
        return EntityPaths.isValidPackagePath(string);
    }

    @Deprecated
    protected static boolean isValidClassifierPath(String string)
    {
        return EntityPaths.isValidClassifierPath(string);
    }

    @Deprecated
    protected static boolean isValidPackageableElementPath(String string)
    {
        return (string != null) && string.matches("^\\w++(::\\w++)*+[\\w$]*+$");
    }

    protected static String getWorkspaceBranchNamePrefix(WorkspaceSpecification workspaceSpec)
    {
        String basePrefix = getWorkspaceBranchNamePrefix(workspaceSpec.getType(), workspaceSpec.getAccessType());
        return workspaceSpec.getSource().visit(new WorkspaceSourceVisitor<String>()
        {
            @Override
            public String visit(ProjectWorkspaceSource source)
            {
                return basePrefix;
            }

            @Override
            public String visit(PatchWorkspaceSource source)
            {
                StringBuilder builder = new StringBuilder(getPatchWorkspaceBranchPrefix()).append(BRANCH_DELIMITER);
                source.getPatchVersionId().appendVersionIdString(builder).append(BRANCH_DELIMITER);
                return builder.append(basePrefix).toString();
            }
        });
    }

    protected static String getWorkspaceBranchNamePrefix(WorkspaceType workspaceType, WorkspaceAccessType workspaceAccessType)
    {
        assert workspaceAccessType != null : "Workspace access type has been used but it is null, which should not be the case";

        switch (workspaceType)
        {
            case GROUP:
            {
                switch (workspaceAccessType)
                {
                    case WORKSPACE:
                    {
                        return GROUP_WORKSPACE_BRANCH_PREFIX;
                    }
                    case CONFLICT_RESOLUTION:
                    {
                        return GROUP_CONFLICT_RESOLUTION_BRANCH_PREFIX;
                    }
                    case BACKUP:
                    {
                        return GROUP_BACKUP_BRANCH_PREFIX;
                    }
                    default:
                    {
                        throw new RuntimeException("Unknown workspace access type: " + workspaceAccessType);
                    }
                }
            }
            case USER:
            {
                switch (workspaceAccessType)
                {
                    case WORKSPACE:
                    {
                        return WORKSPACE_BRANCH_PREFIX;
                    }
                    case CONFLICT_RESOLUTION:
                    {
                        return CONFLICT_RESOLUTION_BRANCH_PREFIX;
                    }
                    case BACKUP:
                    {
                        return BACKUP_BRANCH_PREFIX;
                    }
                    default:
                    {
                        throw new RuntimeException("Unknown workspace access type: " + workspaceAccessType);
                    }
                }
            }
            default:
            {
                throw new RuntimeException("Unknown workspace type: " + workspaceType);
            }
        }
    }

    protected static String getPatchWorkspaceBranchPrefix()
    {
        return PATCH_WORKSPACE_BRANCH_PREFIX;
    }

    protected static WorkspaceSpecification parseWorkspaceBranchName(String branchName)
    {
        if (branchName == null)
        {
            return null;
        }

        Matcher matcher = WORKSPACE_BRANCH_PATTERN.matcher(branchName);
        if (!matcher.matches())
        {
            return null;
        }

        String patchVersionString = matcher.group("patchVersion");
        String typeString = matcher.group("type");
        String userId = matcher.group("user");
        String workspaceId = matcher.group("id");

        WorkspaceSource source;
        if (patchVersionString == null)
        {
            source = WorkspaceSource.projectWorkspaceSource();
        }
        else
        {
            VersionId versionId;
            try
            {
                versionId = VersionId.parseVersionId(patchVersionString);
            }
            catch (Exception ignore)
            {
                // not a valid version string
                return null;
            }
            source = WorkspaceSource.patchWorkspaceSource(versionId);
        }

        WorkspaceType type;
        WorkspaceAccessType accessType;
        switch (typeString)
        {
            case WORKSPACE_BRANCH_PREFIX:
            {
                type = WorkspaceType.USER;
                accessType = WorkspaceAccessType.WORKSPACE;
                break;
            }
            case CONFLICT_RESOLUTION_BRANCH_PREFIX:
            {
                type = WorkspaceType.USER;
                accessType = WorkspaceAccessType.CONFLICT_RESOLUTION;
                break;
            }
            case BACKUP_BRANCH_PREFIX:
            {
                type = WorkspaceType.USER;
                accessType = WorkspaceAccessType.BACKUP;
                break;
            }
            case GROUP_WORKSPACE_BRANCH_PREFIX:
            {
                type = WorkspaceType.GROUP;
                accessType = WorkspaceAccessType.WORKSPACE;
                break;
            }
            case GROUP_CONFLICT_RESOLUTION_BRANCH_PREFIX:
            {
                type = WorkspaceType.GROUP;
                accessType = WorkspaceAccessType.CONFLICT_RESOLUTION;
                break;
            }
            case GROUP_BACKUP_BRANCH_PREFIX:
            {
                type = WorkspaceType.GROUP;
                accessType = WorkspaceAccessType.BACKUP;
                break;
            }
            default:
            {
                // This should never happen
                return null;
            }
        }

        // For user workspaces, userId must be non-null; for others, userId must be null
        if ((type == WorkspaceType.USER) == (userId == null))
        {
            return null;
        }
        return WorkspaceSpecification.newWorkspaceSpecification(workspaceId, type, accessType, source, userId);
    }

    protected String getRef(GitLabProjectId projectId, SourceSpecification sourceSpec)
    {
        return getRef(sourceSpec, () -> getDefaultBranch(projectId));
    }

    protected String getBranchName(GitLabProjectId projectId, SourceSpecification sourceSpec)
    {
        return getBranchName(sourceSpec, () -> getDefaultBranch(projectId));
    }

    protected String getBranchName(SourceSpecification sourceSpec, Project gitLabProject)
    {
        return getBranchName(sourceSpec, () -> getDefaultBranch(gitLabProject));
    }

    protected String getBranchName(SourceSpecification sourceSpec, String defaultBranch)
    {
        return getBranchName(sourceSpec, () -> defaultBranch);
    }

    protected String getRef(SourceSpecification sourceSpec, Supplier<String> defaultBranchSupplier)
    {
        return sourceSpec.visit(new SourceSpecificationVisitor<String>()
        {
            @Override
            public String visit(ProjectSourceSpecification sourceSpec)
            {
                return defaultBranchSupplier.get();
            }

            @Override
            public String visit(VersionSourceSpecification sourceSpec)
            {
                return buildVersionTagName(sourceSpec.getVersionId());
            }

            @Override
            public String visit(PatchSourceSpecification sourceSpec)
            {
                return getPatchReleaseBranchName(sourceSpec);
            }

            @Override
            public String visit(WorkspaceSourceSpecification sourceSpec)
            {
                return getWorkspaceBranchName(sourceSpec);
            }
        });
    }

    protected String getBranchName(SourceSpecification sourceSpec, Supplier<String> defaultBranchSupplier)
    {
        return sourceSpec.visit(new SourceSpecificationVisitor<String>()
        {
            @Override
            public String visit(ProjectSourceSpecification sourceSpec)
            {
                return defaultBranchSupplier.get();
            }

            @Override
            public String visit(PatchSourceSpecification sourceSpec)
            {
                return getPatchReleaseBranchName(sourceSpec);
            }

            @Override
            public String visit(WorkspaceSourceSpecification sourceSpec)
            {
                return getWorkspaceBranchName(sourceSpec);
            }
        });
    }

    protected String getDefaultBranch(GitLabProjectId projectId)
    {
        try
        {
            ProjectApi projectApi = getGitLabApi().getProjectApi();
            return getDefaultBranch(withRetries(() -> projectApi.getProject(projectId.getGitLabId())));
        }
        catch (Exception e)
        {
            throw buildException(e, () -> "Error getting default branch for " + projectId);
        }
    }

    protected String getDefaultBranch(Project project)
    {
        String defaultBranch = project.getDefaultBranch();
        return (defaultBranch == null) ? MASTER_BRANCH : defaultBranch;
    }

    @Deprecated
    protected String getSourceBranch(GitLabProjectId projectId, VersionId patchReleaseVersionId)
    {
        return (patchReleaseVersionId == null) ? getDefaultBranch(projectId) : getPatchReleaseBranchName(patchReleaseVersionId);
    }

    protected String getSourceBranch(GitLabProjectId projectId, WorkspaceSourceSpecification sourceSpec)
    {
        return getSourceBranch(projectId, sourceSpec.getWorkspaceSpecification());
    }

    protected String getSourceBranch(GitLabProjectId projectId, WorkspaceSpecification workspaceSpec)
    {
        return getSourceBranch(projectId, workspaceSpec.getSource());
    }

    protected String getSourceBranch(GitLabProjectId projectId, WorkspaceSource workspaceSource)
    {
        return getSourceBranch(workspaceSource, () -> getDefaultBranch(projectId));
    }

    protected static String getSourceBranch(WorkspaceSourceSpecification sourceSpec, Supplier<String> defaultBranchSupplier)
    {
        return getSourceBranch(sourceSpec.getWorkspaceSpecification(), defaultBranchSupplier);
    }

    protected static String getSourceBranch(WorkspaceSpecification workspaceSpec, Supplier<String> defaultBranchSupplier)
    {
        return getSourceBranch(workspaceSpec.getSource(), defaultBranchSupplier);
    }

    protected static String getSourceBranch(WorkspaceSource workspaceSource, Supplier<String> defaultBranchSupplier)
    {
        return workspaceSource.visit(new WorkspaceSourceVisitor<String>()
        {
            @Override
            public String visit(ProjectWorkspaceSource source)
            {
                return defaultBranchSupplier.get();
            }

            @Override
            public String visit(PatchWorkspaceSource source)
            {
                return getPatchReleaseBranchName(source.getPatchVersionId());
            }
        });
    }

    protected String getWorkspaceBranchName(WorkspaceSourceSpecification sourceSpec)
    {
        return getWorkspaceBranchName(sourceSpec.getWorkspaceSpecification());
    }

    protected String getWorkspaceBranchName(WorkspaceSpecification workspaceSpec)
    {
        return getWorkspaceBranchName(workspaceSpec, this::getCurrentUser);
    }

    protected static String getWorkspaceBranchName(WorkspaceSpecification workspaceSpec, Supplier<String> currentUser)
    {
        StringBuilder builder = new StringBuilder();
        WorkspaceSource source = workspaceSpec.getSource();
        if (source instanceof PatchWorkspaceSource)
        {
            builder.append(PATCH_WORKSPACE_BRANCH_PREFIX).append(BRANCH_DELIMITER);
            ((PatchWorkspaceSource) source).getPatchVersionId().appendVersionIdString(builder).append(BRANCH_DELIMITER);
        }
        builder.append(getWorkspaceBranchNamePrefix(workspaceSpec.getType(), workspaceSpec.getAccessType())).append(BRANCH_DELIMITER);
        if (workspaceSpec.getType() == WorkspaceType.USER)
        {
            String userId = workspaceSpec.getUserId();
            builder.append((userId == null) ? currentUser.get() : userId).append(BRANCH_DELIMITER);
        }
        return builder.append(workspaceSpec.getId()).toString();
    }

    @Deprecated
    protected String getWorkspaceBranchName(SourceSpecification sourceSpecification)
    {
        return getWorkspaceBranchName((WorkspaceSourceSpecification) sourceSpecification);
    }

    protected static String getPatchReleaseBranchPrefix()
    {
        return PATCH_BRANCH_PREFIX + BRANCH_DELIMITER;
    }

    protected static String getPatchReleaseBranchName(PatchSourceSpecification sourceSpec)
    {
        return getPatchReleaseBranchName(sourceSpec.getVersionId());
    }

    protected static String getPatchReleaseBranchName(VersionId patchRelaseVersionId)
    {
        return patchRelaseVersionId.appendVersionIdString(new StringBuilder(PATCH_BRANCH_PREFIX).append(BRANCH_DELIMITER)).toString();
    }

    protected String newUserTemporaryBranchName()
    {
        return newUserTemporaryBranchName(null);
    }

    protected String newUserTemporaryBranchName(SourceSpecification sourceSpec)
    {
        StringBuilder builder = new StringBuilder(TEMPORARY_BRANCH_PREFIX).append(BRANCH_DELIMITER)
                .append(getCurrentUser()).append(BRANCH_DELIMITER);
        if (sourceSpec instanceof WorkspaceSourceSpecification)
        {
            builder.append(((WorkspaceSourceSpecification) sourceSpec).getWorkspaceSpecification().getId()).append(BRANCH_DELIMITER);
        }
        return builder.append(newTemporaryBranchId()).toString();
    }

    protected static String getRandomIdString()
    {
        byte[] tempIdBytes = new byte[12];
        ByteBuffer buffer = ByteBuffer.wrap(tempIdBytes);
        buffer.putInt(RANDOM.nextInt());
        buffer.putLong(System.currentTimeMillis());
        return new String(RANDOM_ID_ENCODER.encode(tempIdBytes), StandardCharsets.ISO_8859_1);
    }

    private static String newTemporaryBranchId()
    {
        return getRandomIdString();
    }

    protected static boolean isWorkspaceBranchName(String branchName, WorkspaceAccessType workspaceAccessType)
    {
        return isWorkspaceBranchName(branchName, WorkspaceType.USER, workspaceAccessType) || isWorkspaceBranchName(branchName, WorkspaceType.GROUP, workspaceAccessType);
    }

    protected static boolean isWorkspaceBranchName(String branchName, WorkspaceType workspaceType, WorkspaceAccessType workspaceAccessType)
    {
        return branchNameStartsWith(branchName, getWorkspaceBranchNamePrefix(workspaceType, workspaceAccessType), (String[]) null);
    }

    protected static boolean branchNameStartsWith(String branchName, String first, String... more)
    {
        if (branchName == null)
        {
            return false;
        }

        if (!branchName.startsWith(first))
        {
            return false;
        }

        int index = first.length();
        if (more != null)
        {
            for (int i = 0; i < more.length; i++)
            {
                if ((branchName.length() <= index) || (branchName.charAt(index) != BRANCH_DELIMITER))
                {
                    return false;
                }
                index++;
                String next = more[i];
                if (!branchName.startsWith(next, index))
                {
                    return false;
                }
                index += next.length();
            }
        }

        return (branchName.length() == index) || (branchName.charAt(index) == BRANCH_DELIMITER);
    }

    protected static String buildVersionTagName(VersionId versionId)
    {
        return versionId.appendVersionIdString(new StringBuilder(RELEASE_TAG_PREFIX)).toString();
    }

    protected static boolean isVersionTagName(String name)
    {
        return (name != null) &&
                name.startsWith(RELEASE_TAG_PREFIX) &&
                VersionId.isValidVersionIdString(name, RELEASE_TAG_PREFIX.length(), name.length());
    }

    protected static VersionId parseVersionTagName(String name)
    {
        return VersionId.parseVersionId(name, RELEASE_TAG_PREFIX.length(), name.length());
    }

    protected static VersionId parseVersionIdString(String versionIdString)
    {
        VersionId versionId;
        try
        {
            versionId = VersionId.parseVersionId(versionIdString);
        }
        catch (IllegalArgumentException e)
        {
            throw new LegendSDLCServerException(e.getMessage(), Response.Status.BAD_REQUEST, e);
        }
        return versionId;
    }

    protected static boolean isVersionTag(Tag tag)
    {
        return isVersionTagName(tag.getName());
    }

    @Deprecated
    protected static String getReferenceInfo(GitLabProjectId projectId, String workspaceId, WorkspaceType workspaceType, WorkspaceAccessType workspaceAccessType, String revisionId)
    {
        return getReferenceInfo(projectId.toString(), workspaceId, workspaceType, workspaceAccessType, revisionId);
    }

    @Deprecated
    protected static String getReferenceInfo(String projectId, String workspaceId, WorkspaceType workspaceType, WorkspaceAccessType workspaceAccessType, String revisionId)
    {
        int messageLength = projectId.length() + 8;
        if (workspaceId != null)
        {
            messageLength += workspaceId.length() + 20;
        }
        if (revisionId != null)
        {
            messageLength += revisionId.length() + 13;
        }
        return appendReferenceInfo(new StringBuilder(messageLength), projectId, workspaceId, workspaceType, workspaceAccessType, revisionId).toString();
    }

    @Deprecated
    protected static StringBuilder appendReferenceInfo(StringBuilder builder, String projectId, String workspaceId, WorkspaceType workspaceType, WorkspaceAccessType workspaceAccessType, String revisionId)
    {
        if (revisionId != null)
        {
            builder.append("revision ").append(revisionId).append(" of ");
        }
        if (workspaceId != null)
        {
            builder.append(workspaceType.getLabel()).append(' ').append(workspaceAccessType.getLabel()).append(' ').append(workspaceId).append(" of ");
        }
        builder.append("project ").append(projectId);
        return builder;
    }

    protected static String getReferenceInfo(GitLabProjectId projectId, SourceSpecification sourceSpec)
    {
        return getReferenceInfo(projectId, sourceSpec, null);
    }

    protected static String getReferenceInfo(GitLabProjectId projectId, SourceSpecification sourceSpec, String revisionId)
    {
        return getReferenceInfo(projectId.toString(), sourceSpec, revisionId);
    }

    protected static String getReferenceInfo(String projectId, WorkspaceSpecification workspaceSpec)
    {
        return getReferenceInfo(projectId, workspaceSpec.getSourceSpecification());
    }

    protected static String getReferenceInfo(String projectId, WorkspaceSource workspaceSource)
    {
        return getReferenceInfo(projectId, workspaceSource.getSourceSpecification());
    }

    protected static String getReferenceInfo(String projectId, SourceSpecification sourceSpec)
    {
        return getReferenceInfo(projectId, sourceSpec, null);
    }

    protected static String getReferenceInfo(String projectId, SourceSpecification sourceSpec, String revisionId)
    {
        return appendReferenceInfo(new StringBuilder(), projectId, sourceSpec, revisionId).toString();
    }

    protected static StringBuilder appendReferenceInfo(StringBuilder builder, String projectId, SourceSpecification sourceSpec)
    {
        return appendReferenceInfo(builder, projectId, sourceSpec, null);
    }

    protected static StringBuilder appendReferenceInfo(StringBuilder builder, String projectId, SourceSpecification sourceSpec, String revisionId)
    {
        if (revisionId != null)
        {
            builder.append("revision ").append(revisionId).append(" of ");
        }
        sourceSpec.visit(new SourceSpecificationConsumer()
        {
            @Override
            protected void accept(VersionSourceSpecification sourceSpec)
            {
                sourceSpec.getVersionId().appendVersionIdString(builder.append("version "));
            }

            @Override
            protected void accept(PatchSourceSpecification sourceSpec)
            {
                sourceSpec.getVersionId().appendVersionIdString(builder.append("patch "));
            }

            @Override
            protected void accept(WorkspaceSourceSpecification sourceSpec)
            {
                WorkspaceSpecification workspaceSpec = sourceSpec.getWorkspaceSpecification();
                builder.append(workspaceSpec.getType().getLabel()).append(" ")
                        .append(workspaceSpec.getAccessType().getLabel()).append(" ")
                        .append(workspaceSpec.getId()).append(" of ");
                workspaceSpec.getSource().visit(new WorkspaceSourceConsumer()
                {
                    @Override
                    protected void accept(PatchWorkspaceSource source)
                    {
                        source.getPatchVersionId().appendVersionIdString(builder.append("patch ")).append(" of ");
                    }
                });
            }
        });
        return builder.append("project ").append(projectId);
    }

    protected static <T, R> R applyIfNotNull(Function<? super T, ? extends R> function, T value)
    {
        return (value == null) ? null : function.apply(value);
    }

    protected static String toStringIfNotNull(Object value)
    {
        return (value == null) ? null : value.toString();
    }

    protected static Instant toInstantIfNotNull(Date date)
    {
        return (date == null) ? null : date.toInstant();
    }

    protected static Date toDateIfNotNull(Instant instant)
    {
        return (instant == null) ? null : Date.from(instant);
    }

    protected static Integer parseIntegerIdIfNotNull(String id)
    {
        return parseIntegerIdIfNotNull(id, null);
    }

    protected static Integer parseIntegerIdIfNotNull(String id, Status errorStatus)
    {
        return (id == null) ? null : parseIntegerId(id, errorStatus);
    }

    protected static int parseIntegerId(String id)
    {
        return parseIntegerId(id, null);
    }

    protected static int parseIntegerId(String id, Status errorStatus)
    {
        try
        {
            return Integer.parseInt(id);
        }
        catch (NumberFormatException e)
        {
            throw new LegendSDLCServerException("Invalid id: " + id, (errorStatus == null) ? Status.BAD_REQUEST : errorStatus);
        }
    }

    protected LegendSDLCServerException buildException(Exception e, String message)
    {
        return buildException(e, null, null, ex -> message);
    }

    protected LegendSDLCServerException buildException(Exception e, Supplier<String> message)
    {
        return buildException(e, null, null, (message == null) ? null : ex -> message.get());
    }

    protected LegendSDLCServerException buildException(Exception e, Supplier<String> forbiddenMessage, Supplier<String> notFoundMessage, Supplier<String> defaultMessage)
    {
        return buildException(e,
                (forbiddenMessage == null) ?
                        null :
                        ex -> Optional.ofNullable(forbiddenMessage.get()).map(m -> StringTools.appendThrowableMessageIfPresent(m, ex)).orElse(null),
                (notFoundMessage == null) ?
                        null :
                        ex -> notFoundMessage.get(),
                (defaultMessage == null) ?
                        null :
                        ex -> Optional.ofNullable(defaultMessage.get()).map(m -> StringTools.appendThrowableMessageIfPresent(m, ex)).orElse(null));
    }

    protected LegendSDLCServerException buildException(Exception e, Function<? super GitLabApiException, String> forbiddenMessage, Function<? super GitLabApiException, String> notFoundMessage, Function<? super Exception, String> defaultMessage)
    {
        return processException(e,
            Function.identity(),
            glae ->
            {
                switch (glae.getHttpStatus())
                {
                    case 401:
                    {
                        // this means the access token is invalid
                        this.userContext.clearAccessToken();
                        HttpServletRequest httpRequest = this.userContext.getHttpRequest();
                        StringBuffer urlBuilder = httpRequest.getRequestURL();
                        String requestQueryString = httpRequest.getQueryString();
                        if (requestQueryString != null)
                        {
                            urlBuilder.append('?').append(requestQueryString);
                        }
                        if ("GET".equalsIgnoreCase(httpRequest.getMethod()))
                        {
                            // TODO consider a more appropriate redirect status if HTTP version is 1.1
                            return new LegendSDLCServerException(urlBuilder.toString(), Status.FOUND);
                        }
                        else
                        {
                            return new LegendSDLCServerException("Please retry request: " + httpRequest.getMethod() + " " + urlBuilder.toString(), Status.SERVICE_UNAVAILABLE, glae);
                        }
                    }
                    case 403:
                    {
                        return new LegendSDLCServerException(buildExceptionMessage(glae, forbiddenMessage, defaultMessage), Status.FORBIDDEN, glae);
                    }
                    case 404:
                    {
                        return new LegendSDLCServerException(buildExceptionMessage(glae, notFoundMessage, defaultMessage), Status.NOT_FOUND, glae);
                    }
                    default:
                    {
                        return null;
                    }
                }
            },
            (defaultMessage == null) ? null : ex -> Optional.ofNullable(defaultMessage.apply(ex)).map(m -> new LegendSDLCServerException(m, ex)).orElse(null)
        );
    }

    private String buildExceptionMessage(GitLabApiException glae, Function<? super GitLabApiException, String> message, Function<? super GitLabApiException, String> defaultMessage)
    {
        if (message != null)
        {
            try
            {
                return message.apply(glae);
            }
            catch (Exception e)
            {
                LOGGER.error("Error building exception message for {} exception", glae.getHttpStatus(), e);
            }
        }
        if (defaultMessage != null)
        {
            try
            {
                return defaultMessage.apply(glae);
            }
            catch (Exception e)
            {
                LOGGER.error("Error building exception message for {} exception", glae.getHttpStatus(), e);
            }
        }
        try
        {
            return glae.getMessage();
        }
        catch (Exception e)
        {
            LOGGER.error("Error building exception message for {} exception", glae.getHttpStatus(), e);
        }
        return "An unexpected error occurred (GitLab response status: " + glae.getHttpStatus() + ")";
    }

    protected LegendSDLCServerException processException(Exception e, Function<? super LegendSDLCServerException, ? extends LegendSDLCServerException> meHandler, Function<? super GitLabApiException, ? extends LegendSDLCServerException> glaeHandler, Function<? super Exception, ? extends LegendSDLCServerException> defaultHandler)
    {
        // Special handling
        if ((meHandler != null) && (e instanceof LegendSDLCServerException))
        {
            try
            {
                LegendSDLCServerException result = meHandler.apply((LegendSDLCServerException) e);
                if (result != null)
                {
                    return result;
                }
            }
            catch (Exception ex)
            {
                LOGGER.error("Error processing exception of type {}", e.getClass().getSimpleName(), ex);
            }
        }
        else if ((glaeHandler != null) && (e instanceof GitLabApiException))
        {
            try
            {
                LegendSDLCServerException result = glaeHandler.apply((GitLabApiException) e);
                if (result != null)
                {
                    return result;
                }
            }
            catch (Exception ex)
            {
                LOGGER.error("Error processing exception of type {}", e.getClass().getSimpleName(), ex);
            }
        }

        // Provided default handling
        if (defaultHandler != null)
        {
            try
            {
                LegendSDLCServerException result = defaultHandler.apply(e);
                if (result != null)
                {
                    return result;
                }
            }
            catch (Exception ex)
            {
                LOGGER.error("Error processing exception of type {} with the default handler", e.getClass().getSimpleName(), ex);
            }
        }

        // Default default handling (final fall through case)
        String message;
        try
        {
            message = StringTools.appendThrowableMessageIfPresent("An unexpected exception occurred", e);
        }
        catch (Exception ex)
        {
            LOGGER.error("Error generating default error message ", ex);
            message = "An unexpected exception occurred";
        }
        return new LegendSDLCServerException(message, e);
    }

    protected <T> T withRetries(ThrowingSupplier<T, ? extends GitLabApiException> apiCall) throws GitLabApiException
    {
        return GitLabApiTools.callWithRetries(apiCall, MAX_RETRIES, INITIAL_RETRY_WAIT_INTERVAL_MILLIS, RETRY_WAIT_INTERVAL_UPDATER);
    }

    protected void withRetries(ThrowingRunnable<? extends GitLabApiException> apiCall) throws GitLabApiException
    {
        withRetries(() ->
        {
            apiCall.run();
            return null;
        });
    }

    protected static User fromGitLabAbstractUser(AbstractUser<?> user)
    {
        if (user == null)
        {
            return null;
        }

        String username = user.getUsername();
        String name = user.getName();
        return new User()
        {
            @Override
            public String getUserId()
            {
                return username;
            }

            @Override
            public String getName()
            {
                return name;
            }
        };
    }

    protected static Workspace fromWorkspaceBranchName(String projectId, String branchName)
    {
        WorkspaceSpecification workspaceSpec = parseWorkspaceBranchName(branchName);
        return (workspaceSpec == null) ? null : fromWorkspaceSpecification(projectId, workspaceSpec);
    }

    protected static Workspace fromWorkspaceSpecification(String projectId, WorkspaceSpecification workspaceSpec)
    {
        String workspaceId = workspaceSpec.getId();
        String userId = workspaceSpec.getUserId();
        if ((userId == null) && (workspaceSpec.getType() == WorkspaceType.USER))
        {
            throw new RuntimeException("Cannot create workspace from specification " + workspaceSpec + ": user workspace must have user id");
        }
        return new Workspace()
        {
            @Override
            public String getProjectId()
            {
                return projectId;
            }

            @Override
            public String getUserId()
            {
                return userId;
            }

            @Override
            public String getWorkspaceId()
            {
                return workspaceId;
            }
        };
    }

    protected static Patch fromPatchBranchName(String projectId, String branchName)
    {
        int index = branchName.lastIndexOf(BRANCH_DELIMITER);
        VersionId patchReleaseVersionId = parseVersionIdString(branchName.substring(index + 1));
        return new Patch()
        {
            @Override
            public String getProjectId()
            {
                return projectId;
            }

            @Override
            public VersionId getPatchReleaseVersionId()
            {
                return patchReleaseVersionId;
            }
        };
    }

    protected MergeRequest getReviewMergeRequest(MergeRequestApi mergeRequestApi, GitLabProjectId projectId, String reviewId)
    {
        return getReviewMergeRequest(mergeRequestApi, projectId, reviewId, false);
    }

    protected MergeRequest getReviewMergeRequest(MergeRequestApi mergeRequestApi, GitLabProjectId projectId, String reviewId, boolean includeRebaseInProgress)
    {
        return getReviewMergeRequest(mergeRequestApi, projectId, reviewId, includeRebaseInProgress, () -> getDefaultBranch(projectId));
    }

    protected MergeRequest getReviewMergeRequest(MergeRequestApi mergeRequestApi, GitLabProjectId projectId, String reviewId, Supplier<String> defaultBranchSupplier)
    {
        return getReviewMergeRequest(mergeRequestApi, projectId, reviewId, false, defaultBranchSupplier);
    }

    protected MergeRequest getReviewMergeRequest(MergeRequestApi mergeRequestApi, GitLabProjectId projectId, String reviewId, boolean includeRebaseInProgress, Supplier<String> defaultBranchSupplier)
    {
        int mergeRequestId = parseIntegerId(reviewId);
        MergeRequest mergeRequest;
        try
        {
            mergeRequest = withRetries(() -> mergeRequestApi.getMergeRequest(projectId.getGitLabId(), mergeRequestId, false, null, includeRebaseInProgress));
        }
        catch (Exception e)
        {
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to access review " + reviewId + " in project " + projectId,
                () -> "Unknown review in project " + projectId + ": " + reviewId,
                () -> "Error accessing review " + reviewId + " in project " + projectId);
        }

        if (!isReviewMergeRequest(mergeRequest, defaultBranchSupplier))
        {
            throw new LegendSDLCServerException("Unknown review in project " + projectId + ": " + reviewId, Status.NOT_FOUND);
        }
        return mergeRequest;
    }

    protected static boolean isReviewMergeRequest(MergeRequest mergeRequest, Supplier<String> defaultBranchSupplier)
    {
        if (mergeRequest != null)
        {
            WorkspaceSpecification workspaceSpec = parseWorkspaceBranchName(mergeRequest.getSourceBranch());
            if ((workspaceSpec != null) && (workspaceSpec.getAccessType() == WorkspaceAccessType.WORKSPACE))
            {
                String expectedTargetBranch = getSourceBranch(workspaceSpec, defaultBranchSupplier);
                return expectedTargetBranch.equals(mergeRequest.getTargetBranch());
            }
        }
        return false;
    }

    protected MergeRequest getReviewMergeRequestApprovals(MergeRequestApi mergeRequestApi, GitLabProjectId projectId, String reviewId)
    {
        int mergeRequestId = parseIntegerId(reviewId);
        MergeRequest mergeRequest;
        try
        {
            mergeRequest = withRetries(() -> mergeRequestApi.getMergeRequestApprovals(projectId.getGitLabId(), mergeRequestId));
        }
        catch (Exception e)
        {
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to get approval details for review " + reviewId + " in project " + projectId,
                () -> "Unknown review in project " + projectId + ": " + reviewId,
                () -> "Error getting approval details for review " + reviewId + " in project " + projectId);
        }
        return mergeRequest;
    }

    protected static boolean isOpen(MergeRequest mergeRequest)
    {
        return isOpen(mergeRequest.getState());
    }

    protected static boolean isCommitted(MergeRequest mergeRequest)
    {
        return isCommitted(mergeRequest.getState());
    }

    protected static boolean isClosed(MergeRequest mergeRequest)
    {
        return isClosed(mergeRequest.getState());
    }

    protected static boolean isLocked(MergeRequest mergeRequest)
    {
        return isLocked(mergeRequest.getState());
    }

    protected static ReviewState getReviewState(MergeRequest mergeRequest)
    {
        return getReviewState(mergeRequest.getState());
    }

    protected static ReviewState getReviewState(String mergeState)
    {
        if (mergeState != null)
        {
            if (isOpen(mergeState))
            {
                return ReviewState.OPEN;
            }
            if (isCommitted(mergeState))
            {
                return ReviewState.COMMITTED;
            }
            if (isClosed(mergeState))
            {
                return ReviewState.CLOSED;
            }
        }
        return ReviewState.UNKNOWN;
    }

    private static boolean isOpen(String mergeState)
    {
        return MR_STATE_OPENED.equalsIgnoreCase(mergeState);
    }

    private static boolean isCommitted(String mergeState)
    {
        return MR_STATE_MERGED.equalsIgnoreCase(mergeState);
    }

    private static boolean isClosed(String mergeState)
    {
        return MR_STATE_CLOSED.equalsIgnoreCase(mergeState);
    }

    private static boolean isLocked(String mergeState)
    {
        return MR_STATE_LOCKED.equalsIgnoreCase(mergeState);
    }

    protected static String resolveRevisionId(String revisionId, ProjectFileAccessProvider.RevisionAccessContext revisionAccessContext)
    {
        if (revisionId == null)
        {
            throw new IllegalArgumentException("Resolving revision alias does not work with null revisionId, null handling must be done before using this method");
        }
        RevisionAlias revisionAlias = getRevisionAlias(revisionId);
        switch (revisionAlias)
        {
            case BASE:
            {
                Revision revision = revisionAccessContext.getBaseRevision();
                return (revision == null) ? null : revision.getId();
            }
            case HEAD:
            {
                Revision revision = revisionAccessContext.getCurrentRevision();
                return (revision == null) ? null : revision.getId();
            }
            case REVISION_ID:
            {
                return revisionId;
            }
            default:
            {
                throw new IllegalArgumentException("Unknown revision alias type: " + revisionAlias);
            }
        }
    }

    protected static RevisionAlias getRevisionAlias(String revisionId)
    {
        if (revisionId == null)
        {
            return null;
        }
        if (RevisionAlias.BASE.getValue().equalsIgnoreCase(revisionId))
        {
            return RevisionAlias.BASE;
        }
        if (RevisionAlias.HEAD.getValue().equalsIgnoreCase(revisionId) || RevisionAlias.CURRENT.getValue().equalsIgnoreCase(revisionId) || RevisionAlias.LATEST.getValue().equalsIgnoreCase(revisionId))
        {
            return RevisionAlias.HEAD;
        }
        return RevisionAlias.REVISION_ID;
    }

    protected static Version fromGitLabTag(String projectId, Tag tag)
    {
        if (tag == null)
        {
            return null;
        }

        VersionId versionId = parseVersionTagName(tag.getName());
        String revisionId = tag.getCommit().getId();
        String notes = applyIfNotNull(Release::getDescription, tag.getRelease());
        return new Version()
        {
            @Override
            public VersionId getId()
            {
                return versionId;
            }

            @Override
            public String getProjectId()
            {
                return projectId;
            }

            @Override
            public String getRevisionId()
            {
                return revisionId;
            }

            @Override
            public String getNotes()
            {
                return notes;
            }
        };
    }

    protected Version getProjectVersion(String projectId, int majorVersion, int minorVersion, int patchVersion)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        if ((majorVersion < 0) || (minorVersion < 0) || (patchVersion < 0))
        {
            return null;
        }
        VersionId versionId = VersionId.newVersionId(majorVersion, minorVersion, patchVersion);
        String name = buildVersionTagName(versionId);
        try
        {
            return fromGitLabTag(projectId, getGitLabApi().getTagsApi().getTag(gitLabProjectId.getGitLabId(), name));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access version " + versionId.toVersionIdString() + " of project " + projectId,
                    () -> "Version " + versionId.toVersionIdString() + " is unknown for project " + projectId,
                    () -> "Error accessing version " + versionId.toVersionIdString() + " of project " + projectId);
        }
    }

    protected Version newVersion(GitLabProjectId projectId, VersionId patchReleaseVersionId, String revisionId, VersionId versionId, String notes)
    {
        String tagName = buildVersionTagName(versionId);
        String message = "Release tag for version " + versionId.toVersionIdString();

        try
        {
            GitLabApi gitLabApi = getGitLabApi();
            CommitsApi commitsApi = gitLabApi.getCommitsApi();

            Commit referenceCommit;
            if (revisionId == null)
            {
                referenceCommit = withRetries(() -> commitsApi.getCommit(projectId.getGitLabId(), getSourceBranch(projectId, patchReleaseVersionId)));
                if (referenceCommit == null)
                {
                    throw new LegendSDLCServerException("Cannot create version " + versionId.toVersionIdString() + " of project " + projectId + ": cannot find current revision (project may be corrupt)", Status.INTERNAL_SERVER_ERROR);
                }
            }
            else
            {
                try
                {
                    referenceCommit = withRetries(() -> commitsApi.getCommit(projectId.getGitLabId(), revisionId));
                }
                catch (GitLabApiException e)
                {
                    if (GitLabApiTools.isNotFoundGitLabApiException(e))
                    {
                        throw new LegendSDLCServerException("Revision " + revisionId + " is unknown in project " + projectId, Status.BAD_REQUEST);
                    }
                    throw e;
                }

                String sourceBranch = getSourceBranch(projectId, patchReleaseVersionId);
                boolean isOnSourceBranch = PagerTools.stream(withRetries(() -> commitsApi.getCommitRefs(projectId.getGitLabId(), revisionId, CommitRef.RefType.BRANCH, ITEMS_PER_PAGE)))
                        .anyMatch(ref -> sourceBranch.equals(ref.getName()));
                if (!isOnSourceBranch)
                {
                    throw new LegendSDLCServerException("Revision " + revisionId + " is unknown in project " + projectId, Status.BAD_REQUEST);
                }
            }

            Tag tag = gitLabApi.getTagsApi().createTag(projectId.getGitLabId(), tagName, referenceCommit.getId(), message, (String) null);
            if (notes != null)
            {
                gitLabApi.getReleasesApi().createRelease(projectId.getGitLabId(), new ReleaseParams().withTagName(tagName).withDescription(notes));
            }
            return fromGitLabTag(projectId.toString(), tag);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to create version " + versionId.toVersionIdString() + " of project " + projectId,
                    () -> "Unknown project: " + projectId,
                    () -> "Error creating version " + versionId.toVersionIdString() + " of project " + projectId);
        }
    }

    protected boolean isPatchReleaseBranchPresent(GitLabProjectId projectId, VersionId patchReleaseVersionId)
    {
        try
        {
            return GitLabApiTools.branchExists(getGitLabApi().getRepositoryApi(), projectId.getGitLabId(), getPatchReleaseBranchName(patchReleaseVersionId));
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to access patch " + patchReleaseVersionId.toVersionIdString(),
                    () -> "Unknown patch: " + patchReleaseVersionId.toVersionIdString(), // this case should never happen
                    () -> "Error in fetching the patch release branch " + patchReleaseVersionId.toVersionIdString());
        }
    }
}
