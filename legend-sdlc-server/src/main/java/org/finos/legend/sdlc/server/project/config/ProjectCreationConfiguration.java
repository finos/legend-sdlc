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

package org.finos.legend.sdlc.server.project.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.regex.Pattern;

public class ProjectCreationConfiguration
{
    private final Integer defaultProjectStructureVersion;
    private final Pattern groupIdPattern;
    private final Pattern artifactIdPattern;

    private ProjectCreationConfiguration(Integer defaultProjectStructureVersion, Pattern groupIdPattern, Pattern artifactIdPattern)
    {
        this.defaultProjectStructureVersion = defaultProjectStructureVersion;
        this.groupIdPattern = groupIdPattern;
        this.artifactIdPattern = artifactIdPattern;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof ProjectCreationConfiguration))
        {
            return false;
        }

        ProjectCreationConfiguration that = (ProjectCreationConfiguration) other;
        return Objects.equals(this.defaultProjectStructureVersion, that.defaultProjectStructureVersion) &&
            patternsEqual(this.groupIdPattern, that.groupIdPattern) &&
            patternsEqual(this.artifactIdPattern, that.artifactIdPattern);
    }

    @Override
    public int hashCode()
    {
        int hash = Objects.hashCode(this.defaultProjectStructureVersion);
        if (this.groupIdPattern != null)
        {
            hash = (31 * hash) + Objects.hashCode(this.groupIdPattern.pattern());
        }
        if (this.artifactIdPattern != null)
        {
            hash = (31 * hash) + Objects.hashCode(this.artifactIdPattern.pattern());
        }
        return hash;
    }

    @Override
    public String toString()
    {
        return "<ProjectCreationConfiguration defaultProjectStructureVersion=" + this.defaultProjectStructureVersion + " groupIdPattern=" + this.groupIdPattern + " artifactIdPattern=" + this.artifactIdPattern + ">";
    }

    public Integer getDefaultProjectStructureVersion()
    {
        return this.defaultProjectStructureVersion;
    }

    public Pattern getGroupIdPattern()
    {
        return this.groupIdPattern;
    }

    public Pattern getArtifactIdPattern()
    {
        return this.artifactIdPattern;
    }

    @JsonCreator
    public static ProjectCreationConfiguration newConfig(@JsonProperty("defaultProjectStructureVersion") Integer defaultProjectStructureVersion, @JsonProperty("groupIdPattern") Pattern groupIdPattern, @JsonProperty("artifactIdPattern") Pattern artifactIdPattern)
    {
        return new ProjectCreationConfiguration(defaultProjectStructureVersion, groupIdPattern, artifactIdPattern);
    }

    public static ProjectCreationConfiguration newConfig(Integer defaultProjectStructureVersion, String groupIdPattern, String artifactIdPattern)
    {
        return new ProjectCreationConfiguration(defaultProjectStructureVersion, (groupIdPattern == null) ? null : Pattern.compile(groupIdPattern), (artifactIdPattern == null) ? null : Pattern.compile(artifactIdPattern));
    }

    public static ProjectCreationConfiguration emptyConfig()
    {
        return new ProjectCreationConfiguration(null, null, null);
    }

    private static boolean patternsEqual(Pattern pattern1, Pattern pattern2)
    {
        return (pattern1 == pattern2) ||
            ((pattern1 != null) &&
                (pattern2 != null) &&
                (pattern1.flags() == pattern2.flags()) &&
                Objects.equals(pattern1.pattern(), pattern2.pattern()));
    }
}
