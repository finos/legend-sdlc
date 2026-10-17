// Copyright 2026 Goldman Sachs
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

package org.finos.legend.sdlc.backend.api.spi;

import java.util.regex.Pattern;

/**
 * Deployment policy for project creation, supplied by the host via
 * {@link BackendEnvironment#getProjectCreationConfiguration()}: the structure version new projects are created
 * at, and validation patterns for their coordinates. Every field is optional; a backend falls back to its own
 * defaults (latest structure version, no coordinate restrictions) for absent ones. This is the data view of the
 * server's project-creation configuration — the server's configuration class itself stays in the server.
 */
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

    /**
     * The structure version new projects are created at. Null means the backend's default (the latest version).
     *
     * @return default project structure version or null
     */
    public Integer getDefaultProjectStructureVersion()
    {
        return this.defaultProjectStructureVersion;
    }

    /**
     * Pattern a new project's group id must match. Null means no restriction.
     *
     * @return group id pattern or null
     */
    public Pattern getGroupIdPattern()
    {
        return this.groupIdPattern;
    }

    /**
     * Pattern a new project's artifact id must match. Null means no restriction.
     *
     * @return artifact id pattern or null
     */
    public Pattern getArtifactIdPattern()
    {
        return this.artifactIdPattern;
    }

    public static ProjectCreationConfiguration newProjectCreationConfiguration(Integer defaultProjectStructureVersion, Pattern groupIdPattern, Pattern artifactIdPattern)
    {
        return new ProjectCreationConfiguration(defaultProjectStructureVersion, groupIdPattern, artifactIdPattern);
    }
}
