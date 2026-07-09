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

package org.finos.legend.sdlc.core.comparison;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.sdlc.domain.model.comparison.Comparison;
import org.finos.legend.sdlc.domain.model.comparison.EntityDiff;
import org.finos.legend.sdlc.domain.model.entity.change.EntityChangeType;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;
import org.finos.legend.sdlc.project.files.ProjectPaths;
import org.finos.legend.sdlc.project.structure.EntitySourceDirectory;
import org.finos.legend.sdlc.project.structure.ProjectStructure;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generic comparison at the file/entity level. The entity-diff assembly ({@link #newComparison}) is factored out of
 * the GitLab comparison api in re-architecture Phase 3 and is fed by backend-neutral {@link FileDiff}s: a backend
 * with native diffing translates its diff results, while {@link #compare} computes the diffs generically by walking
 * two {@link ProjectFileAccessProvider.FileAccessContext}s (no rename detection: a moved file appears as a delete
 * plus a create).
 */
public class ComparisonOperations
{
    private ComparisonOperations()
    {
    }

    /**
     * Compute a comparison by walking the files of two file access contexts, comparing content bytes. The revision
     * ids are only recorded on the resulting {@link Comparison}; the contexts determine what is actually compared.
     */
    public static Comparison compare(ProjectFileAccessProvider.FileAccessContext fromFileAccessContext, ProjectFileAccessProvider.FileAccessContext toFileAccessContext, String fromRevisionId, String toRevisionId)
    {
        MutableMap<String, byte[]> fromFiles = Maps.mutable.empty();
        fromFileAccessContext.getFiles().forEach(f -> fromFiles.put(ProjectPaths.canonicalizeFile(f.getPath()), f.getContentAsBytes()));

        List<FileDiff> fileDiffs = Lists.mutable.empty();
        toFileAccessContext.getFiles().forEach(f ->
        {
            String path = ProjectPaths.canonicalizeFile(f.getPath());
            byte[] fromContent = fromFiles.remove(path);
            if (fromContent == null)
            {
                fileDiffs.add(FileDiff.newFileDiff(path, path, false, true, false));
            }
            else if (!Arrays.equals(fromContent, f.getContentAsBytes()))
            {
                fileDiffs.add(FileDiff.newFileDiff(path, path, false, false, false));
            }
        });
        fromFiles.forEachKey(path -> fileDiffs.add(FileDiff.newFileDiff(path, path, true, false, false)));

        ProjectStructure fromProjectStructure = ProjectStructure.getProjectStructure(fromFileAccessContext);
        ProjectStructure toProjectStructure = ProjectStructure.getProjectStructure(toFileAccessContext);
        return newComparison(fromRevisionId, toRevisionId, fileDiffs, fromProjectStructure, toProjectStructure);
    }

    /**
     * Assemble a {@link Comparison} from file diffs. File changes fall into three groups: entity file changes become
     * {@link EntityDiff}s; a project configuration change is captured by the {@code isProjectConfigurationUpdated}
     * flag; all other files (e.g. build files) are ignored.
     */
    public static Comparison newComparison(String fromRevisionId, String toRevisionId, Iterable<? extends FileDiff> fileDiffs, ProjectStructure fromProjectStructure, ProjectStructure toProjectStructure)
    {
        List<EntityDiff> entityDiffs = Lists.mutable.empty();
        AtomicBoolean isProjectConfigurationUpdated = new AtomicBoolean(false);
        fileDiffs.forEach(diff ->
        {
            // File changes can be of three types:
            // 1. entity file changes - which we should handle without any problem
            // 2. project configuration changes - which we will just capture by a boolean flag to indicate if there are any changes
            // 3. other files: e.g. users can go in and modify pom.xml, add some non-entity files, etc. we DO NOT keep track of these
            String oldPath = ProjectPaths.canonicalizeFile(diff.getOldPath());
            String newPath = ProjectPaths.canonicalizeFile(diff.getNewPath());

            // project configuration change
            if (ProjectStructure.PROJECT_CONFIG_PATH.equals(oldPath) || ProjectStructure.PROJECT_CONFIG_PATH.equals(newPath))
            {
                // technically, we know the only probable operation that can happen is MODIFICATION, CREATE is really an edge case
                isProjectConfigurationUpdated.set(true);
                return;
            }

            EntitySourceDirectory oldPathSourceDirectory = fromProjectStructure.findSourceDirectoryForEntityFilePath(oldPath);
            EntitySourceDirectory newPathSourceDirectory = toProjectStructure.findSourceDirectoryForEntityFilePath(newPath);

            // entity file change
            if ((oldPathSourceDirectory != null) || (newPathSourceDirectory != null))
            {
                String oldEntityPath = (oldPathSourceDirectory == null) ? oldPath : oldPathSourceDirectory.filePathToEntityPath(oldPath);
                String newEntityPath = (newPathSourceDirectory == null) ? newPath : newPathSourceDirectory.filePathToEntityPath(newPath);
                EntityChangeType entityChangeType;
                if (diff.isDeleted())
                {
                    entityChangeType = EntityChangeType.DELETE;
                }
                else if (diff.isCreated())
                {
                    entityChangeType = EntityChangeType.CREATE;
                }
                else if (diff.isRenamed())
                {
                    entityChangeType = EntityChangeType.RENAME;
                }
                else
                {
                    entityChangeType = EntityChangeType.MODIFY;
                }
                entityDiffs.add(new EntityDiff()
                {
                    @Override
                    public EntityChangeType getEntityChangeType()
                    {
                        return entityChangeType;
                    }

                    @Override
                    public String getNewPath()
                    {
                        return newEntityPath;
                    }

                    @Override
                    public String getOldPath()
                    {
                        return oldEntityPath;
                    }
                });
            }

            // SKIP non-entity, non-config file
        });
        return new Comparison()
        {
            @Override
            public String getToRevisionId()
            {
                return toRevisionId;
            }

            @Override
            public String getFromRevisionId()
            {
                return fromRevisionId;
            }

            @Override
            public List<EntityDiff> getEntityDiffs()
            {
                return entityDiffs;
            }

            @Override
            public boolean isProjectConfigurationUpdated()
            {
                return isProjectConfigurationUpdated.get();
            }
        };
    }
}
