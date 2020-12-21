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

import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.domain.model.review.ReviewState;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.revision.RevisionAlias;
import org.finos.legend.sdlc.domain.model.user.User;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabAuthException;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabMode;
import org.finos.legend.sdlc.server.gitlab.tools.GitLabApiTools;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.finos.legend.sdlc.server.tools.StringTools;
import org.finos.legend.sdlc.server.tools.ThrowingRunnable;
import org.finos.legend.sdlc.server.tools.ThrowingSupplier;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.MergeRequestApi;
import org.gitlab4j.api.models.AbstractUser;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.Tag;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Date;
import java.util.Random;
import java.util.function.Function;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response.Status;

abstract class BaseGitLabApi
{
    private static final Random RANDOM = new Random();
    private static final Encoder RANDOM_ID_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final Pattern PACKAGEABLE_ELEMENT_PATH_PATTERN = Pattern.compile("^\\w++(::\\w++)*+$");

    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_RETRY_WAIT_INTERVAL_MILLIS = 1000L;
    private static final LongUnaryOperator RETRY_WAIT_INTERVAL_UPDATER = w -> w + 1000L;

    private static final String WORKSPACE_BRANCH_PREFIX = "workspace";
    private static final String CONFLICT_RESOLUTION_BRANCH_PREFIX = "resolution";
    private static final String BACKUP_BRANCH_PREFIX = "backup";
    private static final String TEMPORARY_BRANCH_PREFIX = "tmp";

    private static final String MR_STATE_OPENED = "opened";
    private static final String MR_STATE_CLOSED = "closed";
    private static final String MR_STATE_LOCKED = "locked";
    private static final String MR_STATE_MERGED = "merged";

    protected static final int ITEMS_PER_PAGE = 100;
    protected static final String MASTER_BRANCH = "master";

    protected static final String PACKAGE_SEPARATOR = "::";

    protected static final char BRANCH_DELIMITER = '/';

    protected static final String RELEASE_TAG_PREFIX = "release-";
    protected static final Pattern RELEASE_TAG_NAME_PATTERN = Pattern.compile("^" + RELEASE_TAG_PREFIX + "\\d+\\.\\d+\\.\\d+$");

    private final GitLabUserContext userContext;

