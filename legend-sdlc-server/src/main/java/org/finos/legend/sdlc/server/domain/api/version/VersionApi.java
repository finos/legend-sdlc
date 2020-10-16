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

package org.finos.legend.sdlc.server.domain.api.version;

import org.finos.legend.sdlc.domain.model.version.Version;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;

import javax.ws.rs.core.Response.Status;
import java.util.List;

public interface VersionApi
{
    /**
     * Get all versions of the given project matching the version number
     * constraints. Each constraint is inclusive, and null indicates no
     * constraint.
     *
     * @param projectId       project id
     * @param minMajorVersion minimum major version (inclusive, null for no minimum)
     * @param maxMajorVersion maximum major version (inclusive, null for no maximum)
     * @param minMinorVersion minimum minor version (inclusive, null for no minimum)
     * @param maxMinorVersion maximum minor version (inclusive, null for no maximum)
     * @param minPatchVersion minimum patch version (inclusive, null for no minimum)
     * @param maxPatchVersion maximum patch version (inclusive, null for no maximum)
     * @return project versions matching the constraints
     */
    List<Version> getVersions(String projectId, Integer minMajorVersion, Integer maxMajorVersion, Integer minMinorVersion, Integer maxMinorVersion, Integer minPatchVersion, Integer maxPatchVersion);

    /**
     * Get the latest version of the given project matching the given
     * constraints, or null if there is no such version. Each constraint
     * is inclusive, and null indicates no constraint.
     *
     * @param projectId       project id
     * @param minMajorVersion minimum major version (inclusive, null for no minimum)
     * @param maxMajorVersion maximum major version (inclusive, null for no maximum)
     * @param minMinorVersion minimum minor version (inclusive, null for no minimum)
     * @param maxMinorVersion maximum minor version (inclusive, null for no maximum)
     * @param minPatchVersion minimum patch version (inclusive, null for no minimum)
     * @param maxPatchVersion maximum patch version (inclusive, null for no maximum)
     * @return latest version matching the constraints, or null if there is no such version
     */
    Version getLatestVersion(String projectId, Integer minMajorVersion, Integer maxMajorVersion, Integer minMinorVersion, Integer maxMinorVersion, Integer minPatchVersion, Integer maxPatchVersion);

    default Version getVersion(String projectId, String versionIdString)
    {
        VersionId versionId;
        try
        {
            versionId = VersionId.parseVersionId(versionIdString);
        }
        catch (IllegalArgumentException e)
        {
            throw new LegendSDLCServerException(e.getMessage(), Status.BAD_REQUEST, e);
        }
        return getVersion(projectId, versionId);
    }

    default Version getVersion(String projectId, VersionId versionId)
    {
        return getVersion(projectId, versionId.getMajorVersion(), versionId.getMinorVersion(), versionId.getPatchVersion());
    }

    /**
     * Get a specific project version. Returns null if there is no such version.
     *
     * @param projectId    project id
     * @param majorVersion major version
     * @param minorVersion minor version
     * @param patchVersion patch version
     * @return version or null
     */
    Version getVersion(String projectId, int majorVersion, int minorVersion, int patchVersion);

    Version newVersion(String projectId, NewVersionType type, String revisionId, String notes);
}
