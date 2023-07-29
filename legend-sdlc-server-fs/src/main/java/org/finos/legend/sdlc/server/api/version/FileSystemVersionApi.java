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

package org.finos.legend.sdlc.server.api.version;

import org.finos.legend.sdlc.domain.model.version.Version;
import org.finos.legend.sdlc.server.domain.api.version.NewVersionType;
import org.finos.legend.sdlc.server.domain.api.version.VersionApi;
import org.finos.legend.sdlc.server.exception.UnavailableFeature;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

public class FileSystemVersionApi implements VersionApi
{

    @Inject
    public FileSystemVersionApi()
    {
    }

    @Override
    public List<Version> getVersions(String projectId, Integer minMajorVersion, Integer maxMajorVersion, Integer minMinorVersion, Integer maxMinorVersion, Integer minPatchVersion, Integer maxPatchVersion)
    {
        return Collections.emptyList();
    }

    @Override
    public Version getLatestVersion(String projectId, Integer minMajorVersion, Integer maxMajorVersion, Integer minMinorVersion, Integer maxMinorVersion, Integer minPatchVersion, Integer maxPatchVersion)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public Version getVersion(String projectId, int majorVersion, int minorVersion, int patchVersion)
    {
        throw UnavailableFeature.exception();
    }

    @Override
    public Version newVersion(String projectId, NewVersionType type, String revisionId, String notes)
    {
        throw UnavailableFeature.exception();
    }

}