    protected BaseGitLabApi(GitLabUserContext userContext)
    {
        this.userContext = userContext;
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
            throw new LegendSDLCServerException("Invalid project id: " + projectId, Status.BAD_REQUEST, e);
        }
        if (!this.userContext.isValidMode(gitLabProjectId.getGitLabMode()))
        {
            throw new LegendSDLCServerException("Unknown project: " + projectId, Status.NOT_FOUND);
        }
        return gitLabProjectId;
    }

    protected GitLabApi getGitLabApi(GitLabMode mode)
    {
        try
        {
            return this.userContext.getGitLabAPI(mode);
        }
        catch (LegendSDLCServerException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            StringBuilder message = new StringBuilder("Error getting GitLabApi for user ").append(getCurrentUser()).append(" and mode ").append(mode);
            String detail = (e instanceof GitLabAuthException) ? ((GitLabAuthException) e).getDetail() : e.getMessage();
            if (detail != null)
            {
                message.append(": ").append(detail);
            }
            throw new LegendSDLCServerException(message.toString(), e);
        }
    }

    protected Iterable<GitLabMode> getValidGitLabModes()
    {
        return this.userContext.getValidGitLabModes();
    }

    protected boolean isValidGitLabMode(GitLabMode mode)
    {
        return this.userContext.isValidMode(mode);
    }

    protected static boolean isValidEntityName(String string)
    {
        return (string != null) && !string.isEmpty() && string.chars().allMatch(c -> (c == '_') || Character.isLetterOrDigit(c));
    }

    protected static boolean isValidEntityPath(String string)
    {
        return isValidPackageableElementPath(string) &&
                !string.startsWith("meta::") &&
                string.contains(PACKAGE_SEPARATOR);
    }

    protected static boolean isValidPackagePath(String string)
    {
        return isValidPackageableElementPath(string);
    }

    protected static boolean isValidClassifierPath(String string)
    {
        return isValidPackageableElementPath(string) && string.startsWith("meta::");
    }

    protected static boolean isValidPackageableElementPath(String string)
    {
        return (string != null) && PACKAGEABLE_ELEMENT_PATH_PATTERN.matcher(string).matches();
    }

    protected static String getWorkspaceBranchNamePrefix(ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        assert workspaceAccessType != null : "Workspace access type has been used but it is null, which should not be the case";
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

    protected String getBranchName(String workspaceId, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        return (workspaceId == null) ? MASTER_BRANCH : createUserBranchName(getWorkspaceBranchNamePrefix(workspaceAccessType), getCurrentUser(), workspaceId);
    }

    protected String getUserWorkspaceBranchName(String workspaceId, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        return createUserBranchName(getWorkspaceBranchNamePrefix(workspaceAccessType), getCurrentUser(), workspaceId);
    }

    protected String newUserTemporaryBranchName()
    {
        return createUserBranchName(TEMPORARY_BRANCH_PREFIX, getCurrentUser(), newTemporaryBranchId());
    }

    protected String newUserTemporaryBranchName(String workspaceId)
    {
        return createUserBranchName(TEMPORARY_BRANCH_PREFIX, getCurrentUser(), workspaceId, newTemporaryBranchId());
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

    private static String createUserBranchName(String first, String... more)
    {
        int moreCount = (more == null) ? 0 : more.length;
        if (moreCount == 0)
        {
            return first;
        }

        StringBuilder builder = new StringBuilder(first.length() + (moreCount * 16));
        builder.append(first);
        for (int i = 0; i < moreCount; i++)
        {
            builder.append(BRANCH_DELIMITER).append(more[i]);
        }
        return builder.toString();
    }

    protected static String getWorkspaceIdFromWorkspaceBranchName(String workspaceBranchName, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        int userIdEndIndex = workspaceBranchName.indexOf(BRANCH_DELIMITER, getWorkspaceBranchNamePrefix(workspaceAccessType).length() + 1);
        if (userIdEndIndex == -1)
        {
            throw new IllegalArgumentException("Invalid workspace branch name: " + workspaceBranchName);
        }
        return workspaceBranchName.substring(userIdEndIndex + 1);
    }


    protected static boolean isWorkspaceBranchName(String branchName, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        return branchNameStartsWith(branchName, getWorkspaceBranchNamePrefix(workspaceAccessType), (String[]) null);
    }

    protected boolean isUserWorkspaceBranchName(String branchName, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        return branchNameStartsWith(branchName, getWorkspaceBranchNamePrefix(workspaceAccessType), getCurrentUser());
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
        return (name != null) && RELEASE_TAG_NAME_PATTERN.matcher(name).matches();
    }

    protected static VersionId parseVersionTagName(String name)
    {
        return VersionId.parseVersionId(name, RELEASE_TAG_PREFIX.length(), name.length());
    }

    protected static boolean isVersionTag(Tag tag)
    {
        return isVersionTagName(tag.getName());
    }

    protected static ProjectType getProjectTypeFromMode(GitLabMode mode)
    {
        switch (mode)
        {
            case PROD:
            {
                return ProjectType.PRODUCTION;
            }
            case UAT:
            {
                return ProjectType.PROTOTYPE;
            }
            default:
            {
                throw new RuntimeException("Unknown GitLab mode: " + mode);
            }
        }
    }

    protected static GitLabMode getGitLabModeFromProjectType(ProjectType type)
    {
        switch (type)
        {
            case PRODUCTION:
            {
                return GitLabMode.PROD;
            }
            case PROTOTYPE:
            {
                return GitLabMode.UAT;
            }
            default:
            {
                throw new RuntimeException("Unknown project type: " + type);
            }
        }
    }

    protected static String getReferenceInfo(GitLabProjectId projectId, String workspaceId, String revisionId)
    {
        return getReferenceInfo(projectId.toString(), workspaceId, revisionId);
    }

    protected static String getReferenceInfo(String projectId, String workspaceId, String revisionId)
    {
        int messageLength = projectId.length() + 8;
        if (workspaceId != null)
        {
            messageLength += workspaceId.length() + 14;
        }
        if (revisionId != null)
        {
            messageLength += revisionId.length() + 13;
        }
        return appendReferenceInfo(new StringBuilder(messageLength), projectId, workspaceId, revisionId).toString();
    }

    protected static StringBuilder appendReferenceInfo(StringBuilder builder, String projectId, String workspaceId, String revisionId)
    {
        if (revisionId != null)
        {
            builder.append("revision ").append(revisionId).append(" of ");
        }
        if (workspaceId != null)
        {
            builder.append("workspace ").append(workspaceId).append(" of ");
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
        return buildException(e, null, null, ex -> message.get());
    }

    protected LegendSDLCServerException buildException(Exception e, Supplier<String> forbiddenMessage, Supplier<String> notFoundMessage, Supplier<String> defaultMessage)
    {
        return buildException(e, (forbiddenMessage == null) ? null : ex -> forbiddenMessage.get(), (notFoundMessage == null) ? null : ex -> notFoundMessage.get(), ex -> defaultMessage.get());
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
                            BaseGitLabApi.this.userContext.clearAccessTokens();
                            HttpServletRequest httpRequest = BaseGitLabApi.this.userContext.getHttpRequest();
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
                            return new LegendSDLCServerException((forbiddenMessage != null) ? forbiddenMessage.apply(glae) : ((defaultMessage != null) ? defaultMessage.apply(glae) : glae.getMessage()), Status.FORBIDDEN, glae);
                        }
                        case 404:
                        {
                            return new LegendSDLCServerException((notFoundMessage != null) ? notFoundMessage.apply(glae) : ((defaultMessage != null) ? defaultMessage.apply(glae) : glae.getMessage()), Status.NOT_FOUND, glae);
                        }
                        default:
                        {
                            return null;
                        }
                    }
                },
                (defaultMessage == null) ? null : ex ->
                {
                    String message = defaultMessage.apply(ex);
                    if (message == null)
                    {
                        return null;
                    }
                    return new LegendSDLCServerException(StringTools.appendThrowableMessageIfPresent(message, ex), ex);
                }
        );
    }

    protected LegendSDLCServerException processException(Exception e, Function<? super LegendSDLCServerException, ? extends LegendSDLCServerException> meHandler, Function<? super GitLabApiException, ? extends LegendSDLCServerException> glaeHandler, Function<? super Exception, ? extends LegendSDLCServerException> defaultHandler)
    {
        // Special handling
        if ((meHandler != null) && (e instanceof LegendSDLCServerException))
        {
            LegendSDLCServerException result = meHandler.apply((LegendSDLCServerException) e);
            if (result != null)
            {
                return result;
            }
        }
        else if ((glaeHandler != null) && (e instanceof GitLabApiException))
        {
            LegendSDLCServerException result = glaeHandler.apply((GitLabApiException) e);
            if (result != null)
            {
                return result;
            }
        }

        // Provided default handling
        if (defaultHandler != null)
        {
            LegendSDLCServerException result = defaultHandler.apply(e);
            if (result != null)
            {
                return result;
            }
        }

        // Default default handling (final fall through case)
        return new LegendSDLCServerException(StringTools.appendThrowableMessageIfPresent("An unexpected exception occurred", e), e);
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

    protected static Workspace fromWorkspaceBranchName(String projectId, String branchName, ProjectFileAccessProvider.WorkspaceAccessType workspaceAccessType)
    {
        int userIdStartIndex = getWorkspaceBranchNamePrefix(workspaceAccessType).length() + 1;
        int userIdEndIndex = branchName.indexOf(BRANCH_DELIMITER, userIdStartIndex);
        String userId = branchName.substring(userIdStartIndex, userIdEndIndex);
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

    protected MergeRequest getReviewMergeRequest(MergeRequestApi mergeRequestApi, GitLabProjectId projectId, String reviewId)
    {
        return getReviewMergeRequest(mergeRequestApi, projectId, reviewId, false);
    }

    protected MergeRequest getReviewMergeRequest(MergeRequestApi mergeRequestApi, GitLabProjectId projectId, String reviewId, boolean includeRebaseInProgress)
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
        if (!isReviewMergeRequest(mergeRequest))
        {
            throw new LegendSDLCServerException("Unknown review in project " + projectId + ": " + reviewId, Status.NOT_FOUND);
        }
        return mergeRequest;
    }

    protected static boolean isReviewMergeRequest(MergeRequest mergeRequest)
    {
        return (mergeRequest != null) && isWorkspaceBranchName(mergeRequest.getSourceBranch(), ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE);
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
            if (RevisionAlias.HEAD.getValue().equalsIgnoreCase(revisionId)
                    || RevisionAlias.CURRENT.getValue().equalsIgnoreCase(revisionId)
                    || RevisionAlias.LATEST.getValue().equalsIgnoreCase(revisionId))
            {
                return RevisionAlias.HEAD;
            }
            return RevisionAlias.REVISION_ID;
        }
        return null;
    }
}
