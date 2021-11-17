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
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;

import java.util.Objects;

public class DepotProjectId
{
    private static final char DELIMITER = ':';

    private final String groupId;
    private final String artifactId;

    private DepotProjectId(String groupId, String artifactId)
    {
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    public String getGroupId()
    {
        return this.groupId;
    }

    public String getArtifactId()
    {
        return this.artifactId;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof DepotProjectId))
        {
            return false;
        }
        DepotProjectId that = (DepotProjectId)other;
        return this.getGroupId().equals(that.getGroupId()) && this.getArtifactId().equals(that.getArtifactId());

    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.groupId, this.artifactId);
    }

    @Override
    public String toString()
    {
        return this.groupId + DELIMITER + this.artifactId;
    }

    public static boolean isValid(String projectId)
    {
        return (projectId != null) && (projectId.indexOf(DELIMITER) != -1);
    }

    @JsonCreator
    public static DepotProjectId newDepotProjectId(@JsonProperty("groupId") String groupId, @JsonProperty("artifactId") String artifactId)
    {
        LegendSDLCServerException.validateNonNull(groupId, "groupId may not be null");
        LegendSDLCServerException.validateNonNull(artifactId, "artifactId may not be null");

        return new DepotProjectId(groupId, artifactId);
    }

    public static DepotProjectId parseProjectId(String projectId)
    {
        if (projectId == null)
        {
            return null;
        }
        int separatorIndex = getSeparatorIndex(projectId);
        return newDepotProjectId(projectId.substring(0, separatorIndex), projectId.substring(separatorIndex + 1));
    }

    private static int getSeparatorIndex(String projectId)
    {
        int separatorIndex = projectId.indexOf(DELIMITER);
        if (separatorIndex == -1)
        {
            throw new IllegalArgumentException("Invalid project id: " + projectId);
        }
        return separatorIndex;
    }
}
