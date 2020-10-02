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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.collections.api.LazyIntIterable;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.map.primitive.IntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.finos.legend.sdlc.server.project.ProjectStructure;

import java.util.Arrays;
import java.util.Collection;
import java.util.SortedMap;
import java.util.StringJoiner;
import java.util.TreeMap;

public class DefaultProjectStructureExtensionProvider extends BaseProjectStructureExtensionProvider
{
    private final IntObjectMap<ProjectStructureVersionExtensions> extensions;

    private DefaultProjectStructureExtensionProvider(IntObjectMap<ProjectStructureVersionExtensions> extensions)
    {
        this.extensions = extensions;
    }

    @Override
    public Integer getLatestVersionForProjectStructureVersion(int projectStructureVersion)
    {
        ProjectStructureVersionExtensions projectStructureVersionExtensions = this.extensions.get(projectStructureVersion);
        return (projectStructureVersionExtensions == null) ? null : projectStructureVersionExtensions.getMaxVersion();
    }

    @Override
    public ProjectStructureExtension getProjectStructureExtension(int projectStructureVersion, int projectStructureExtensionVersion)
    {
        ProjectStructureVersionExtensions projectStructureVersionExtensions = this.extensions.get(projectStructureVersion);
        return (projectStructureVersionExtensions == null) ? null : projectStructureVersionExtensions.getVersion(projectStructureExtensionVersion);
    }

    public static DefaultProjectStructureExtensionProvider fromExtensions(ProjectStructureExtension... extensions)
    {
        return fromExtensions(Arrays.asList(extensions));
    }

    @JsonCreator
    public static DefaultProjectStructureExtensionProvider fromExtensions(@JsonProperty("extensions") Collection<? extends ProjectStructureExtension> extensions)
    {
        if ((extensions == null) || extensions.isEmpty())
        {
            return new DefaultProjectStructureExtensionProvider(IntObjectMaps.immutable.empty());
        }
        MutableIntObjectMap<MutableIntObjectMap<ProjectStructureExtension>> extensionsByVersion = IntObjectMaps.mutable.empty();
        extensions.forEach(e ->
        {
            ProjectStructureExtension old = extensionsByVersion.getIfAbsentPut(e.getProjectStructureVersion(), IntObjectMaps.mutable::empty).put(e.getVersion(), e);
            if (old != null)
            {
                throw new IllegalArgumentException("Multiple extensions for project structure version " + e.getProjectStructureVersion() + ", extension version " + e.getVersion());
            }
        });
        validate(extensionsByVersion);
        MutableIntObjectMap<ProjectStructureVersionExtensions> result = IntObjectMaps.mutable.ofInitialCapacity(extensionsByVersion.size());
        extensionsByVersion.forEachKeyValue((projectStructureVersion, map) -> result.put(projectStructureVersion, new ProjectStructureVersionExtensions(map)));
        return new DefaultProjectStructureExtensionProvider(result);
    }

    private static void validate(IntObjectMap<? extends IntObjectMap<ProjectStructureExtension>> extensions)
    {
        LazyIntIterable invalidProjectStructureVersions = extensions.keysView().select(v -> (v < 0) || (v > ProjectStructure.getLatestProjectStructureVersion()));
        if (invalidProjectStructureVersions.notEmpty())
        {
            throw new IllegalStateException(invalidProjectStructureVersions.toSortedList().makeString("Invalid project structure versions: ", ", ", ""));
        }

        SortedMap<Integer, IntList> invalidVersions = new TreeMap<>();
        extensions.forEachKeyValue((projectStructureVersion, versionExtensions) ->
        {
            LazyIntIterable versionInvalidVersions = versionExtensions.keysView().select(v -> v < 0);
            if (versionInvalidVersions.notEmpty())
            {
                invalidVersions.put(projectStructureVersion, versionInvalidVersions.toSortedList());
            }
        });
        if (!invalidVersions.isEmpty())
        {
            StringJoiner joiner = new StringJoiner("Invalid project structure extension versions: ", ", ", "");
            invalidVersions.forEach((psv, versions) -> joiner.add(versions.asLazy().collect(v -> psv + "." + v).makeString(", ")));
            throw new IllegalStateException(joiner.toString());
        }
    }

    private static class ProjectStructureVersionExtensions
    {
        private final IntObjectMap<ProjectStructureExtension> extensions;
        private final int maxVersion;

        private ProjectStructureVersionExtensions(IntObjectMap<ProjectStructureExtension> extensions)
        {
            this.extensions = extensions;
            this.maxVersion = extensions.keySet().max();
        }

        int getMaxVersion()
        {
            return this.maxVersion;
        }

        ProjectStructureExtension getVersion(int version)
        {
            return this.extensions.get(version);
        }
    }
}
