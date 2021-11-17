// Copyright 2021 Goldman Sachs
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

package org.finos.legend.sdlc.server.depot.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;

import java.util.Objects;

public class DepotProjectVersion
{
    private static final char DELIMITER = ':';

    private final DepotProjectId depotProjectId;
    private final String versionId;

    private DepotProjectVersion(DepotProjectId depotProjectId, String versionId)
    {
        this.depotProjectId = depotProjectId;
        this.versionId = versionId;
    }

    @JsonIgnore
    public DepotProjectId getDepotProjectId()
    {
        return this.depotProjectId;
    }

    public String getVersionId()
    {
        return this.versionId;
    }

    public String getGroupId()
    {
        return this.depotProjectId.getGroupId();
    }

    public String getArtifactId()
    {
        return this.depotProjectId.getArtifactId();
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof DepotProjectVersion))
        {
            return false;
        }
        DepotProjectVersion that = (DepotProjectVersion)other;
        return this.getDepotProjectId().equals(that.getDepotProjectId()) && this.getVersionId().equals(that.getVersionId());

    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.depotProjectId, this.versionId);
    }

    @Override
    public String toString()
    {
        return this.depotProjectId.toString() + DELIMITER + this.versionId;
    }

    public static DepotProjectVersion parseDepotProjectVersion(String depotProjectVersion)
    {
        if (depotProjectVersion == null)
        {
            return null;
        }

        int delimiterIndex = depotProjectVersion.lastIndexOf(DELIMITER);
        if (delimiterIndex == -1)
        {
            throw new IllegalArgumentException("Invalid project string: " + depotProjectVersion);
        }

        String projectId = depotProjectVersion.substring(0, delimiterIndex);
        VersionId versionId;
        try
        {
            versionId = VersionId.parseVersionId(depotProjectVersion, delimiterIndex + 1, depotProjectVersion.length());
        }
        catch (IllegalArgumentException ex)
        {
            throw new IllegalArgumentException("Invalid project string: " + depotProjectVersion, ex);
        }
        return newDepotProjectVersion(projectId, versionId);
    }

    public static DepotProjectVersion newDepotProjectVersion(String projectId, VersionId versionId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "projectId may not be null");
        LegendSDLCServerException.validateNonNull(versionId, "versionId may not be null");

        return new DepotProjectVersion(DepotProjectId.parseProjectId(projectId), versionId.toVersionIdString());
    }

    @JsonCreator
    public static DepotProjectVersion newDepotProjectVersion(@JsonProperty("groupId") String groupId, @JsonProperty("artifactId") String artifactId, @JsonProperty("versionId") String versionId)
    {
        LegendSDLCServerException.validateNonNull(versionId, "versionId may not be null");
        LegendSDLCServerException.validateNonNull(groupId, "groupId may not be null");
        LegendSDLCServerException.validateNonNull(artifactId, "artifactId may not be null");

        return new DepotProjectVersion(DepotProjectId.newDepotProjectId(groupId, artifactId), versionId);
    }
}
