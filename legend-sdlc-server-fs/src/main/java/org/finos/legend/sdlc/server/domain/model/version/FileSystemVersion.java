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

package org.finos.legend.sdlc.server.domain.model.version;

import org.finos.legend.sdlc.domain.model.version.Version;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.domain.model.revision.FileSystemRevision;

public class FileSystemVersion implements Version
{
    private VersionId id;
    private String projectId;
    private String revisionId;
    private String notes;

    public FileSystemVersion()
    {
    }

    public FileSystemVersion(String projectId, FileSystemRevision revision)
    {
        this.id = VersionId.newVersionId(0, 0,0);
        this.projectId = projectId;
        this.revisionId = revision.getId();
        this.notes = "";
    }

    @Override
    public VersionId getId()
    {
        return id;
    }

    public void setId(VersionId id)
    {
        this.id = id;
    }

    @Override
    public String getProjectId()
    {
        return projectId;
    }

    public void setProjectId(String projectId)
    {
        this.projectId = projectId;
    }

    @Override
    public String getRevisionId()
    {
        return revisionId;
    }

    public void setRevisionId(String revisionId)
    {
        this.revisionId = revisionId;
    }

    @Override
    public String getNotes()
    {
        return notes;
    }

    public void setNotes(String notes)
    {
        this.notes = notes;
    }
}
