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

import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.ProjectFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Function;

public class ProjectFiles
{
    public static ProjectFile newStringProjectFile(String path, String content)
    {
        return new SimpleStringProjectFile(path, content);
    }

    public static ProjectFile newStringProjectFile(String path, Function<? super String, ? extends String> getter)
    {
        return new LazyStringProjectFile(path, getter);
    }

    public static ProjectFile newByteArrayProjectFile(String path, byte[] content)
    {
        return new SimpleByteArrayProjectFile(path, content);
    }

    public static ProjectFile newByteArrayProjectFile(String path, Function<? super String, ? extends byte[]> getter)
    {
        return new LazyByteArrayProjectFile(path, getter);
    }

    public static ProjectFile newDelegatingProjectFile(String path, Function<? super String, ? extends ProjectFile> getter)
    {
        return new LazyDelegatingProjectFile(path, getter);
    }

    private abstract static class BaseProjectFile implements ProjectFile
    {
        private final String path;

        protected BaseProjectFile(String path)
        {
            this.path = path;
        }

        @Override
        public String getPath()
        {
            return this.path;
        }
    }

    private abstract static class StringProjectFile extends BaseProjectFile
    {
        protected StringProjectFile(String path)
        {
            super(path);
        }

        @Override
        public InputStream getContentAsInputStream()
        {
            return new ByteArrayInputStream(getContentAsBytes());
        }

        @Override
        public Reader getContentAsReader()
        {
            return new StringReader(getContent());
        }

        @Override
        public byte[] getContentAsBytes()
        {
            return getContent().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String getContentAsString()
        {
            return getContent();
        }

        protected abstract String getContent();
    }

    private abstract static class ByteArrayProjectFile extends BaseProjectFile
    {
        protected ByteArrayProjectFile(String path)
        {
            super(path);
        }

        @Override
        public InputStream getContentAsInputStream()
        {
            return new ByteArrayInputStream(getContent());
        }

        @Override
        public byte[] getContentAsBytes()
        {
            byte[] content = getContent();
            return Arrays.copyOf(content, content.length);
        }

        @Override
        public String getContentAsString()
        {
            return new String(getContent(), StandardCharsets.UTF_8);
        }

        protected abstract byte[] getContent();
    }

    private static class SimpleStringProjectFile extends StringProjectFile
    {
        private final String content;

        private SimpleStringProjectFile(String path, String content)
        {
            super(path);
            this.content = content;
        }

        @Override
        protected String getContent()
        {
            return this.content;
        }
    }

    private static class LazyStringProjectFile extends StringProjectFile
    {
        private final Function<? super String, ? extends String> getter;
        private String content;

        private LazyStringProjectFile(String path, Function<? super String, ? extends String> getter)
        {
            super(path);
            this.getter = getter;
        }

        @Override
        protected String getContent()
        {
            synchronized (this)
            {
                if (this.content == null)
                {
                    this.content = this.getter.apply(getPath());
                }
                return this.content;
            }
        }
    }

    private static class SimpleByteArrayProjectFile extends ByteArrayProjectFile
    {
        private final byte[] content;

        private SimpleByteArrayProjectFile(String path, byte[] content)
        {
            super(path);
            this.content = content;
        }

        @Override
        protected byte[] getContent()
        {
            return this.content;
        }
    }

    private static class LazyByteArrayProjectFile extends ByteArrayProjectFile
    {
        private final Function<? super String, ? extends byte[]> getter;
        private byte[] content;

        private LazyByteArrayProjectFile(String path, Function<? super String, ? extends byte[]> getter)
        {
            super(path);
            this.getter = getter;
        }

        @Override
        protected byte[] getContent()
        {
            synchronized (this)
            {
                if (this.content == null)
                {
                    this.content = this.getter.apply(getPath());
                }
                return this.content;
            }
        }
    }

    private static class LazyDelegatingProjectFile extends BaseProjectFile
    {
        private final Function<? super String, ? extends ProjectFile> getter;
        private ProjectFile delegate;

        private LazyDelegatingProjectFile(String path, Function<? super String, ? extends ProjectFile> getter)
        {
            super(path);
            this.getter = getter;
        }

        @Override
        public InputStream getContentAsInputStream()
        {
            return getDelegate().getContentAsInputStream();
        }

        @Override
        public Reader getContentAsReader()
        {
            return getDelegate().getContentAsReader();
        }

        @Override
        public byte[] getContentAsBytes()
        {
            return getDelegate().getContentAsBytes();
        }

        @Override
        public String getContentAsString()
        {
            return getDelegate().getContentAsString();
        }

        private synchronized ProjectFile getDelegate()
        {
            if (this.delegate == null)
            {
                this.delegate = this.getter.apply(getPath());
            }
            return this.delegate;
        }
    }
}
