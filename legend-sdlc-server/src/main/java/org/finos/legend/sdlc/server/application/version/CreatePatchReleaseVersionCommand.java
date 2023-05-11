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

package org.finos.legend.sdlc.server.application.version;

public class CreatePatchReleaseVersionCommand
{
    private int majorVersion;
    private int minorVersion;
    private int patchVersion;
    private String projectId;
    private String revisionId;
    private String notes;

    public void setMajorVersion(int majorVersion)
    {
        this.majorVersion = majorVersion;
    }

    public void setMinorVersion(int minorVersion)
    {
        this.minorVersion = minorVersion;
    }

    public void setPatchVersion(int patchVersion)
    {
        this.patchVersion = patchVersion;
    }

    public void setProjectId(String projectId)
    {
        this.projectId = projectId;
    }

    public void setRevisionId(String revisionId)
    {
        this.revisionId = revisionId;
    }

    public void setNotes(String notes)
    {
        this.notes = notes;
    }

    public int getMajorVersion()
    {
        return this.majorVersion;
    }

    public int getMinorVersion()
    {
        return this.minorVersion;
    }

    public int getPatchVersion()
    {
        return this.patchVersion;
    }

    public String getProjectId()
    {
        return this.projectId;
    }

    public String getRevisionId()
    {
        return this.revisionId;
    }

    public String getNotes()
    {
        return this.notes;
    }
}
