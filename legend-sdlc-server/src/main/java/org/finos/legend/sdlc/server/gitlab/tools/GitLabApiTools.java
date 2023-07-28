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

package org.finos.legend.sdlc.server.gitlab.tools;

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.monitoring.SDLCMetricsHandler;
import org.finos.legend.sdlc.server.tools.CallUntil;
import org.finos.legend.sdlc.server.tools.ThrowingSupplier;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.RepositoryApi;
import org.gitlab4j.api.models.AccessLevel;
import org.gitlab4j.api.models.Branch;
import org.gitlab4j.api.models.Commit;
import org.gitlab4j.api.models.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.LongUnaryOperator;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class GitLabApiTools
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabApiTools.class);
    private static final String RETRY_METRIC = "gitlab retryable exception";

    public static <T> T callWithRetries(ThrowingSupplier<T, ? extends GitLabApiException> apiCall, int maxRetries, long waitIntervalMillis) throws GitLabApiException
    {
        return callWithRetries(apiCall, maxRetries, waitIntervalMillis, null);
    }

    public static <T> T callWithRetries(ThrowingSupplier<T, ? extends GitLabApiException> apiCall, int maxRetries, long initialWaitIntervalMillis, LongUnaryOperator waitIntervalUpdater) throws GitLabApiException
    {
        if (maxRetries <= 0)
        {
            return apiCall.get();
        }

        List<GitLabApiException> exceptions;
        try
        {
            return apiCall.get();
        }
        catch (GitLabApiException e)
        {
            if (!isRetryableGitLabApiException(e))
            {
                throw e;
            }
            noteRetryableException();
            exceptions = Lists.mutable.ofInitialCapacity(maxRetries + 1);
            exceptions.add(e);
            LOGGER.error(getRetryableExceptionLogMessage(e, 1), e);
        }

        long waitInterval = initialWaitIntervalMillis;
        for (int i = 0; i < maxRetries; i++)
        {
            // Wait
            if (waitInterval > 0)
            {
                LOGGER.debug("Waiting {} millis for attempt #{}", waitInterval, i + 2);
                try
                {
                    Thread.sleep(waitInterval);
                }
                catch (InterruptedException e)
                {
                    LOGGER.warn("Interrupted while waiting", e);
                    Thread.currentThread().interrupt();
                }
            }

            // Try to execute API call
            try
            {
                return apiCall.get();
            }
            catch (GitLabApiException e)
            {
                if (!isRetryableGitLabApiException(e))
                {
                    addSuppressedExceptions(e, exceptions);
                    throw e;
                }
                noteRetryableException();
                exceptions.add(e);
                LOGGER.error(getRetryableExceptionLogMessage(e, i + 2), e);
            }
            catch (Exception e)
            {
                addSuppressedExceptions(e, exceptions);
                throw e;
            }

            // Update wait interval
            if (waitIntervalUpdater != null)
            {
                try
                {
                    waitInterval = waitIntervalUpdater.applyAsLong(waitInterval);
                }
                catch (Exception e)
                {
                    // log exceptions trying to update the wait interval, but otherwise ignore them
                    LOGGER.error("Exception while updating the wait interval (from {}  millis)", waitInterval, e);
                }
            }
        }

        GitLabApiException lastException = exceptions.get(exceptions.size() - 1);
        if (exceptions.size() > 1)
        {
            addSuppressedExceptions(lastException, exceptions.subList(0, exceptions.size() - 1));
        }
        throw lastException;
    }

    public static boolean isRetryableGitLabApiException(Exception e)
    {
        return (e instanceof GitLabApiException) && isRetryableGitLabApiException((GitLabApiException) e);
    }

    public static boolean isRetryableGitLabApiException(GitLabApiException e)
    {
        int status = e.getHttpStatus();
        return (status == Status.REQUEST_TIMEOUT.getStatusCode()) ||
                (status == Status.BAD_GATEWAY.getStatusCode()) ||
                (status == Status.SERVICE_UNAVAILABLE.getStatusCode()) ||
                (status == Status.GATEWAY_TIMEOUT.getStatusCode());
    }

    public static boolean isNotFoundGitLabApiException(Exception e)
    {
        return (e instanceof GitLabApiException) && isNotFoundGitLabApiException((GitLabApiException) e);
    }

    public static boolean isNotFoundGitLabApiException(GitLabApiException e)
    {
        return e.getHttpStatus() == Status.NOT_FOUND.getStatusCode();
    }

    public static GitLabApiException findGitLabApiException(Throwable throwable)
    {
        for (Throwable t = throwable; t != null; t = t.getCause())
        {
            if (t instanceof GitLabApiException)
            {
                return (GitLabApiException) t;
            }
        }
        return null;
    }

    private static void addSuppressedExceptions(Throwable t, Iterable<? extends Throwable> suppressed)
    {
        suppressed.forEach(s -> addSuppressedException(t, s));
    }

    private static void addSuppressedException(Throwable t, Throwable suppressed)
    {
        try
        {
            t.addSuppressed(suppressed);
        }
        catch (Exception ignore)
        {
            // ignore this
        }
    }

    private static String getRetryableExceptionLogMessage(GitLabApiException e, int attemptNum)
    {
        StringBuilder builder = new StringBuilder("Retryable exception encountered on attempt #").append(attemptNum);
        builder.append("; HTTP status: ").append(e.getHttpStatus());
        String eMessage = e.getMessage();
        if (eMessage != null)
        {
            builder.append("; message: ").append(eMessage);
        }
        return builder.toString();
    }

    private static void noteRetryableException()
    {
        SDLCMetricsHandler.incrementCounter(RETRY_METRIC);
    }

    public static boolean branchExists(GitLabApi api, Object projectIdOrPath, String branchName) throws GitLabApiException
    {
        return branchExists(api.getRepositoryApi(), projectIdOrPath, branchName);
    }

    public static boolean branchExists(RepositoryApi api, Object projectIdOrPath, String branchName) throws GitLabApiException
    {
        return getBranch(api, projectIdOrPath, branchName) != null;
    }

    public static Branch getBranch(GitLabApi api, Object projectIdOrPath, String branchName) throws GitLabApiException
    {
        return getBranch(api.getRepositoryApi(), projectIdOrPath, branchName);
    }

    public static Branch getBranch(RepositoryApi api, Object projectIdOrPath, String branchName) throws GitLabApiException
    {
        try
        {
            return api.getBranch(projectIdOrPath, branchName);
        }
        catch (GitLabApiException e)
        {
            if (isNotFoundGitLabApiException(e))
            {
                return null;
            }
            throw e;
        }
    }

    public static boolean deleteBranchAndVerify(GitLabApi api, Object projectIdOrPath, String branchName, int maxVerificationTries, long verificationWaitMillis) throws GitLabApiException
    {
        return deleteBranchAndVerify(api.getRepositoryApi(), projectIdOrPath, branchName, maxVerificationTries, verificationWaitMillis);
    }

    public static boolean deleteBranchAndVerify(RepositoryApi api, Object projectIdOrPath, String branchName, int maxVerificationTries, long verificationWaitMillis) throws GitLabApiException
    {
        LOGGER.debug("Deleting branch {} in project {}", branchName, projectIdOrPath);

        // Try to delete branch
        try
        {
            api.deleteBranch(projectIdOrPath, branchName);
        }
        catch (GitLabApiException e)
        {
            if (isNotFoundGitLabApiException(e))
            {
                LOGGER.debug("Branch {} does not exist in project {}: nothing to delete", branchName, projectIdOrPath);
                return true;
            }
            throw e;
        }

        // Verify that the branch is gone
        CallUntil<Branch, GitLabApiException> callUntil = CallUntil.callUntil(() -> getBranch(api, projectIdOrPath, branchName), Objects::isNull, maxVerificationTries, verificationWaitMillis);
        LOGGER.debug("Deleting branch {} in project {} {}", branchName, projectIdOrPath, callUntil.succeeded() ? "succeeded" : "failed");
        return callUntil.succeeded();
    }

    public static Branch createBranchAndVerify(RepositoryApi api, Object projectIdOrPath, String branchName, String sourceCommitId, int maxVerificationTries, long verificationWaitMillis) throws GitLabApiException
    {
        LOGGER.debug("Creating branch {} in project {} from commit {}", branchName, projectIdOrPath, sourceCommitId);

        Branch branch = getBranchAtCommit(api, projectIdOrPath, branchName, sourceCommitId);
        if (branch == null)
        {
            // Branch does not exist in the expected form, try to create it
            api.createBranch(projectIdOrPath, branchName, sourceCommitId);
            CallUntil<Branch, GitLabApiException> callUntil = CallUntil.callUntil(() -> getBranchAtCommit(api, projectIdOrPath, branchName, sourceCommitId), Objects::nonNull, maxVerificationTries, verificationWaitMillis);
            if (callUntil.succeeded())
            {
                branch = callUntil.getResult();
            }
            LOGGER.debug("Creating branch {} in project {} from commit {} {}", branchName, projectIdOrPath, sourceCommitId, (branch == null) ? "failed" : "succeeded");
        }
        else
        {
            // Branch already exists in the expected form, no need to create it
            LOGGER.debug("Branch {} already exists in project {} with commit {}", branchName, projectIdOrPath, sourceCommitId);
        }
        return branch;
    }

    private static Branch getBranchAtCommit(RepositoryApi api, Object projectIdOrPath, String branchName, String expectedCommitId) throws GitLabApiException
    {
        Branch branch = getBranch(api, projectIdOrPath, branchName);
        if (branch != null)
        {
            Commit branchCommit = branch.getCommit();
            if ((branchCommit == null) || !expectedCommitId.equals(branchCommit.getId()))
            {
                return null;
            }
        }
        return branch;
    }

    public static Branch createBranchFromSourceBranchAndVerify(RepositoryApi api, Object projectIdOrPath, String branchName, String sourceBranchName, int maxVerificationTries, long verificationWaitMillis) throws GitLabApiException
    {
        Branch sourceBranch = null;
        CallUntil<Branch, GitLabApiException> callUntil = CallUntil.callUntil(() -> getBranch(api, projectIdOrPath, sourceBranchName), Objects::nonNull, maxVerificationTries, verificationWaitMillis);
        if (callUntil.succeeded())
        {
            sourceBranch = callUntil.getResult();
        }
        if (sourceBranch == null)
        {
            LOGGER.warn("Failed to get source branch {} in project {}. Aborting branch creation from source branch.", sourceBranchName, projectIdOrPath);
            return null;
        }
        return createBranchAndVerify(api, projectIdOrPath, branchName, sourceBranch.getCommit().getId(), maxVerificationTries, verificationWaitMillis);
    }

    @Deprecated
    public static Branch createProtectedBranchFromSourceTagAndVerify(GitLabApi api, GitLabProjectId projectId, String branchName, String sourceTagName, int maxVerificationTries, long verificationWaitMillis) throws GitLabApiException
    {
        return createProtectedBranchFromSourceTagAndVerify(api, projectId.getGitLabId(), branchName, sourceTagName, maxVerificationTries, verificationWaitMillis);
    }

    @Deprecated
    public static Tag getTag(GitLabApi api, GitLabProjectId gitLabProjectId, String tagName) throws GitLabApiException
    {
        return getTag(api, gitLabProjectId.getGitLabId(), tagName);
    }

    @Deprecated
    public static boolean tagExists(GitLabApi api, GitLabProjectId gitLabProjectId, String tagName) throws GitLabApiException
    {
        return tagExists(api, gitLabProjectId.getGitLabId(), tagName);
    }

    public static Branch createProtectedBranchFromSourceTagAndVerify(GitLabApi api, Object projectIdOrPath, String branchName, String sourceTagName, int maxVerificationTries, long verificationWaitMillis) throws GitLabApiException
    {
        Tag sourceTag = getTag(api, projectIdOrPath, sourceTagName);
        if (sourceTag == null)
        {
            throw new LegendSDLCServerException("Source release version " + sourceTagName + " does not exist", Response.Status.CONFLICT);
        }
        Branch targetBranch = createBranchAndVerify(api.getRepositoryApi(), projectIdOrPath, branchName, sourceTag.getCommit().getId(), maxVerificationTries, verificationWaitMillis);
        api.getProtectedBranchesApi().protectBranch(projectIdOrPath, branchName, AccessLevel.NONE, AccessLevel.MAINTAINER, AccessLevel.MAINTAINER, true);
        return targetBranch;
    }

    public static Tag getTag(GitLabApi api, Object projectIdOrPath, String tagName) throws GitLabApiException
    {
        try
        {
           return api.getTagsApi().getTag(projectIdOrPath, tagName);
        }
        catch (GitLabApiException e)
        {
            if (isNotFoundGitLabApiException(e))
            {
                return null;
            }
            throw e;
        }
    }

    public static boolean tagExists(GitLabApi api, Object projectIdOrPath, String tagName) throws GitLabApiException
    {
        return getTag(api, projectIdOrPath, tagName) != null;
    }
}
