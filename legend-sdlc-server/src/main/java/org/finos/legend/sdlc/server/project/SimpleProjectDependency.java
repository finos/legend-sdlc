// Copyright 2022 Goldman Sachs
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

package org.finos.legend.sdlc.server.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependency;
import org.finos.legend.sdlc.domain.model.project.configuration.ProjectDependencyExclusion;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class SimpleProjectDependency extends ProjectDependency
{
    private final String projectId;
    private final String versionId;
    private final List<ProjectDependencyExclusion> exclusions;

    @JsonCreator
    public SimpleProjectDependency(@JsonProperty("projectId") String projectId, @JsonProperty("versionId") @JsonDeserialize(using = VersionIdDeserializer.class) String versionId, @JsonProperty("exclusions") @JsonInclude(JsonInclude.Include.NON_EMPTY) List<ProjectDependencyExclusion> exclusions)
    {
        this.projectId = projectId;
        this.versionId = versionId;
        this.exclusions = exclusions == null ? Collections.emptyList() : exclusions;
    }

    @Override
    public String getProjectId()
    {
        return this.projectId;
    }

    @Override
    public String getVersionId()
    {
        return this.versionId;
    }

    @Override
    public List<ProjectDependencyExclusion> getExclusions()
    {
        return this.exclusions;
    }

    private static class VersionIdDeserializer extends JsonDeserializer<String>
    {
        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException
        {
            JsonNode node = p.readValueAsTree();
            switch (node.getNodeType())
            {
                case STRING:
                {
                    return node.asText();
                }
                case OBJECT:
                {
                    LegacyVersionId versionId = p.getCodec().treeToValue(node, LegacyVersionId.class);
                    return versionId.toVersionIdString();
                }
                default:
                {
                    throw MismatchedInputException.from(p, String.class, "Invalid versionId");
                }
            }
        }
    }

    private static class LegacyVersionId
    {
        private final int majorVersion;
        private final int minorVersion;
        private final int patchVersion;

        @JsonCreator
        private LegacyVersionId(@JsonProperty(value = "majorVersion") int majorVersion, @JsonProperty(value = "minorVersion") int minorVersion, @JsonProperty(value = "patchVersion") int patchVersion)
        {
            if (majorVersion < 0)
            {
                throw new IllegalArgumentException("Invalid major version: " + majorVersion);
            }
            if (minorVersion < 0)
            {
                throw new IllegalArgumentException("Invalid minor version: " + minorVersion);
            }
            if (patchVersion < 0)
            {
                throw new IllegalArgumentException("Invalid patch version: " + patchVersion);
            }

            this.majorVersion = majorVersion;
            this.minorVersion = minorVersion;
            this.patchVersion = patchVersion;
        }

        private String toVersionIdString()
        {
            return this.majorVersion + "." + this.minorVersion + "." + this.patchVersion;
        }
    }
}
