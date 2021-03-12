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

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.FileAccessContext;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider.ProjectFile;

import java.util.Map;
import java.util.stream.Stream;

class CachingFileAccessContext implements FileAccessContext
{
    private final FileAccessContext delegate;
    private final MutableMap<String, byte[]> cache = Maps.mutable.empty();
    private volatile boolean isCacheFull = false;

    private CachingFileAccessContext(FileAccessContext delegate)
    {
        this.delegate = delegate;
    }

    @Override
    public Stream<ProjectFile> getFilesInDirectory(String directory)
    {
        fillCache();
        Stream<Map.Entry<String, byte[]>> stream = this.cache.entrySet().stream();
        String canonicalDirectory = directory.endsWith("/") ? directory : (directory + "/");
        if (!"/".equals(directory))
        {
            stream = stream.filter(f -> f.getKey().startsWith(canonicalDirectory));
        }
        return stream.map(e -> ProjectFiles.newByteArrayProjectFile(e.getKey(), e.getValue()));
    }

    @Override
    public ProjectFile getFile(String path)
    {
        String canonicalPath = canonicalizePath(path);
        byte[] bytes;
        if (this.isCacheFull)
        {
            bytes = this.cache.get(canonicalPath);
        }
        else
        {
            synchronized (this.cache)
            {
                bytes = this.cache.get(canonicalPath);
                if ((bytes == null) && !this.isCacheFull)
                {
                    ProjectFile file = this.delegate.getFile(canonicalPath);
                    if (file != null)
                    {
                        bytes = file.getContentAsBytes();
                        this.cache.put(canonicalPath, bytes);
                    }
                }
            }
        }
        return (bytes == null) ? null : ProjectFiles.newByteArrayProjectFile(canonicalPath, bytes);
    }

    public void fillCache()
    {
        if (!this.isCacheFull)
        {
            synchronized (this.cache)
            {
                if (!this.isCacheFull)
                {
                    this.delegate.getFiles().forEach(pf -> this.cache.getIfAbsentPut(pf.getPath(), pf::getContentAsBytes));
                    this.isCacheFull = true;
                }
            }
        }
    }

    static CachingFileAccessContext wrap(FileAccessContext fileAccessContext)
    {
        if (fileAccessContext == null)
        {
            return null;
        }
        if (fileAccessContext instanceof CachingFileAccessContext)
        {
            return (CachingFileAccessContext) fileAccessContext;
        }
        return new CachingFileAccessContext(fileAccessContext);
    }

    private static String canonicalizePath(String path)
    {
        int pathLength = path.length();
        switch (pathLength)
        {
            case 0:
            {
                return "/";
            }
            case 1:
            {
                return (path.charAt(0) == '/') ? path : ("/" + path);
            }
            default:
            {
                int lastIndex = pathLength - 1;
                return (path.charAt(0) == '/') ?
                        ((path.charAt(lastIndex) == '/') ? path.substring(0, lastIndex) : path) :
                        ((path.charAt(lastIndex) == '/') ? ("/" + path.substring(0, lastIndex)) : ("/" + path));
            }
        }
    }
}
