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

import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.domain.model.review.Approval;
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.domain.model.review.ReviewState;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.revision.RevisionAlias;
import org.finos.legend.sdlc.domain.model.user.User;
import org.finos.legend.sdlc.domain.model.version.Version;
import org.finos.legend.sdlc.domain.model.version.VersionId;
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
import org.gitlab4j.api.Constants;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.MergeRequestApi;
import org.gitlab4j.api.ProjectApi;
import org.gitlab4j.api.models.AbstractUser;
import org.gitlab4j.api.models.Branch;
import org.gitlab4j.api.models.Commit;
import org.gitlab4j.api.models.CommitRef;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.MergeRequestFilter;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.ReleaseParams;
import org.gitlab4j.api.models.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response.Status;

import static org.finos.legend.sdlc.server.gitlab.api.GitLabApiWithFileAccess.fromGitLabTag;

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
    private static final String TEMPORARY_BRANCH_PREFIX = "tmp";
    private static final String GROUP_BRANCH_PREFIX = "group";
    private static final String GROUP_CONFLICT_RESOLUTION_BRANCH_PREFIX = "group-resolution";
    private static final String GROUP_BACKUP_BRANCH_PREFIX = "group-backup";
    private static final String PATCH_RELEASE_BRANCH_PREFIX = "patch/main";
    protected static final String PATCH_RELEASE_WORKSPACE_BRANCH_PREFIX = "patch";

    private static final String MR_STATE_OPENED = "opened";
    private static final String MR_STATE_CLOSED = "closed";
    private static final String MR_STATE_LOCKED = "locked";
    private static final String MR_STATE_MERGED = "merged";

    private static final String MASTER_BRANCH = "master";

    protected static final int ITEMS_PER_PAGE = 100;

    @Deprecated
    protected static final String PACKAGE_SEPARATOR = EntityPaths.PACKAGE_SEPARATOR;

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
                        return GROUP_BRANCH_PREFIX;
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
                        throw new RuntimeException("Unknown workspace access type " + workspaceAccessType);
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
                        throw new RuntimeException("Unknown workspace access type " + workspaceAccessType);
                    }
                }
            }
            default:
            {
                throw new RuntimeException("Unknown workspace type " + workspaceType);
            }
        }
    }

    protected static WorkspaceInfo parseWorkspaceBranchName(String branchName, String patchReleaseVersion)
    {
        String branchType;
        int branchInfoDelimiter;
        if (patchReleaseVersion != null)
        {
            int firstDelimiter = branchName.indexOf(BRANCH_DELIMITER);
            if (firstDelimiter == -1)
            {
                return null;
            }
            int secondDelimiter = branchName.indexOf(BRANCH_DELIMITER, firstDelimiter + 1);
            if (secondDelimiter == -1)
            {
                return null;
            }
            int thirdDelimter = branchName.indexOf(BRANCH_DELIMITER, secondDelimiter + 1);
            if (thirdDelimter == -1)
            {
                return null;
            }
            branchInfoDelimiter = thirdDelimter;
            branchType = branchName.substring(firstDelimiter + 1, secondDelimiter);
        }
        else
        {
            int firstDelimiter = branchName.indexOf(BRANCH_DELIMITER);
            if (firstDelimiter == -1)
            {
                return null;
            }
            branchInfoDelimiter = firstDelimiter;
            branchType = branchName.substring(0, firstDelimiter);
        }

        WorkspaceType type;
        WorkspaceAccessType accessType;

        switch (branchType)
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
            case GROUP_BRANCH_PREFIX:
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
                return null;
            }
        }

        switch (type)
        {
            case USER:
            {
                // <prefix>/<userId>/<workspaceId>
                int nextDelimiter = branchName.indexOf(BRANCH_DELIMITER, branchInfoDelimiter + 1);
                if (nextDelimiter == -1)
                {
                    return null;
                }
                String workspaceId = branchName.substring(nextDelimiter + 1);
                String userId = branchName.substring(branchInfoDelimiter + 1, nextDelimiter);
                return new WorkspaceInfo(workspaceId, type, accessType, userId);
            }
            case GROUP:
            {
                // <prefix>/<workspaceId>
                String workspaceId = branchName.substring(branchInfoDelimiter + 1);
                return new WorkspaceInfo(workspaceId, type, accessType, null);
            }
            default:
            {
                throw new IllegalStateException("Unknown workspace type: " + type);
            }
        }
    }

    protected String getBranchName(String workspaceId, WorkspaceType workspaceType, WorkspaceAccessType workspaceAccessType, GitLabProjectId projectId, String patchReleaseVersion)
    {
        return (workspaceId == null) ? getSourceBranch(projectId, patchReleaseVersion) : getWorkspaceBranchName(workspaceId, workspaceType, workspaceAccessType, patchReleaseVersion);
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

    protected String getSourceBranch(GitLabProjectId projectId, String patchReleaseversion)
    {
        if (patchReleaseversion == null)
        {
            return getDefaultBranch(projectId);
        }
        else
        {
            return getPatchReleaseBranchName(patchReleaseversion);
        }
    }

    protected String getDefaultBranch(Project project)
    {
        String defaultBranch = project.getDefaultBranch();
        return (defaultBranch == null) ? MASTER_BRANCH : defaultBranch;
    }

    protected String getWorkspaceBranchName(String workspaceId, WorkspaceType workspaceType, WorkspaceAccessType workspaceAccessType, String patchReleaseVersion)
    {
        String prefix = getWorkspaceBranchNamePrefix(workspaceType, workspaceAccessType);
        switch (workspaceType)
        {
            case USER:
            {
                return patchReleaseVersion == null ? createBranchName(prefix, getCurrentUser(), workspaceId) : createBranchName(PATCH_RELEASE_WORKSPACE_BRANCH_PREFIX, prefix, patchReleaseVersion, getCurrentUser(), workspaceId);
            }
            case GROUP:
            {
                return patchReleaseVersion == null ? createBranchName(prefix, workspaceId) : createBranchName(PATCH_RELEASE_WORKSPACE_BRANCH_PREFIX, prefix, patchReleaseVersion, workspaceId);
            }
            default:
            {
                throw new IllegalStateException("Unknown workspace type: " + workspaceType);
            }
        }
    }

    protected String getPatchReleaseBranchName(String patchRelaseVersion)
    {
        return patchRelaseVersion == null ? null : PATCH_RELEASE_BRANCH_PREFIX + BRANCH_DELIMITER + patchRelaseVersion;
    }

    protected String newUserTemporaryBranchName()
    {
        return createBranchName(TEMPORARY_BRANCH_PREFIX, getCurrentUser(), newTemporaryBranchId());
    }

    protected String newUserTemporaryBranchName(String workspaceId)
    {
        return createBranchName(TEMPORARY_BRANCH_PREFIX, getCurrentUser(), workspaceId, newTemporaryBranchId());
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

    private static String createBranchName(String first, String second)
    {
        return first + BRANCH_DELIMITER + second;
    }

    private static String createBranchName(String first, String second, String third)
    {
        return first + BRANCH_DELIMITER + second + BRANCH_DELIMITER + third;
    }

    private static String createBranchName(String first, String second, String third, String fourth)
    {
        return first + BRANCH_DELIMITER + second + BRANCH_DELIMITER + third + BRANCH_DELIMITER + fourth;
    }

    private static String createBranchName(String first, String second, String third, String fourth, String fifth)
    {
        return first + BRANCH_DELIMITER + second + BRANCH_DELIMITER + third + BRANCH_DELIMITER + fourth + BRANCH_DELIMITER + fifth;
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

    protected static boolean isVersionTag(Tag tag)
    {
        return isVersionTagName(tag.getName());
    }

    protected static String getReferenceInfo(GitLabProjectId projectId, String workspaceId, WorkspaceType workspaceType, WorkspaceAccessType workspaceAccessType, String revisionId)
    {
        return getReferenceInfo(projectId.toString(), workspaceId, workspaceType, workspaceAccessType, revisionId);
    }

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

    protected static Workspace fromWorkspaceBranchName(String projectId, String patchReleaseVersion, String branchName, WorkspaceType workspaceType, WorkspaceAccessType workspaceAccessType)
    {
        int userIdStartIndex = patchReleaseVersion == null ? getWorkspaceBranchNamePrefix(workspaceType, workspaceAccessType).length() + 1 : PATCH_RELEASE_WORKSPACE_BRANCH_PREFIX.length() + getWorkspaceBranchNamePrefix(workspaceType, workspaceAccessType).length() + 8;
        int userIdEndIndex = branchName.indexOf(BRANCH_DELIMITER, workspaceType == WorkspaceType.GROUP ? userIdStartIndex - 1 : userIdStartIndex);
        String userId = workspaceType == WorkspaceType.GROUP ? null : branchName.substring(userIdStartIndex, userIdEndIndex);
        String workspaceId = branchName.substring(userIdEndIndex + 1);
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

    protected MergeRequest getReviewMergeRequest(MergeRequestApi mergeRequestApi, GitLabProjectId projectId, String patchReleaseVersion, String reviewId)
    {
        return getReviewMergeRequest(mergeRequestApi, projectId, patchReleaseVersion, reviewId, false);
    }

    protected MergeRequest getReviewMergeRequest(MergeRequestApi mergeRequestApi, GitLabProjectId projectId, String patchReleaseVersion, String reviewId, boolean includeRebaseInProgress)
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

        if (!isReviewMergeRequest(mergeRequest, getSourceBranch(projectId, patchReleaseVersion), patchReleaseVersion))
        {
            throw new LegendSDLCServerException("Unknown review in project " + projectId + ": " + reviewId, Status.NOT_FOUND);
        }
        return mergeRequest;
    }

    protected static boolean isReviewMergeRequest(MergeRequest mergeRequest, String targetBranch, String patchReleaseVersion)
    {
        if ((mergeRequest == null) || !targetBranch.equals(mergeRequest.getTargetBranch()))
        {
            return false;
        }
        WorkspaceInfo workspaceInfo = parseWorkspaceBranchName(mergeRequest.getSourceBranch(), patchReleaseVersion);
        return (workspaceInfo != null) && (workspaceInfo.getWorkspaceAccessType() == WorkspaceAccessType.WORKSPACE);
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
        Revision revision;
        if (revisionId == null)
        {
            throw new IllegalArgumentException("Resolving revision alias does not work with null revisionId, null handling must be done before using this method");
        }
        RevisionAlias revisionAlias = getRevisionAlias(revisionId);
        switch (revisionAlias)
        {
            case BASE:
            {
                revision = revisionAccessContext.getBaseRevision();
                return revision == null ? null : revision.getId();
            }
            case HEAD:
            {
                revision = revisionAccessContext.getCurrentRevision();
                return revision == null ? null : revision.getId();
            }
            case REVISION_ID:
            {
                return revisionId;
            }
            default:
            {
                throw new IllegalArgumentException("Unknown revision alias type " + revisionAlias);
            }
        }
    }

    protected static RevisionAlias getRevisionAlias(String revisionId)
    {
        if (revisionId != null)
        {
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
        return null;
    }

    protected static final class WorkspaceInfo
    {
        private final String workspaceId;
        private final WorkspaceType workspaceType;
        private final WorkspaceAccessType workspaceAccessType;
        private final String userId;

        private WorkspaceInfo(String workspaceId, WorkspaceType workspaceType, WorkspaceAccessType workspaceAccessType, String userId)
        {
            this.workspaceId = workspaceId;
            this.workspaceType = workspaceType;
            this.workspaceAccessType = workspaceAccessType;
            this.userId = userId;
        }

        public String getWorkspaceId()
        {
            return this.workspaceId;
        }

        public WorkspaceType getWorkspaceType()
        {
            return this.workspaceType;
        }

        public WorkspaceAccessType getWorkspaceAccessType()
        {
            return this.workspaceAccessType;
        }

        public String getUserId()
        {
            return this.userId;
        }
    }

    protected Version getVersion(String projectId, int majorVersion, int minorVersion, int patchVersion)
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

    protected Version newVersion(GitLabProjectId projectId, String patchReleaseVersion, String revisionId, VersionId versionId, String notes)
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
                referenceCommit = withRetries(() -> commitsApi.getCommit(projectId.getGitLabId(), getSourceBranch(projectId, patchReleaseVersion)));
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

                String sourceBranch = getSourceBranch(projectId, patchReleaseVersion);
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

    protected boolean isPatchReleaseBranchPresent(GitLabProjectId projectId, String patchRelaseVersion)
    {
        Branch existingBranch = null;
        try
        {
            existingBranch = getGitLabApi().getRepositoryApi().getBranch(projectId.getGitLabId(), getPatchReleaseBranchName(patchRelaseVersion));
        }
        catch (Exception e)
        {
            LOGGER.warn("Error in fetching the patch release branch", patchRelaseVersion,e);
        }
        if (existingBranch == null)
        {
            return false;
        }
        return true;
    }

    protected static Constants.MergeRequestState getMergeRequestState(ReviewState state)
    {
        if (state == null)
        {
            return Constants.MergeRequestState.ALL;
        }
        switch (state)
        {
            case OPEN:
            {
                return Constants.MergeRequestState.OPENED;
            }
            case COMMITTED:
            {
                return Constants.MergeRequestState.MERGED;
            }
            case CLOSED:
            {
                return Constants.MergeRequestState.CLOSED;
            }
            case UNKNOWN:
            {
                return Constants.MergeRequestState.ALL;
            }
            default:
            {
                throw new IllegalArgumentException("Unknown review state: " + state);
            }
        }
    }

    protected static Review newReview(String reviewId, String projectId, String workspaceId, WorkspaceType workspaceType, String title, String description, Instant createdAt, Instant lastUpdatedAt, Instant closedAt, Instant committedAt, ReviewState reviewState, User author, String commitRevisionId, String webURL, List<String> labels)
    {
        return new Review()
        {
            @Override
            public String getId()
            {
                return reviewId;
            }

            @Override
            public String getProjectId()
            {
                return projectId;
            }

            @Override
            public String getWorkspaceId()
            {
                return workspaceId;
            }

            @Override
            public WorkspaceType getWorkspaceType()
            {
                return workspaceType;
            }

            @Override
            public String getTitle()
            {
                return title;
            }

            @Override
            public String getDescription()
            {
                return description;
            }

            @Override
            public Instant getCreatedAt()
            {
                return createdAt;
            }

            @Override
            public Instant getLastUpdatedAt()
            {
                return lastUpdatedAt;
            }

            @Override
            public Instant getClosedAt()
            {
                return closedAt;
            }

            @Override
            public Instant getCommittedAt()
            {
                return committedAt;
            }

            @Override
            public ReviewState getState()
            {
                return reviewState;
            }

            @Override
            public User getAuthor()
            {
                return author;
            }

            @Override
            public String getCommitRevisionId()
            {
                return commitRevisionId;
            }

            @Override
            public String getWebURL()
            {
                return webURL;
            }

            @Override
            public List<String> getLabels()
            {
                return labels;
            }
        };
    }

    protected static Review fromGitLabMergeRequest(String projectId, String patchReleaseVersion, MergeRequest mergeRequest)
    {
        if (mergeRequest == null)
        {
            return null;
        }

        String sourceBranchName = mergeRequest.getSourceBranch();
        WorkspaceInfo workspaceInfo = parseWorkspaceBranchName(sourceBranchName, patchReleaseVersion);
        if ((workspaceInfo == null) || (workspaceInfo.getWorkspaceAccessType() != ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE))
        {
            return null;
        }
        return newReview(mergeRequest.getIid(), projectId, workspaceInfo, mergeRequest.getTitle(), mergeRequest.getDescription(), mergeRequest.getCreatedAt(), mergeRequest.getUpdatedAt(), mergeRequest.getClosedAt(), mergeRequest.getMergedAt(), mergeRequest.getState(), mergeRequest.getAuthor(), mergeRequest.getMergeCommitSha(), mergeRequest.getWebUrl(), mergeRequest.getLabels());
    }

    protected static Review newReview(Integer reviewId, String projectId, WorkspaceInfo workspaceInfo, String title, String description, Date createdAt, Date lastUpdatedAt, Date closedAt, Date committedAt, String reviewState, AbstractUser<?> author, String commitRevisionId, String webURL, List<String> labels)
    {
        return newReview(reviewId, projectId, workspaceInfo.getWorkspaceId(), workspaceInfo.getWorkspaceType(), title, description, createdAt, lastUpdatedAt, closedAt, committedAt, reviewState, author, commitRevisionId, webURL, labels);
    }

    protected static Review newReview(Integer reviewId, String projectId, String workspaceId, WorkspaceType workspaceType, String title, String description, Date createdAt, Date lastUpdatedAt, Date closedAt, Date committedAt, String reviewState, AbstractUser<?> author, String commitRevisionId, String webURL, List<String> labels)
    {
        return newReview(toStringIfNotNull(reviewId), projectId, workspaceId, workspaceType, title, description, toInstantIfNotNull(createdAt), toInstantIfNotNull(lastUpdatedAt), toInstantIfNotNull(closedAt), toInstantIfNotNull(committedAt), getReviewState(reviewState), fromGitLabAbstractUser(author), commitRevisionId, webURL, labels);
    }

    protected Stream<Review> addReviewFilters(Stream<Review> stream, ReviewState state, BiPredicate<String, WorkspaceType> workspaceIdAndTypePredicate, Instant since, Instant until, Integer limit)
    {
        return addWorkspaceIdAndTypeFilter(addLimitFilter(addTimeFilter(addStateFilter(stream, state), state, since, until), limit), workspaceIdAndTypePredicate);
    }

    public Stream<Review> addWorkspaceIdAndTypeFilter(Stream<Review> stream, BiPredicate<String, WorkspaceType> workspaceIdAndTypePredicate)
    {
        return workspaceIdAndTypePredicate == null ? stream : stream.filter(r -> workspaceIdAndTypePredicate.test(r.getWorkspaceId(), r.getWorkspaceType()));
    }

    private Stream<Review> addStateFilter(Stream<Review> stream, ReviewState state)
    {
        return (state == null) ? stream : stream.filter(r -> r.getState() == state);
    }

    private Stream<Review> addTimeFilter(Stream<Review> stream, ReviewState state, Instant since, Instant until)
    {
        Predicate<Review> timePredicate = getTimePredicate(state, since, until);
        return (timePredicate == null) ? stream : stream.filter(timePredicate);
    }

    private boolean isCreatedAtWithinBounds(Review review, Instant lowerBound, Instant upperBound)
    {
        return isReviewTimeWithinBounds(review, Review::getCreatedAt, lowerBound, upperBound, "Created at");
    }

    private boolean isClosedAtWithinBounds(Review review, Instant lowerBound, Instant upperBound)
    {
        return isReviewTimeWithinBounds(review, Review::getClosedAt, lowerBound, upperBound, "Closed at");
    }

    private boolean isCommittedAtWithinBounds(Review review, Instant lowerBound, Instant upperBound)
    {
        return isReviewTimeWithinBounds(review, Review::getCommittedAt, lowerBound, upperBound, "Committed at");
    }

    private boolean isUpdatedAtWithinBounds(Review review, Instant lowerBound, Instant upperBound)
    {
        return isReviewTimeWithinBounds(review, Review::getLastUpdatedAt, lowerBound, upperBound, "Last updated at");
    }

    private boolean isReviewTimeWithinBounds(Review review, Function<? super Review, ? extends Instant> function, Instant lowerBound, Instant upperBound, String descriptionForLogging)
    {
        Instant time = function.apply(review);
        if (time == null)
        {
            if (descriptionForLogging != null)
            {
                LOGGER.warn("{} time missing for review {} in project {}", descriptionForLogging, review.getId(), review.getProjectId());
            }
            return false;
        }
        return isInstantWithinBounds(time, lowerBound, upperBound);
    }

    private static boolean isInstantWithinBounds(Instant time, Instant lowerBound, Instant upperBound)
    {
        return ((lowerBound == null) || lowerBound.compareTo(time) <= 0) && ((upperBound == null) || upperBound.compareTo(time) >= 0);
    }

    protected static Approval fromGitLabMergeRequest(MergeRequest mergeRequest)
    {
        if ((mergeRequest == null) || (mergeRequest.getApprovedBy() == null))
        {
            return null;
        }
        return newApproval(mergeRequest.getApprovedBy().stream().map(BaseGitLabApi::fromGitLabAbstractUser).collect(Collectors.toList()));
    }

    private static Approval newApproval(List<User> approvedBy)
    {
        return new Approval()
        {
            @Override
            public List<User> getApprovedBy()
            {
                return approvedBy;
            }
        };
    }

    private Predicate<Review> getTimePredicate(ReviewState state, Instant since, Instant until)
    {
        if ((since == null) && (until == null))
        {
            return null;
        }

        switch ((state == null) ? ReviewState.UNKNOWN : state)
        {
            case OPEN:
            {
                return review -> isCreatedAtWithinBounds(review, since, until) || isUpdatedAtWithinBounds(review, since, until);
            }
            case CLOSED:
            {
                return review -> isClosedAtWithinBounds(review, since, until) || isUpdatedAtWithinBounds(review, since, until);
            }
            case COMMITTED:
            {
                return review -> isCommittedAtWithinBounds(review, since, until) || isUpdatedAtWithinBounds(review, since, until);
            }
            default:
            {
                return review ->
                {
                    if (isUpdatedAtWithinBounds(review, since, until))
                    {
                        return true;
                    }
                    if (review.getState() == null)
                    {
                        LOGGER.warn("State missing for review {} in project {}; cannot filter based on time", review.getId(), review.getProjectId());
                        return false;
                    }
                    switch (review.getState())
                    {
                        case OPEN:
                        case UNKNOWN:
                        {
                            return isCreatedAtWithinBounds(review, since, until);
                        }
                        case COMMITTED:
                        {
                            return isCommittedAtWithinBounds(review, since, until);
                        }
                        case CLOSED:
                        {
                            return isClosedAtWithinBounds(review, since, until);
                        }
                        default:
                        {
                            LOGGER.warn("Unhandled state for review {} in project {}: {}; cannot filter based on time", review.getId(), review.getState(), review.getProjectId());
                            return false;
                        }
                    }
                };
            }
        }
    }

    private Stream<Review> addLimitFilter(Stream<Review> stream, Integer limit)
    {
        return ((limit == null) || (limit <= 0)) ? stream : stream.limit(limit);
    }

    protected MergeRequestFilter withMergeRequestFilters(MergeRequestFilter mergeRequestFilter, ReviewState state, Instant since, Instant until)
    {
        if ((since != null) && (state != null))
        {
            switch (state)
            {
                case CLOSED:
                case COMMITTED:
                {
                    mergeRequestFilter.setUpdatedAfter(Date.from(since));
                    break;
                }
                case OPEN:
                {
                    mergeRequestFilter.setCreatedAfter(Date.from(since));
                    break;
                }
                default:
                {
                    // no filter can be created for other states
                }
            }
        }

        if (until != null)
        {
            mergeRequestFilter.setCreatedBefore(Date.from(until));
        }

        return mergeRequestFilter.withState(getMergeRequestState(state));
    }

    protected List<Review> getReviews(String projectId, String patchReleaseVersion, ReviewState state, Iterable<String> revisionIds, BiPredicate<String, WorkspaceType> workspaceIdAndTypePredicate, Instant since, Instant until, Integer limit)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        Set<String> revisionIdSet;
        if (revisionIds == null)
        {
            revisionIdSet = Collections.emptySet();
        }
        else if (revisionIds instanceof Set)
        {
            revisionIdSet = (Set<String>) revisionIds;
        }
        else
        {
            revisionIdSet = Sets.mutable.withAll(revisionIds);
        }
        Stream<MergeRequest> mergeRequestStream;
        try
        {
            GitLabProjectId gitLabProjectId = parseProjectId(projectId);
            if (!revisionIdSet.isEmpty()) // Do we want to have a check here to know whether revisions belong to the protected branch?
            {
                // TODO: we might want to do this differently since the number of revision IDs can be huge
                // we can have a threshold for which we change our strategy to  to make a single call for
                // merge requests by the other criteria and then filter by revisionIds.
                MutableIntSet mergeRequestIds = IntSets.mutable.empty();
                CommitsApi commitsApi = getGitLabApi().getCommitsApi();
                // Combine all MRs associated with each revision
                mergeRequestStream = revisionIdSet.stream().flatMap(revisionId ->
                {
                    try
                    {
                        return PagerTools.stream(withRetries(() -> commitsApi.getMergeRequests(gitLabProjectId.getGitLabId(), revisionId, ITEMS_PER_PAGE)));
                    }
                    catch (Exception e)
                    {
                        throw buildException(e,
                                () -> "User " + getCurrentUser() + " is not allowed to get reviews associated with revision " + revisionId + " for project " + projectId,
                                () -> "Unknown revision (" + revisionId + ") or project (" + projectId + ")",
                                () -> "Error getting reviews associated with revision " + revisionId + " for project " + projectId);
                    }
                }).filter(mr -> (mr.getIid() != null) && mergeRequestIds.add(mr.getIid())); // remove duplicates
                Constants.MergeRequestState mergeRequestState = getMergeRequestState(state);
                if (mergeRequestState != Constants.MergeRequestState.ALL)
                {
                    String mergeRequestStateString = mergeRequestState.toString();
                    mergeRequestStream = mergeRequestStream.filter(mr -> mergeRequestStateString.equalsIgnoreCase(mr.getState()));
                }
            }
            else
            {
                // if no revision ID is specified we will use the default merge request API from Gitlab to take advantage of the filter
                MergeRequestFilter mergeRequestFilter = withMergeRequestFilters(new MergeRequestFilter(), state, since, until).withProjectId(gitLabProjectId.getGitLabId());
                mergeRequestStream = PagerTools.stream(withRetries(() -> getGitLabApi().getMergeRequestApi().getMergeRequests(mergeRequestFilter, ITEMS_PER_PAGE)));
            }
            String targetBranch = getSourceBranch(gitLabProjectId, patchReleaseVersion);
            Stream<Review> stream = mergeRequestStream.filter(mr -> isReviewMergeRequest(mr, targetBranch, patchReleaseVersion)).map(mr -> fromGitLabMergeRequest(projectId, patchReleaseVersion, mr));
            return addReviewFilters(stream, state, workspaceIdAndTypePredicate, since, until, limit).collect(Collectors.toList());
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to get reviews for project " + projectId + ((state == null) ? "" : (" with state " + state)),
                    () -> "Unknown project (" + projectId + ")",
                    () -> "Error getting reviews for project " + projectId + ((state == null) ? "" : (" with state " + state)));
        }
    }

    protected void validateMergeRequestReviewState(MergeRequest mergeRequest, ReviewState expectedState)
    {
        ReviewState actualState = getReviewState(mergeRequest);
        if (expectedState != actualState)
        {
            throw new LegendSDLCServerException("Review is not " + expectedState.name().toLowerCase() + " (state: " + actualState.name().toLowerCase() + ")", Status.CONFLICT);
        }
    }

    protected MergeRequest updateMergeRequestState(MergeRequestApi mergeRequestApi, GitLabProjectId projectId, String patchReleaseVersion, MergeRequest mergeRequest, Constants.StateEvent stateEvent) throws GitLabApiException
    {
        return mergeRequestApi.updateMergeRequest(projectId.getGitLabId(), mergeRequest.getIid(), getSourceBranch(projectId, patchReleaseVersion), null, null, null, stateEvent, null, null, null, null, null, null);
    }

    protected Review closeReview(String projectId, String patchReleaseVersion, String reviewId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(reviewId, "reviewId may not be null");

        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        MergeRequestApi mergeRequestApi = getGitLabApi().getMergeRequestApi();
        MergeRequest mergeRequest = getReviewMergeRequest(mergeRequestApi, gitLabProjectId, patchReleaseVersion, reviewId);
        validateMergeRequestReviewState(mergeRequest, ReviewState.OPEN);
        try
        {
            MergeRequest closeMergeRequest = updateMergeRequestState(mergeRequestApi, gitLabProjectId, patchReleaseVersion, mergeRequest, Constants.StateEvent.CLOSE);
            return fromGitLabMergeRequest(projectId, patchReleaseVersion, closeMergeRequest);
        }
        catch (Exception e)
        {
            throw buildException(e,
                    () -> "User " + getCurrentUser() + " is not allowed to close review " + reviewId + " in project " + projectId,
                    () -> "Unknown review in project " + projectId + ": " + reviewId,
                    () -> "Error closing review " + reviewId + " in project " + projectId);
        }
    }
}
