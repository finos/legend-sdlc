// Copyright 2022 Goldman Sachs
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

package org.finos.legend.sdlc.server.inmemory.domain.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.domain.model.review.Review;
import org.finos.legend.sdlc.domain.model.review.ReviewState;

import javax.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import org.finos.legend.sdlc.domain.model.user.User;
import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InMemoryReview implements Review
{
    private InMemoryProjectConfiguration configuration = null;
    private final MutableMap<String, Entity> entities = Maps.mutable.empty();
    private String projectId;
    private String reviewId;
    private String workspaceId;
    private String workspaceType;
    private String title;
    private String description;
    private List<String> labels;
    private String targetBranch = "default";


    @Inject
    public InMemoryReview()
    {
    }

    public InMemoryReview(String projectId, String reviewId)
    {
        this.projectId = projectId;
        this.reviewId = reviewId;
        this.workspaceId = "111";
        this.workspaceType = WorkspaceType.USER.toString();
        this.title = "Title"; 
        this.description = "Description";

        List ret = new ArrayList<String>();
        ret.add("Label1");

        this.labels = ret; 
    }

    public InMemoryReview(String projectId, String reviewId, String workspaceId, WorkspaceType workspaceType, String title, String description, List<String> labels)
    {
        this.projectId = projectId;
        this.reviewId = reviewId;
        this.workspaceId = workspaceId;
        this.workspaceType = workspaceType.toString();
        this.title = title; 
        this.description = description;
        this.labels = labels; 
    }

    @JsonIgnore
    public boolean containsEntity(Entity entity)
    {
        return this.entities.containsKey(entity.getPath());
    }

    @JsonIgnore
    public void addEntity(Entity entity)
    {
        this.entities.put(entity.getPath(), entity);
    }

    @JsonIgnore
    public void addEntities(Iterable<? extends Entity> newEntities)
    {
        newEntities.forEach(this::addEntity);
    }

    @JsonIgnore
    public void removeEntity(Entity entity)
    {
        this.entities.remove(entity.getPath());
    }

    @JsonIgnore
    public void removeEntities(Iterable<? extends Entity> entitiesToRemove)
    {
        entitiesToRemove.forEach(this::removeEntity);
    }

    @JsonIgnore
    public Iterable<Entity> getEntities()
    {
        return this.entities.valuesView();
    }

    @JsonIgnore
    public InMemoryProjectConfiguration getConfiguration()
    {
        return this.configuration;
    }

    @Override
    public List<String> getLabels()
    {
        return this.labels;
    }

    @Override
    public String getWebURL()
    {
        return "url";
    }

    @Override
    public String getId()
    {
        return this.reviewId;
    }

    @Override
    public String getProjectId()
    {
        return this.projectId;
    }

    @Override
    public String getWorkspaceId()
    {
        return this.workspaceId;
    }

    @Override
    public WorkspaceType getWorkspaceType()
    {
        return WorkspaceType.USER;
    }

    @Override
    public String getTitle()
    {
        return this.title;
    }

    @Override
    public String getDescription()
    {
        return this.description;
    }

    @Override
    public Instant getCreatedAt()
    {
        return Instant.ofEpochSecond(1653206372);
    }

    @Override
    public Instant getLastUpdatedAt()
    {
        return Instant.ofEpochSecond(1653206392);
    }

    @Override
    public Instant getClosedAt()
    {
        return Instant.ofEpochSecond(1653206392);
    }

    @Override
    public Instant getCommittedAt()
    {
        return Instant.ofEpochSecond(1653206382);
    }

    @Override
    public ReviewState getState()
    {
        if (this.reviewId.equals("1"))
        {
            return ReviewState.OPEN;
        }  
        
        return ReviewState.CLOSED;
    }

    @Override
    public User getAuthor()
    {
        return new User()
        {
            @Override
            public String getUserId()
            {
                return "username";
            }

            @Override
            public String getName()
            {
                return "name";
            }
        };
    }

    @Override
    public String getCommitRevisionId()
    {
        return "1";
    }

    @JsonIgnore
    public void setTargetBranch(String branchName)
    {
        this.targetBranch = branchName;
    }

    @JsonIgnore
    public String getTargetBranch()
    {
        return this.targetBranch;
    }
}
