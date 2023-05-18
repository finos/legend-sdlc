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

package org.finos.legend.sdlc.server.inmemory.backend.api;

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.sdlc.domain.model.patch.Patch;
import org.finos.legend.sdlc.domain.model.version.Version;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.application.version.CreateVersionCommand;
import org.finos.legend.sdlc.server.domain.api.patch.PatchApi;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryBackend;
import org.finos.legend.sdlc.server.inmemory.domain.api.InMemoryProject;

import javax.inject.Inject;
import java.util.List;

public class InMemoryPatchApi implements PatchApi
{
    private final InMemoryBackend backend;

    @Inject
    public InMemoryPatchApi(InMemoryBackend backend)
    {
        this.backend = backend;
    }

    @Override
    public Patch newPatch(String projectId, VersionId sourcePatchReleaseVersion)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<Patch> getPatches(String projectId, Integer minMajorVersion, Integer maxMajorVersion, Integer minMinorVersion, Integer maxMinorVersion, Integer minPatchVersion, Integer maxPatchVersion)
    {
        InMemoryProject project = backend.getProject(projectId);
        return Lists.mutable.withAll(project.getPatches());
    }

    @Override
    public void deletePatch(String projectId, VersionId patchReleaseVersionId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Version releasePatch(String projectId, VersionId patchReleaseVersionId)
    {
        throw new UnsupportedOperationException("Not implemented");
    }
}
