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

import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.sdlc.domain.model.version.Version;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.version.NewVersionType;
import org.finos.legend.sdlc.server.domain.api.version.VersionApi;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabConfiguration;
import org.finos.legend.sdlc.server.gitlab.GitLabProjectId;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.tools.GitLabApiTools;
import org.finos.legend.sdlc.server.gitlab.tools.PagerTools;
import org.finos.legend.sdlc.server.tools.BackgroundTaskProcessor;
import org.gitlab4j.api.CommitsApi;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Commit;
import org.gitlab4j.api.models.CommitRef;
import org.gitlab4j.api.models.CommitRef.RefType;
import org.gitlab4j.api.models.ReleaseParams;
import org.gitlab4j.api.models.Tag;

import java.util.Comparator;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.ws.rs.core.Response.Status;

public class GitLabVersionApi extends GitLabApiWithFileAccess implements VersionApi
{
    private static final VersionId NULL_VERSION = VersionId.newVersionId(0, 0, 0);

    @Inject
    public GitLabVersionApi(GitLabConfiguration gitLabConfiguration, GitLabUserContext userContext, BackgroundTaskProcessor backgroundTaskProcessor)
    {
        super(gitLabConfiguration, userContext, backgroundTaskProcessor);
    }

    @Override
    public List<Version> getVersions(String projectId, Integer minMajorVersion, Integer maxMajorVersion, Integer minMinorVersion, Integer maxMinorVersion, Integer minPatchVersion, Integer maxPatchVersion)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        List<Version> versions = getVersions(gitLabProjectId, minMajorVersion, maxMajorVersion, minMinorVersion, maxMinorVersion, minPatchVersion, maxPatchVersion).collect(Collectors.toList());
        versions.sort(Comparator.comparing(Version::getId).reversed());
        return versions;
    }

    @Override
    public Version getLatestVersion(String projectId, Integer minMajorVersion, Integer maxMajorVersion, Integer minMinorVersion, Integer maxMinorVersion, Integer minPatchVersion, Integer maxPatchVersion)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        return getLatestVersion(parseProjectId(projectId), minMajorVersion, maxMajorVersion, minMinorVersion, maxMinorVersion, minPatchVersion, maxPatchVersion);
    }

    @Override
    public Version getVersion(String projectId, int majorVersion, int minorVersion, int patchVersion)
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

    @Override
    public Version newVersion(String projectId, NewVersionType type, String revisionId, String notes)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(type, "type may not be null");
        GitLabProjectId gitLabProjectId = parseProjectId(projectId);
        Version latestVersion = getLatestVersion(gitLabProjectId);
        VersionId latestVersionId = (latestVersion == null) ? NULL_VERSION : latestVersion.getId();
        VersionId nextVersionId;
        switch (type)
        {
            case MAJOR:
            {
                nextVersionId = latestVersionId.nextMajorVersion();
                break;
            }
            case MINOR:
            {
                nextVersionId = latestVersionId.nextMinorVersion();
                break;
            }
            case PATCH:
            {
                nextVersionId = latestVersionId.nextPatchVersion();
                break;
            }
            default:
            {
                throw new LegendSDLCServerException("Unknown new version type: " + type, Status.BAD_REQUEST);
            }
        }
        return newVersion(gitLabProjectId, revisionId, nextVersionId, notes);
    }

    private Stream<Version> getVersions(GitLabProjectId projectId, Integer minMajorVersion, Integer maxMajorVersion, Integer minMinorVersion, Integer maxMinorVersion, Integer minPatchVersion, Integer maxPatchVersion)
    {
        try
        {
            Stream<Version> stream = PagerTools.stream(getGitLabApi().getTagsApi().getTags(projectId.getGitLabId(), ITEMS_PER_PAGE)).filter(GitLabVersionApi::isVersionTag).map(tag -> fromGitLabTag(projectId.toString(), tag));

            // major version constraint
            if ((minMajorVersion != null) && (maxMajorVersion != null))
            {
                int minMajorVersionInt = minMajorVersion;
                int maxMajorVersionInt = maxMajorVersion;
                if (minMajorVersionInt == maxMajorVersionInt)
                {
                    stream = stream.filter(v -> v.getId().getMajorVersion() == minMajorVersionInt);
                }
                else
                {
                    stream = stream.filter(v ->
                    {
                        int majorVersion = v.getId().getMajorVersion();
                        return (minMajorVersionInt <= majorVersion) && (majorVersion <= maxMajorVersionInt);
                    });
                }
            }
            else if (minMajorVersion != null)
            {
                int minMajorVersionInt = minMajorVersion;
                stream = stream.filter(v -> v.getId().getMajorVersion() >= minMajorVersionInt);
            }
            else if (maxMajorVersion != null)
            {
                int maxMajorVersionInt = maxMajorVersion;
                stream = stream.filter(v -> v.getId().getMajorVersion() <= maxMajorVersionInt);
            }

            // minor version constraint
            if ((minMinorVersion != null) && (maxMinorVersion != null))
            {
                int minMinorVersionInt = minMinorVersion;
                int maxMinorVersionInt = maxMinorVersion;
                if (minMinorVersionInt == maxMinorVersionInt)
                {
                    stream = stream.filter(v -> v.getId().getMinorVersion() == minMinorVersionInt);
                }
                else
                {
                    stream = stream.filter(v ->
                    {
                        int minorVersion = v.getId().getMinorVersion();
                        return (minMinorVersionInt <= minorVersion) && (minorVersion <= maxMinorVersionInt);
                    });
                }
            }
            else if (minMinorVersion != null)
            {
                int minMinorVersionInt = minMinorVersion;
                stream = stream.filter(v -> v.getId().getMinorVersion() >= minMinorVersionInt);
            }
            else if (maxMinorVersion != null)
            {
                int maxMinorVersionInt = maxMinorVersion;
                stream = stream.filter(v -> v.getId().getMinorVersion() <= maxMinorVersionInt);
            }


            // patch version constraint
            if ((minPatchVersion != null) && (maxPatchVersion != null))
            {
                int minPatchVersionInt = minPatchVersion;
                int maxPatchVersionInt = maxPatchVersion;
                if (minPatchVersionInt == maxPatchVersionInt)
                {
                    stream = stream.filter(v -> v.getId().getPatchVersion() == minPatchVersionInt);
                }
                else
                {
                    stream = stream.filter(v ->
                    {
                        int patchVersion = v.getId().getPatchVersion();
                        return (minPatchVersionInt <= patchVersion) && (patchVersion <= maxPatchVersionInt);
                    });
                }
            }
            else if (minPatchVersion != null)
            {
                int minPatchVersionInt = minPatchVersion;
                stream = stream.filter(v -> v.getId().getPatchVersion() >= minPatchVersionInt);
            }
            else if (maxPatchVersion != null)
            {
                int maxPatchVersionInt = maxPatchVersion;
                stream = stream.filter(v -> v.getId().getPatchVersion() <= maxPatchVersionInt);
            }

            return stream;
        }
        catch (Exception e)
        {
            throw buildException(e,
                () -> "User " + getCurrentUser() + " is not allowed to get versions for project " + projectId,
                () -> "Unknown project: " + projectId,
                () -> "Error getting versions for project " + projectId);
        }
    }

    private Version getLatestVersion(GitLabProjectId projectId)
    {
        return getLatestVersion(projectId, null, null, null, null, null, null);
    }

    private Version getLatestVersion(GitLabProjectId projectId, Integer minMajorVersion, Integer maxMajorVersion, Integer minMinorVersion, Integer maxMinorVersion, Integer minPatchVersion, Integer maxPatchVersion)
    {
        return getVersions(projectId, minMajorVersion, maxMajorVersion, minMinorVersion, maxMinorVersion, minPatchVersion, maxPatchVersion).reduce(BinaryOperator.maxBy(Comparator.comparing(Version::getId))).orElse(null);
    }

    private Version newVersion(GitLabProjectId projectId, String revisionId, VersionId versionId, String notes)
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
                referenceCommit = withRetries(() -> commitsApi.getCommit(projectId.getGitLabId(), getDefaultBranch(projectId)));
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

                String defaultBranch = getDefaultBranch(projectId);
                boolean isOnDefaultBranch = PagerTools.stream(withRetries(() -> commitsApi.getCommitRefs(projectId.getGitLabId(), revisionId, RefType.BRANCH, ITEMS_PER_PAGE)))
                        .anyMatch(ref -> defaultBranch.equals(ref.getName()));
                if (!isOnDefaultBranch)
                {
                    throw new LegendSDLCServerException("Revision " + revisionId + " is unknown in project " + projectId, Status.BAD_REQUEST);
                }
            }

            String referenceRevisionId = referenceCommit.getId();
            List<VersionId> revisionVersionIds = PagerTools.stream(withRetries(() -> commitsApi.getCommitRefs(projectId.getGitLabId(), referenceRevisionId, RefType.TAG, ITEMS_PER_PAGE)))
                    .map(CommitRef::getName)
                    .filter(GitLabVersionApi::isVersionTagName)
                    .map(GitLabVersionApi::parseVersionTagName)
                    .collect(Collectors.toList());
            if (!revisionVersionIds.isEmpty())
            {
                StringBuilder builder = new StringBuilder("Revision ").append(referenceRevisionId).append(" has already been released in ");
                if (revisionVersionIds.size() == 1)
                {
                    revisionVersionIds.get(0).appendVersionIdString(builder.append("version "));
                }
                else
                {
                    revisionVersionIds.sort(Comparator.naturalOrder());
                    ListIterate.forEachWithIndex(revisionVersionIds, (id, i) -> id.appendVersionIdString((i == 0) ? builder.append("versions ") : builder.append(", ")));
                }
                throw new LegendSDLCServerException(builder.toString());
            }

            Tag tag = gitLabApi.getTagsApi().createTag(projectId.getGitLabId(), tagName, referenceRevisionId, message, (String) null);
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
}
