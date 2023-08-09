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

package org.finos.legend.sdlc.server.api.patch;

import org.finos.legend.sdlc.domain.model.patch.Patch;
import org.finos.legend.sdlc.domain.model.version.Version;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.api.patch.PatchApi;
import org.finos.legend.sdlc.server.exception.FSException;

import javax.inject.Inject;
import java.util.List;

public class FileSystemPatchApi implements PatchApi
{
    @Inject
    public FileSystemPatchApi()
    {
    }

    @Override
    public Patch newPatch(String projectId, VersionId sourceVersion)
    {
        throw FSException.unavailableFeature();
    }

    @Override
    public List<Patch> getPatches(String projectId, Integer minMajorVersion, Integer maxMajorVersion, Integer minMinorVersion, Integer maxMinorVersion, Integer minPatchVersion, Integer maxPatchVersion)
    {
        throw FSException.unavailableFeature();
    }

    @Override
    public void deletePatch(String projectId, VersionId patchReleaseVersionId)
    {
        throw FSException.unavailableFeature();
    }

    @Override
    public Version releasePatch(String projectId, VersionId patchReleaseVersionId)
    {
        throw FSException.unavailableFeature();
    }
}
