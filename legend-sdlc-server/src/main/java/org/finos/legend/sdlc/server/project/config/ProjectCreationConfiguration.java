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
import org.finos.legend.sdlc.domain.model.project.ProjectType;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class ProjectCreationConfiguration
{
    private final Integer defaultProjectStructureVersion;
    private final Pattern groupIdPattern;
    private final Pattern artifactIdPattern;
    private final Set<ProjectType> disallowedTypes;
    private final Map<ProjectType, String> disallowedTypeMessages;

    private ProjectCreationConfiguration(Integer defaultProjectStructureVersion, Pattern groupIdPattern, Pattern artifactIdPattern, Collection<? extends DisallowedType> disallowedTypes)
    {
        this.defaultProjectStructureVersion = defaultProjectStructureVersion;
        this.groupIdPattern = groupIdPattern;
        this.artifactIdPattern = artifactIdPattern;
        if ((disallowedTypes == null) || disallowedTypes.isEmpty())
        {
            this.disallowedTypes = Collections.emptySet();
            this.disallowedTypeMessages = Collections.emptyMap();
        }
        else
        {
            this.disallowedTypes = EnumSet.noneOf(ProjectType.class);
            this.disallowedTypeMessages = new EnumMap<>(ProjectType.class);
            disallowedTypes.forEach(dt ->
            {
                ProjectType type = dt.getType();
                String message = dt.getMessage();
                if (type != null)
                {
                    this.disallowedTypes.add(type);
                    if (message != null)
                    {
                        this.disallowedTypeMessages.put(type, message);
                    }
                }
            });
        }
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
                patternsEqual(this.artifactIdPattern, that.artifactIdPattern) &&
                this.disallowedTypes.equals(that.disallowedTypes) &&
                this.disallowedTypeMessages.equals(that.disallowedTypeMessages);
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
        hash = (31 * hash) + this.disallowedTypes.hashCode();
        hash = (31 * hash) + this.disallowedTypeMessages.hashCode();
        return hash;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder("<ProjectCreationConfiguration defaultProjectStructureVersion=").append(this.defaultProjectStructureVersion)
                .append(" groupIdPattern=").append(this.groupIdPattern)
                .append(" artifactIdPattern=").append(this.artifactIdPattern)
                .append(" disallowedTypes=[");
        boolean first = true;
        for (ProjectType type : this.disallowedTypes)
        {
            if (first)
            {
                first = false;
            }
            else
            {
                builder.append(", ");
            }
            builder.append(type);
            String message = this.disallowedTypeMessages.get(type);
            if (message != null)
            {
                builder.append(" (\"").append(message).append("\")");
            }
        }
        builder.append("]>");
        return builder.toString();
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

    public boolean isDisallowedType(ProjectType type)
    {
        return this.disallowedTypes.contains(type);
    }

    public String getDisallowedTypeMessage(ProjectType type)
    {
        return this.disallowedTypeMessages.get(type);
    }

    @JsonCreator
    public static ProjectCreationConfiguration newConfig(@JsonProperty("defaultProjectStructureVersion") Integer defaultProjectStructureVersion, @JsonProperty("groupIdPattern") Pattern groupIdPattern, @JsonProperty("artifactIdPattern") Pattern artifactIdPattern, @JsonProperty("disallowedTypes") Collection<DisallowedType> disallowedTypes)
    {
        return new ProjectCreationConfiguration(defaultProjectStructureVersion, groupIdPattern, artifactIdPattern, disallowedTypes);
    }

    public static ProjectCreationConfiguration newConfig(Integer defaultProjectStructureVersion, String groupIdPattern, String artifactIdPattern, DisallowedType... disallowedTypes)
    {
        return newConfig(defaultProjectStructureVersion, groupIdPattern, artifactIdPattern, Arrays.asList(disallowedTypes));
    }

    public static ProjectCreationConfiguration newConfig(Integer defaultProjectStructureVersion, String groupIdPattern, String artifactIdPattern, Collection<? extends DisallowedType> disallowedTypes)
    {
        return new ProjectCreationConfiguration(defaultProjectStructureVersion, (groupIdPattern == null) ? null : Pattern.compile(groupIdPattern), (artifactIdPattern == null) ? null : Pattern.compile(artifactIdPattern), disallowedTypes);
    }

    public static ProjectCreationConfiguration emptyConfig()
    {
        return new ProjectCreationConfiguration(null, null, null, null);
    }

    private static boolean patternsEqual(Pattern pattern1, Pattern pattern2)
    {
        return (pattern1 == pattern2) ||
                ((pattern1 != null) &&
                        (pattern2 != null) &&
                        (pattern1.flags() == pattern2.flags()) &&
                        Objects.equals(pattern1.pattern(), pattern2.pattern()));
    }

    public static class DisallowedType
    {
        private final ProjectType type;
        private final String message;

        private DisallowedType(ProjectType type, String message)
        {
            this.type = type;
            this.message = message;
        }

        public ProjectType getType()
        {
            return this.type;
        }

        public String getMessage()
        {
            return this.message;
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other)
            {
                return true;
            }

            if (!(other instanceof DisallowedType))
            {
                return false;
            }

            DisallowedType that = (DisallowedType) other;
            return Objects.equals(this.type, that.type) && Objects.equals(this.message, that.message);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(this.type) ^ Objects.hashCode(this.message);
        }

        @Override
        public String toString()
        {
            return "<DisallowedType type=" + this.type + " message=" + message + ">";
        }

        @JsonCreator
        public static DisallowedType newDisallowedType(@JsonProperty("type") ProjectType type, @JsonProperty("message") String message)
        {
            return new DisallowedType(type, message);
        }
    }
}
