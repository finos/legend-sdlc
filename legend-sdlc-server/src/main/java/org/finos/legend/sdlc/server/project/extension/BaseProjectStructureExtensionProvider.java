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

import org.finos.legend.sdlc.server.project.ProjectStructure;

import java.util.Objects;

public abstract class BaseProjectStructureExtensionProvider implements ProjectStructureExtensionProvider
{
    protected BaseProjectStructureExtensionProvider()
    {
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof ProjectStructureExtensionProvider))
        {
            return false;
        }
        ProjectStructureExtensionProvider that = (ProjectStructureExtensionProvider)other;
        for (int i = 0, latest = ProjectStructure.getLatestProjectStructureVersion(); i <= latest; i++)
        {
            Integer latestExtension = this.getLatestVersionForProjectStructureVersion(i);
            if (!Objects.equals(latestExtension, that.getLatestVersionForProjectStructureVersion(i)))
            {
                return false;
            }
            if (latestExtension != null)
            {
                for (int j = 1; j <= latestExtension; j++)
                {
                    if (!Objects.equals(this.getProjectStructureExtension(i, j), that.getProjectStructureExtension(i, j)))
                    {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public int hashCode()
    {
        int hashCode = 0;
        for (int i = 0, latest = ProjectStructure.getLatestProjectStructureVersion(); i <= latest; i++)
        {
            Integer latestExtension = getLatestVersionForProjectStructureVersion(i);
            if (latestExtension != null)
            {
                int versionHashCode = 0;
                for (int j = 1; j <= latestExtension; j++)
                {
                    ProjectStructureExtension extension = getProjectStructureExtension(i, j);
                    if (extension != null)
                    {
                        versionHashCode += Integer.hashCode(j) ^ extension.hashCode();
                    }
                }
                hashCode += Integer.hashCode(i) ^ versionHashCode;
            }
        }
        return hashCode;
    }
}
