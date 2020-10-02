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

package org.finos.legend.sdlc.server.application.entity;

import org.eclipse.collections.api.factory.Lists;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DeleteEntitiesCommand extends AbstractEntityChangeCommand
{
    private List<String> entitiesToDelete;

    public List<String> getEntitiesToDelete()
    {
        return (this.entitiesToDelete == null) ? null : Collections.unmodifiableList(this.entitiesToDelete);
    }

    public void addEntityToDelete(String entityPath)
    {
        if (this.entitiesToDelete == null)
        {
            this.entitiesToDelete = Lists.mutable.empty();
        }
        this.entitiesToDelete.add(entityPath);
    }

    public void setEntitiesToDelete(Collection<String> entitiesToDelete)
    {
        if (entitiesToDelete == null)
        {
            this.entitiesToDelete = null;
        }
        else if (this.entitiesToDelete == null)
        {
            this.entitiesToDelete = Lists.mutable.withAll(entitiesToDelete);
        }
        else
        {
            this.entitiesToDelete.clear();
            this.entitiesToDelete.addAll(entitiesToDelete);
        }
    }
}
