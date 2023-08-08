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

package org.finos.legend.sdlc.server.api.entity;

public class LocalCommitAction
{

    public enum Action
    {
        CREATE, DELETE, MOVE, UPDATE, CHMOD;
    }

    private Action action;
    private String filePath;
    private byte[] content;

    public Action getAction()
    {
        return action;
    }

    public LocalCommitAction withAction(Action action)
    {
        this.action = action;
        return this;
    }

    public String getFilePath()
    {
        return filePath;
    }

    public LocalCommitAction withFilePath(String filePath)
    {
        this.filePath = filePath;
        return this;
    }

    public byte[] getContent()
    {
        return content;
    }

    public LocalCommitAction withContent(byte[] content)
    {
        this.content = content;
        return this;
    }

}
