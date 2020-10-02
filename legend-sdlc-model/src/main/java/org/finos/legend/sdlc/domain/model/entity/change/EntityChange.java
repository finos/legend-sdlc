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

package org.finos.legend.sdlc.domain.model.entity.change;

import java.util.Map;
import java.util.Objects;

public abstract class EntityChange
{
    /**
     * Get the type of the change.
     *
     * @return change type
     */
    public abstract EntityChangeType getType();

    /**
     * Get the path of the entity being changed. In the case of {@link EntityChangeType#RENAME}, this is the entity path
     * before the rename.
     *
     * @return entity path
     */
    public abstract String getEntityPath();

    /**
     * Get the path of the classifier of the entity for {@link EntityChangeType#CREATE} or
     * {@link EntityChangeType#MODIFY}. In all other cases, this should be null.
     *
     * @return entity classifier path
     */
    public abstract String getClassifierPath();

    /**
     * Get the entity content for a {@link EntityChangeType#CREATE} or {@link EntityChangeType#MODIFY}. In all other
     * cases, this should be null.
     *
     * @return entity content (for create or modify)
     */
    public abstract Map<String, ?> getContent();

    /**
     * Get the new entity path for a {@link EntityChangeType#RENAME}. In all other cases, this should be null.
     *
     * @return new entity path (for rename)
     */
    public abstract String getNewEntityPath();

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof EntityChange))
        {
            return false;
        }

        EntityChange that = (EntityChange) other;
        return (this.getType() == that.getType()) &&
                Objects.equals(this.getEntityPath(), that.getEntityPath()) &&
                Objects.equals(this.getClassifierPath(), that.getClassifierPath()) &&
                Objects.equals(this.getContent(), that.getContent()) &&
                Objects.equals(this.getNewEntityPath(), that.getNewEntityPath());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getType(), getEntityPath(), getClassifierPath(), getContent(), getNewEntityPath());
    }

    @Override
    public String toString()
    {
        return "<EntityChange type=" + ((getType() == null) ? null : getType().name()) +
                " entityPath=" + getEntityPath() +
                " classifierPath=" + getClassifierPath() +
                " content=" + ((getContent() == null) ? null : "{...}") +
                " newEntityPath=" + getNewEntityPath() +
                ">";
    }

    public static EntityChange newCreateEntity(String entityPath, String classifierPath, Map<String, ?> content)
    {
        return newEntityChange(EntityChangeType.CREATE, entityPath, classifierPath, content, null);
    }

    public static EntityChange newDeleteEntity(String entityPath)
    {
        return newEntityChange(EntityChangeType.DELETE, entityPath, null, null, null);
    }

    public static EntityChange newModifyEntity(String entityPath, String classifierPath, Map<String, ?> content)
    {
        return newEntityChange(EntityChangeType.MODIFY, entityPath, classifierPath, content, null);
    }

    public static EntityChange newRenameEntity(String oldEntityPath, String newEntityPath)
    {
        return newEntityChange(EntityChangeType.RENAME, oldEntityPath, null, null, newEntityPath);
    }

    private static EntityChange newEntityChange(EntityChangeType type, String entityPath, String classifierPath, Map<String, ?> content, String newEntityPath)
    {
        return new EntityChange()
        {
            @Override
            public EntityChangeType getType()
            {
                return type;
            }

            @Override
            public String getEntityPath()
            {
                return entityPath;
            }

            @Override
            public String getClassifierPath()
            {
                return classifierPath;
            }

            @Override
            public Map<String, ?> getContent()
            {
                return content;
            }

            @Override
            public String getNewEntityPath()
            {
                return newEntityPath;
            }
        };
    }
}
