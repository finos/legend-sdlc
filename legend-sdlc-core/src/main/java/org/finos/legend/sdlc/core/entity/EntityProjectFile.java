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

package org.finos.legend.sdlc.core.entity;

import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.project.files.ProjectFileAccessProvider;
import org.finos.legend.sdlc.project.structure.EntitySourceDirectory;

import java.util.Objects;

/**
 * A project file known to lie in an entity source directory, with lazy access to its entity path and (deserialized)
 * entity. Deserialization validates that the entity's declared path matches the path implied by the file location.
 */
public class EntityProjectFile
{
    private final EntitySourceDirectory sourceDirectory;
    private final ProjectFileAccessProvider.ProjectFile file;
    private String path;
    private Entity entity;

    public EntityProjectFile(EntitySourceDirectory sourceDirectory, ProjectFileAccessProvider.ProjectFile file)
    {
        this.sourceDirectory = sourceDirectory;
        this.file = file;
    }

    public synchronized String getEntityPath()
    {
        if (this.path == null)
        {
            this.path = this.sourceDirectory.filePathToEntityPath(this.file.getPath());
        }
        return this.path;
    }

    public synchronized Entity getEntity()
    {
        if (this.entity == null)
        {
            Entity localEntity = this.sourceDirectory.deserialize(this.file);
            if (!Objects.equals(localEntity.getPath(), getEntityPath()))
            {
                throw new RuntimeException("Expected entity path " + getEntityPath() + ", found " + localEntity.getPath());
            }
            this.entity = localEntity;
        }
        return this.entity;
    }
}
