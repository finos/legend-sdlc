// Copyright 2023 Goldman Sachs
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

package org.finos.legend.sdlc.server.backend.simple.domain.model.revision;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;

public class SimpleBackendRevision implements Revision
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleBackendRevision.class);

    private String id;
    private String authorName;
    private Instant authoredTimestamp;
    private String committerName;
    private Instant committedTimestamp;
    private String message;
    private MutableMap<String, Entity> entities;
    private String[] context;

    @JsonIgnore
    private int revisionCounter = 1;

    public SimpleBackendRevision(MutableMap<String, Entity> entities, String[] context)
    {
        this.entities = entities;
        this.context = context;
        this.revisionCounter = 1;
        this.id = this.makeId();
        this.authorName = "alice";
        this.authoredTimestamp = Instant.now();
        this.committerName = "alice";
        this.committedTimestamp = Instant.now();
        this.message = "message";
    }

    private String makeId()
    {
        return "rev" + this.revisionCounter;
    }

    @Override
    public String getId()
    {
        return id;
    }

    @Override
    public String getAuthorName()
    {
        return authorName;
    }

    @Override
    public Instant getAuthoredTimestamp()
    {
        return authoredTimestamp;
    }

    @Override
    public String getCommitterName()
    {
        return committerName;
    }

    @Override
    public Instant getCommittedTimestamp()
    {
        return committedTimestamp;
    }

    @Override
    public String getMessage()
    {
        return message;
    }

    @JsonIgnore
    public MutableMap<String, Entity> getEntities()
    {
        return entities;
    }

    public void update(Iterable<? extends Entity> entities)
    {
        this.revisionCounter += 1;
        this.delete(entities);
        this.add(entities);
        this.updateTime();
    }

    public void add(Iterable<? extends Entity> entities)
    {
        this.revisionCounter += 1;
        for (Entity entity : entities)
        {
            String path = fullPath(entity);
            this.entities.put(path, entity);
        }
        this.updateTime();
    }

    public void delete(Iterable<? extends Entity> entities)
    {
        this.revisionCounter += 1;
        for (Entity entity : entities)
        {
            String path = fullPath(entity);
            this.entities.remove(path);
        }
        this.updateTime();
    }

    private String fullPath(Entity entity)
    {
        return entity.getPath();
    }

    private void updateTime()
    {
        this.committedTimestamp = Instant.now();
    }

    public void logStats()
    {
        LOGGER.info("Revision stats. Revision=%s. Context=%s. Entities count=%d", this.id, this.contextToString(), this.entities.size());
    }

    private String contextToString()
    {
        if (context == null)
        {
            return "";
        }
        return Arrays.stream(this.context).collect(Collectors.joining(","));
    }
}
