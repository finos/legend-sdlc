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

package org.finos.legend.sdlc.server.domain.api.workspace;

import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.server.domain.api.project.source.SourceSpecification;
import org.finos.legend.sdlc.server.domain.api.project.source.WorkspaceSourceSpecification;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.WorkspaceAccessType;

import java.util.Objects;

public class WorkspaceSpecification
{
    private final String id;
    private final WorkspaceType type;
    private final WorkspaceAccessType accessType;
    private final WorkspaceSource source;
    private final String userId;

    private WorkspaceSpecification(String id, WorkspaceType type, WorkspaceAccessType accessType, WorkspaceSource source, String userId)
    {
        if ((userId != null) && (type != WorkspaceType.USER))
        {
            throw new IllegalArgumentException("User id may only be specified for user workspaces");
        }
        this.id = Objects.requireNonNull(id, "id may not be null");
        this.type = Objects.requireNonNull(type, "type may not be null");
        this.accessType = Objects.requireNonNull(accessType, "access type may not be null");
        this.source = Objects.requireNonNull(source, "source may not be null");
        this.userId = userId;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof WorkspaceSpecification))
        {
            return false;
        }

        WorkspaceSpecification that = (WorkspaceSpecification) other;
        return (this.type == that.type) &&
                (this.accessType == that.accessType) &&
                this.id.equals(that.id) &&
                this.source.equals(that.source) &&
                Objects.equals(this.userId, that.userId);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.id, this.type, this.accessType, this.source, this.userId);
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder("<WorkspaceSpecification id=\"").append(this.id).append("\" type=").append(this.type.getLabel());
        if (this.accessType != WorkspaceAccessType.WORKSPACE)
        {
            builder.append(" accessType=").append(this.accessType.getLabel());
        }
        if (!(this.source instanceof ProjectWorkspaceSource))
        {
            this.source.appendString(builder.append(" source="));
        }
        if (this.userId != null)
        {
            builder.append(" userId=\"").append(this.userId).append('"');
        }
        return builder.append('>').toString();
    }

    public String getId()
    {
        return this.id;
    }

    public WorkspaceType getType()
    {
        return this.type;
    }

    public WorkspaceAccessType getAccessType()
    {
        return this.accessType;
    }

    public WorkspaceSource getSource()
    {
        return this.source;
    }

    /**
     * The user id, if specified; otherwise, null. The user id may only be specified if the workspace type is
     * {@link WorkspaceType#USER}. Even then, it is optional.
     *
     * @return user id or null
     */
    public String getUserId()
    {
        return this.userId;
    }

    public WorkspaceSourceSpecification getSourceSpecification()
    {
        return SourceSpecification.workspaceSourceSpecification(this);
    }

    /**
     * Create a new workspace specification. If the workspace type is {@link WorkspaceType#USER}, then a user id may be
     * specified. If it is not specified, then it is assumed to be the current user.
     *
     * @param id         workspace id
     * @param type       workspace type
     * @param accessType workspace access type
     * @param source     workspace source
     * @param userId     optional user id
     * @return workspace specification
     */
    public static WorkspaceSpecification newWorkspaceSpecification(String id, WorkspaceType type, WorkspaceAccessType accessType, WorkspaceSource source, String userId)
    {
        return new WorkspaceSpecification(id, type, (accessType == null) ? WorkspaceAccessType.WORKSPACE : accessType, (source == null) ? WorkspaceSource.projectWorkspaceSource() : source, userId);
    }

    public static WorkspaceSpecification newWorkspaceSpecification(String id, WorkspaceType type, WorkspaceAccessType accessType, WorkspaceSource source)
    {
        return newWorkspaceSpecification(id, type, accessType, source, null);
    }

    public static WorkspaceSpecification newWorkspaceSpecification(String id, WorkspaceType type, WorkspaceAccessType accessType)
    {
        return newWorkspaceSpecification(id, type, accessType, null);
    }

    public static WorkspaceSpecification newWorkspaceSpecification(String id, WorkspaceType type, WorkspaceSource source)
    {
        return newWorkspaceSpecification(id, type, null, source);
    }

    public static WorkspaceSpecification newWorkspaceSpecification(String id, WorkspaceType type)
    {
        return newWorkspaceSpecification(id, type, null, null);
    }
}
