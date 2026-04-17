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

package org.finos.legend.sdlc.server.project;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class ProjectFileOperation
{
    private final String path;

    private ProjectFileOperation(String path)
    {
        this.path = path;
    }

    public String getPath()
    {
        return this.path;
    }

    public static class AddFile extends ProjectFileOperation
    {
        private final byte[] bytes;

        private AddFile(String path, byte[] bytes)
        {
            super(path);
            this.bytes = bytes;
        }

        public byte[] getContent()
        {
            return this.bytes;
        }

        @Override
        public String toString()
        {
            return "<AddFile path=\"" + getPath() + "\" size=" + this.bytes.length + " bytes>";
        }
    }

    public static class DeleteFile extends ProjectFileOperation
    {
        private DeleteFile(String path)
        {
            super(path);
        }

        @Override
        public String toString()
        {
            return "<DeleteFile path=\"" + getPath() + "\">";
        }
    }

    public static class ModifyFile extends ProjectFileOperation
    {
        private final byte[] bytes;

        private ModifyFile(String path, byte[] bytes)
        {
            super(path);
            this.bytes = bytes;
        }

        public byte[] getNewContent()
        {
            return this.bytes;
        }

        @Override
        public String toString()
        {
            return "<ModifyFile path=\"" + getPath() + "\" newSize=" + this.bytes.length + " bytes>";
        }
    }

    public static class MoveFile extends ProjectFileOperation
    {
        private final String newPath;
        private final byte[] newContent;

        private MoveFile(String path, String newPath, byte[] newContent)
        {
            super(path);
            this.newPath = newPath;
            this.newContent = newContent;
        }

        public String getNewPath()
        {
            return this.newPath;
        }

        public byte[] getNewContent()
        {
            return this.newContent;
        }

        @Override
        public String toString()
        {
            StringBuilder builder = new StringBuilder("<MoveFile path=\"").append(getPath()).append("\" newPath=\"").append(getNewPath()).append('"');
            if (this.newContent != null)
            {
                builder.append(" newSize=").append(this.newContent.length).append(" bytes");
            }
            builder.append('>');
            return builder.toString();
        }
    }

    public static AddFile addFile(String path, byte[] content)
    {
        Objects.requireNonNull(path, "path may not be null");
        Objects.requireNonNull(content, "content may not be null");
        return new AddFile(path, content);
    }

    public static AddFile addFile(String path, String content)
    {
        Objects.requireNonNull(path, "path may not be null");
        Objects.requireNonNull(content, "content may not be null");
        return new AddFile(path, content.getBytes(StandardCharsets.UTF_8));
    }

    public static DeleteFile deleteFile(String path)
    {
        Objects.requireNonNull(path, "path may not be null");
        return new DeleteFile(path);
    }

    public static ModifyFile modifyFile(String path, byte[] content)
    {
        Objects.requireNonNull(path, "path may not be null");
        Objects.requireNonNull(content, "content may not be null");
        return new ModifyFile(path, content);
    }

    public static ModifyFile modifyFile(String path, String content)
    {
        Objects.requireNonNull(path, "path may not be null");
        Objects.requireNonNull(content, "content may not be null");
        return new ModifyFile(path, content.getBytes(StandardCharsets.UTF_8));
    }

    public static MoveFile moveFile(String path, String newPath)
    {
        Objects.requireNonNull(path, "path may not be null");
        Objects.requireNonNull(newPath, "newPath may not be null");
        return new MoveFile(path, newPath, null);
    }

    public static MoveFile moveFile(String path, String newPath, String newContent)
    {
        Objects.requireNonNull(path, "path may not be null");
        Objects.requireNonNull(newPath, "newPath may not be null");
        Objects.requireNonNull(newContent, "newContent may not be null");
        return new MoveFile(path, newPath, newContent.getBytes(StandardCharsets.UTF_8));
    }

    public static MoveFile moveFile(String path, String newPath, byte[] newContent)
    {
        Objects.requireNonNull(path, "path may not be null");
        Objects.requireNonNull(newPath, "newPath may not be null");
        Objects.requireNonNull(newContent, "newContent may not be null");
        return new MoveFile(path, newPath, newContent);
    }
}
