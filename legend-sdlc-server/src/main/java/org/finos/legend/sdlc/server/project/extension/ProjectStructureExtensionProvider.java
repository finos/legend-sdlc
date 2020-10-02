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

package org.finos.legend.sdlc.server.project.extension;

public interface ProjectStructureExtensionProvider
{
    /**
     * Return the latest project structure extension version for the given project structure version.
     * Returns null if there is no such project structure extension version.
     *
     * @param projectStructureVersion project structure version
     * @return latest project structure extension version or null
     */
    Integer getLatestVersionForProjectStructureVersion(int projectStructureVersion);

    /**
     * Return the project structure extension for the given structure and extension version. Throws
     * an IllegalArgumentException if there is no such project structure extension.
     *
     * @param projectStructureVersion          project structure version
     * @param projectStructureExtensionVersion project extension version
     * @return project structure extension
     */
    ProjectStructureExtension getProjectStructureExtension(int projectStructureVersion, int projectStructureExtensionVersion);
}
