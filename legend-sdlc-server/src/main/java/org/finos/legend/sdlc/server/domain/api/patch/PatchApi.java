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

import java.util.List;

public interface PatchApi
{
    /**
     * Create a new patch for the given project.
     *
     * @param projectId project id
     * @param sourceVersion patch release version you want to create from
     * @return new patch
     */
    Patch newPatch(String projectId, Version sourceVersion);

    /**
     * Get the list of all patch release branches for the given project.
     *
     * @param projectId project id
     * @return all patches
     */
    List<Patch> getAllPatches(String projectId);

    /**
     * Deleted the given patch release branch for the given project.
     *
     * @param projectId project id
     * @param patchReleaseVersion patch release branch you want to delete
     */
    void deletePatch(String projectId, String patchReleaseVersion);

    /**
     * Release the given patch release branch for the given project.
     *
     * @param projectId project id
     * @param patchReleaseVersion patch release branch you want to delete
     * @return version
     */
    Version releasePatch(String projectId, String patchReleaseVersion);
}
