// Copyright 2023 Goldman Sachs
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

package org.finos.legend.sdlc.server.domain.api.patch;

import org.finos.legend.sdlc.domain.model.patch.Patch;
import org.finos.legend.sdlc.domain.model.version.Version;
import org.finos.legend.sdlc.domain.model.version.VersionId;

import java.util.List;

public interface PatchApi
{
    /**
     * Create a new patch for the given project.
     *
     * @param projectId project id
     * @param sourceVersion source version from which patch release branch needs to be created
     * @return new patch
     */
    Patch newPatch(String projectId, VersionId sourceVersion);

    /**
     * Get the list of all patch release branches for the given project.
     *
     * @param projectId project id
     * @param minMajorVersion minimum major version (inclusive, null for no minimum)
     * @param maxMajorVersion maximum major version (inclusive, null for no maximum)
     * @param minMinorVersion minimum minor version (inclusive, null for no minimum)
     * @param maxMinorVersion maximum minor version (inclusive, null for no maximum)
     * @param minPatchVersion minimum patch version (inclusive, null for no minimum)
     * @param maxPatchVersion maximum patch version (inclusive, null for no maximum)
     * @return all patches matching the constraints
     */
    List<Patch> getPatches(String projectId, Integer minMajorVersion, Integer maxMajorVersion, Integer minMinorVersion, Integer maxMinorVersion, Integer minPatchVersion, Integer maxPatchVersion);

    /**
     * Deleted the given patch release branch for the given project.
     *
     * @param projectId project id
     * @param patchReleaseVersionId patch release branch you want to delete
     */
    void deletePatch(String projectId, VersionId patchReleaseVersionId);

    /**
     * Release the given patch release branch for the given project.
     *
     * @param projectId project id
     * @param patchReleaseVersionId patch release branch you want to release
     * @return version
     */
    Version releasePatch(String projectId, VersionId patchReleaseVersionId);
}
