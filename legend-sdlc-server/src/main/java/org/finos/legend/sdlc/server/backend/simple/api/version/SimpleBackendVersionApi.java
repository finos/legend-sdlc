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

package org.finos.legend.sdlc.server.backend.simple.api.version;

import org.eclipse.collections.impl.list.mutable.FastList;
import org.finos.legend.sdlc.domain.model.version.Version;
import org.finos.legend.sdlc.server.backend.simple.exception.UnavailableFeature;
import org.finos.legend.sdlc.server.backend.simple.state.SimpleBackendState;
import org.finos.legend.sdlc.server.domain.api.version.NewVersionType;
import org.finos.legend.sdlc.server.domain.api.version.VersionApi;

import javax.inject.Inject;
import java.util.List;

public class SimpleBackendVersionApi implements VersionApi
{
    private SimpleBackendState simpleBackendState;

    @Inject
    public SimpleBackendVersionApi(SimpleBackendState simpleBackendState)
    {
        this.simpleBackendState = simpleBackendState;
    }

    @Override
    public List<Version> getVersions(String projectId, Integer minMajorVersion, Integer maxMajorVersion, Integer minMinorVersion, Integer maxMinorVersion, Integer minPatchVersion, Integer maxPatchVersion)
    {
        return FastList.newListWith(this.simpleBackendState.getProject(projectId).getVersion());
    }

    @Override
    public Version getLatestVersion(String projectId, Integer minMajorVersion, Integer maxMajorVersion, Integer minMinorVersion, Integer maxMinorVersion, Integer minPatchVersion, Integer maxPatchVersion)
    {
        return this.simpleBackendState.getProject(projectId).getVersion();
    }

    @Override
    public Version getVersion(String projectId, int majorVersion, int minorVersion, int patchVersion)
    {
        return this.simpleBackendState.getProject(projectId).getVersion();
    }

    @Override
    public Version newVersion(String projectId, NewVersionType type, String revisionId, String notes)
    {
        throw UnavailableFeature.exception();
    }
}
